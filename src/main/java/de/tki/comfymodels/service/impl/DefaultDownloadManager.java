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

    @Autowired
    private ConfigService configService;

    @Override
    public void startQueue(List<ModelInfo> models, String baseDir, BiConsumer<Integer, String> statusUpdater, Runnable onFinished) {
        isStopped = false;
        isPaused = false;
        new Thread(() -> {
            try {
                java.util.concurrent.CompletableFuture<?>[] futures = new java.util.concurrent.CompletableFuture[models.size()];
                for (int i = 0; i < models.size(); i++) {
                    final int index = i;
                    ModelInfo info = models.get(i);
                    futures[i] = java.util.concurrent.CompletableFuture.runAsync(() -> {
                        if (isStopped) { statusUpdater.accept(index, "Stopped"); return; }
                        if (info.getUrl() == null || info.getUrl().startsWith("MISSING")) { statusUpdater.accept(index, "Skipped (No URL)"); return; }
                        try {
                            Path targetDir = Paths.get(baseDir, info.getType());
                            Files.createDirectories(targetDir);
                            downloadWithResume(info, targetDir.resolve(info.getName()), index, statusUpdater);
                        } catch (Exception e) { statusUpdater.accept(index, "Error: " + e.getMessage()); }
                    }, executor);
                }
                java.util.concurrent.CompletableFuture.allOf(futures).join();
            } finally { onFinished.run(); }
        }).start();
    }

    private void downloadWithResume(ModelInfo info, Path targetFile, int index, BiConsumer<Integer, String> statusUpdater) throws Exception {
        File file = targetFile.toFile();
        
        // ANTI-STUB LOGIK: Wenn Datei < 10KB, löschen wir sie und fangen neu an (da es wahrscheinlich ein LFS-Pointer ist)
        if (file.exists() && file.length() < 10240) {
            file.delete();
        }
        
        long existingFileSize = file.exists() ? file.length() : 0;
        
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(info.getUrl())).header("User-Agent", "Mozilla/5.0");
        String hfToken = configService.getHfToken();
        if (info.getUrl().contains("huggingface.co") && !hfToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + hfToken);
        }

        if (existingFileSize > 0) builder.header("Range", "bytes=" + existingFileSize + "-");

        HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        int statusCode = response.statusCode();
        
        if (statusCode == 401 || statusCode == 403) {
            statusUpdater.accept(index, "❌ Auth Required (Token?)");
            return;
        }

        long contentLen = response.headers().firstValueAsLong("Content-Length").orElse(0L);
        long totalBytes = (statusCode == 206) ? contentLen + existingFileSize : contentLen;

        // VERIFIZIERUNG: Falls die Gesamtgröße extrem klein ist, ist es ein LFS Stub
        if (totalBytes > 0 && totalBytes < 5000 && info.getName().endsWith(".safetensors")) {
            statusUpdater.accept(index, "❌ LFS Stub detected (Token?)");
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
                if (isStopped) return;
                while (isPaused) { statusUpdater.accept(index, "Paused..."); Thread.sleep(1000); }
                raf.write(buffer, 0, read);
                downloaded += read;
                long now = System.currentTimeMillis();
                if (now - lastUpdate > 800) {
                    statusUpdater.accept(index, "Downloading: " + (totalBytes > 0 ? (downloaded * 100 / totalBytes) : "?") + "% (" + formatSize(downloaded) + ")");
                    lastUpdate = now;
                }
            }
        }
        statusUpdater.accept(index, "Finished");
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
