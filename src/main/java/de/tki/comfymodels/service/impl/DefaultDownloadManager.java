package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IDownloadManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

@Service
public class DefaultDownloadManager implements IDownloadManager {
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    private volatile boolean isPaused = false;
    private volatile boolean isStopped = false;
    private volatile boolean[] currentSelection;
    private final java.util.Set<Integer> completedIndices = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<Integer, String> statusMap = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    private ConfigService configService;

    @Autowired
    private PathResolver pathResolver;

    private void safeUpdateStatus(int index, String status, BiConsumer<Integer, String> statusUpdater) {
        statusMap.put(index, status);
        // Only mark as completed if it's a final state
        boolean isFinal = status.contains("✅ Finished") || 
                         status.contains("Stopped") || 
                         status.contains("Skipped") || 
                         status.contains("Already exists") ||
                         status.startsWith("❌ Error");
        
        if (isFinal) {
            completedIndices.add(index);
        }
        statusUpdater.accept(index, status);
    }

    @Override
    public java.util.Map<Integer, String> getQueueStatus() {
        return new java.util.HashMap<>(statusMap);
    }

    @Override
    public void startQueue(List<ModelInfo> models, boolean[] selectedIndices, String baseDir, BiConsumer<Integer, String> statusUpdater, Runnable onFinished) {
        isStopped = false;
        isPaused = false;
        this.currentSelection = selectedIndices;
        completedIndices.clear();
        statusMap.clear();
        
        java.util.concurrent.atomic.AtomicBoolean finishedCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
        
        new Thread(() -> {
            try {
                int size = models.size();
                java.util.concurrent.CompletableFuture<?>[] futures = new java.util.concurrent.CompletableFuture[size];
                
                for (int i = 0; i < size; i++) {
                    final int index = i;
                    
                    if (isStopped) {
                        futures[index] = java.util.concurrent.CompletableFuture.completedFuture(null);
                        continue;
                    }

                    if (!isSelected(index)) { 
                        safeUpdateStatus(index, "Skipped (Not Selected)", statusUpdater);
                        futures[index] = java.util.concurrent.CompletableFuture.completedFuture(null);
                        continue;
                    }

                    ModelInfo info = models.get(index);
                    futures[index] = java.util.concurrent.CompletableFuture.runAsync(() -> {
                        if (isStopped || !isSelected(index)) return;
                        
                        try {
                            String subPath = info.getSave_path();
                            if (subPath == null || subPath.isEmpty() || "default".equalsIgnoreCase(subPath)) {  
                                subPath = de.tki.comfymodels.domain.ModelFolder.fromString(info.getType()).getDefaultFolderName();
                            }

                            Path modelsBase = (baseDir != null && !baseDir.isEmpty()) ? 
                                    pathResolver.resolve(baseDir) : 
                                    pathResolver.resolve(configService.getModelsPath());
                            Path targetDir = modelsBase.resolve(subPath);
                            Files.createDirectories(targetDir);
                            downloadWithResume(info, targetDir.resolve(info.getName()), index, statusUpdater);  
                        } catch (Exception e) { 
                            if (!isStopped && isSelected(index)) {
                                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                                safeUpdateStatus(index, "❌ Error: " + msg, statusUpdater);
                            }
                        }
                    }, executor);
                }
                
                // Wait for all tasks to complete (including skipped ones)
                try {
                    java.util.concurrent.CompletableFuture.allOf(futures).join();
                } catch (Exception ignored) {}
                
            } finally {
                if (finishedCalled.compareAndSet(false, true)) {
                    onFinished.run();
                }
            }
        }).start();
    }

    private boolean isSelected(int index) {
        return currentSelection == null || (index < currentSelection.length && currentSelection[index]);
    }

    @Override
    public void updateSelection(boolean[] selectedIndices) {
        this.currentSelection = selectedIndices;
    }

    private void downloadWithResume(ModelInfo info, Path targetFile, int index, BiConsumer<Integer, String> statusUpdater) {
        downloadWithResumeInternal(info, targetFile, index, statusUpdater, 0);
    }

    private void downloadWithResumeInternal(ModelInfo info, Path targetFile, int index, BiConsumer<Integer, String> statusUpdater, int retryCount) {
        try {
            if (waitForPauseAndCheckSelection(index, statusUpdater)) return;

            File file = targetFile.toFile();
            String archivePath = configService.getArchivePath();
            boolean isActuallyInArchive = archivePath != null && !archivePath.isEmpty() && 
                                         file.getAbsolutePath().toLowerCase().startsWith(new File(archivePath).getAbsolutePath().toLowerCase());

            String hfToken = configService.getHfToken();

            // Check disk space before starting
            try {
                long usableSpace = Files.getFileStore(targetFile.getParent().getRoot()).getUsableSpace();
                if (usableSpace < 10L * 1024 * 1024 * 1024) { // 10 GB Buffer
                    safeUpdateStatus(index, "⚠️ Low Disk Space (<10GB)", statusUpdater);
                }
            } catch (Exception ignored) {}

            if (file.exists() && (file.length() < 10240 || isActuallyInArchive)) {
                // If it's in the archive, we don't treat it as "local existing model" for the download manager.
                if (!isActuallyInArchive) file.delete();
            }

            long existingFileSize = (file.exists() && !isActuallyInArchive) ? file.length() : 0;

            if (waitForPauseAndCheckSelection(index, statusUpdater)) return;
            
            HttpRequest.Builder headBuilder = HttpRequest.newBuilder()
                .uri(URI.create(info.getUrl()))
                .header("User-Agent", "Mozilla/5.0")
                .method("HEAD", HttpRequest.BodyPublishers.noBody());
                
            if (info.getUrl().contains("huggingface.co") && hfToken != null && !hfToken.isEmpty()) {
                headBuilder.header("Authorization", "Bearer " + hfToken);
            }

            HttpResponse<Void> headResponse = httpClient.send(headBuilder.build(), HttpResponse.BodyHandlers.discarding());
            
            if (headResponse.statusCode() == 401 || headResponse.statusCode() == 403) {
                safeUpdateStatus(index, "❌ Auth Required (Token?)", statusUpdater);
                return;
            }

            long totalRemoteSize = headResponse.headers().firstValueAsLong("Content-Length").orElse(0L);

            // Double Check Disk Space against total size
            try {
                long usableSpace = Files.getFileStore(targetFile.getParent().getRoot()).getUsableSpace();
                if (totalRemoteSize > 0 && usableSpace < totalRemoteSize) {
                    safeUpdateStatus(index, "❌ No Space (" + formatSize(usableSpace) + " < " + formatSize(totalRemoteSize) + ")", statusUpdater);
                    return;
                }
            } catch (Exception ignored) {}

            if (existingFileSize > 0 && totalRemoteSize > 0 && existingFileSize == totalRemoteSize) {
                // Verification of existing file
                if (info.getName().endsWith(".safetensors") && (existingFileSize % 2 != 0)) {
                     if (retryCount < 1) {
                         safeUpdateStatus(index, "🔄 Fixing Corrupted File...", statusUpdater);
                         file.delete();
                         downloadWithResumeInternal(info, targetFile, index, statusUpdater, retryCount + 1);
                         return;
                     }
                }
                safeUpdateStatus(index, "✅ Already exists", statusUpdater);
                return;
            }

            if (waitForPauseAndCheckSelection(index, statusUpdater)) return;

            HttpRequest.Builder downloadBuilder = HttpRequest.newBuilder()
                .uri(URI.create(info.getUrl()))
                .header("User-Agent", "Mozilla/5.0");

            if (info.getUrl().contains("huggingface.co") && hfToken != null && !hfToken.isEmpty()) {
                downloadBuilder.header("Authorization", "Bearer " + hfToken);
            }

            if (existingFileSize > 0) downloadBuilder.header("Range", "bytes=" + existingFileSize + "-");

            HttpResponse<InputStream> response = httpClient.send(downloadBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
            int statusCode = response.statusCode();

            if (statusCode == 416) { 
                 safeUpdateStatus(index, "✅ Already exists", statusUpdater);
                 return;
            }

            if (statusCode == 401 || statusCode == 403) {
                safeUpdateStatus(index, "❌ Auth Required (Token?)", statusUpdater);
                return;
            }

            long contentLen = response.headers().firstValueAsLong("Content-Length").orElse(0L);
            long totalBytes = (statusCode == 206) ? contentLen + existingFileSize : contentLen;

            if (totalBytes > 0 && totalBytes < 5000 && info.getName().endsWith(".safetensors")) {
                safeUpdateStatus(index, "❌ LFS Stub detected (Token?)", statusUpdater);
                return;
            }

            if (statusCode == 200) existingFileSize = 0;

            try (InputStream is = response.body(); RandomAccessFile raf = new RandomAccessFile(file, "rw")) {       
                raf.seek(existingFileSize);
                byte[] buffer = new byte[65536];
                long downloaded = existingFileSize;
                int read;
                long lastUpdate = 0;
                while ((read = is.read(buffer)) != -1) {
                    if (isStopped || !isSelected(index) || Thread.currentThread().isInterrupted()) {
                        safeUpdateStatus(index, !isSelected(index) ? "Skipped (Unchecked)" : "Stopped", statusUpdater);
                        return;
                    }

                    if (isPaused) {
                        if (waitForPauseAndCheckSelection(index, statusUpdater)) return;
                        safeUpdateStatus(index, "Resuming...", statusUpdater);
                    }

                    raf.write(buffer, 0, read);
                    downloaded += read;
                    
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 800) {
                        safeUpdateStatus(index, "Downloading: " + (totalBytes > 0 ? (downloaded * 100 / totalBytes) : "?") + "% (" + formatSize(downloaded) + ")", statusUpdater);
                        lastUpdate = now;
                    }
                }
            }
            
            if (isStopped || !isSelected(index)) {
                safeUpdateStatus(index, !isSelected(index) ? "Skipped (Unchecked)" : "Stopped", statusUpdater);
            } else {
                long finalSize = file.length();
                boolean sizeMismatch = totalBytes > 0 && finalSize < totalBytes;
                boolean corruptedSafetensor = info.getName().endsWith(".safetensors") && (finalSize % 2 != 0);

                if ((sizeMismatch || corruptedSafetensor) && retryCount < 1) {
                    safeUpdateStatus(index, "🔄 Verification failed, redownloading...", statusUpdater);
                    file.delete();
                    downloadWithResumeInternal(info, targetFile, index, statusUpdater, retryCount + 1);
                } else if (sizeMismatch) {
                    safeUpdateStatus(index, "❌ Incomplete (" + formatSize(finalSize) + "/" + formatSize(totalBytes) + ")", statusUpdater);
                } else if (corruptedSafetensor) {
                    safeUpdateStatus(index, "❌ Corrupted (Odd Size)", statusUpdater);
                } else {
                    safeUpdateStatus(index, "✅ Finished", statusUpdater);
                }
            }
        } catch (Exception e) {
            // In case of a timing issue (flag set while exception is thrown)
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            
            if (isStopped || !isSelected(index) || Thread.currentThread().isInterrupted()) {
                safeUpdateStatus(index, !isSelected(index) ? "Skipped (Unchecked)" : "Stopped", statusUpdater);
            } else {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                safeUpdateStatus(index, "Error: " + msg, statusUpdater);
            }
        }
    }

    private boolean waitForPauseAndCheckSelection(int index, BiConsumer<Integer, String> statusUpdater) {
        try {
            if (isPaused) {
                safeUpdateStatus(index, "Paused", statusUpdater);
            }
            while (isPaused && !isStopped && isSelected(index)) {
                Thread.sleep(200);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (isStopped || !isSelected(index)) {
            safeUpdateStatus(index, !isSelected(index) ? "Skipped (Unchecked)" : "Stopped", statusUpdater);
            return true;
        }
        return false;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    @Override public void togglePause() { isPaused = !isPaused; }
    @Override public void stop() { isStopped = true; isPaused = false; }
    @Override public boolean isPaused() { return isPaused; }
}