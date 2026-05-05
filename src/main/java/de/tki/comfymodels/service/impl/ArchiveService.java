package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.ModelInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongConsumer;
import java.util.stream.Stream;

@Service
public class ArchiveService {

    private final ConfigService configService;
    private final PathResolver pathResolver;

    @Autowired
    public ArchiveService(ConfigService configService, PathResolver pathResolver) {
        this.configService = configService;
        this.pathResolver = pathResolver;
    }

    public String normalizeFolder(String folder) {
        return pathResolver.stripRedundantPrefixes(folder);
    }

    public Map<String, List<ModelInfo>> getModelsGroupedByFolder() {
        return scanDirectory(configService.getModelsPath(), true);
    }

    public Map<String, List<ModelInfo>> getArchivedModelsGroupedByFolder() {
        return scanDirectory(configService.getArchivePath(), false);
    }

    private Map<String, List<ModelInfo>> scanDirectory(String pathStr, boolean excludeArchived) {
        Map<String, List<ModelInfo>> grouped = new TreeMap<>();
        if (pathStr == null || pathStr.isEmpty()) return grouped;
        
        Path root = Paths.get(pathStr);
        if (!Files.exists(root)) return grouped;

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> {
                    // Ignore hidden directories and .venv / venv
                    for (Path part : root.relativize(p)) {
                        String name = part.toString();
                        if (name.startsWith(".") || name.equalsIgnoreCase("venv") || name.equalsIgnoreCase(".venv") || name.equalsIgnoreCase("__pycache__")) {
                            return false;
                        }
                    }
                    return true;
                })
                .filter(Files::isRegularFile)
                .filter(this::isSupportedModel)
                .filter(p -> !excludeArchived || !isAlreadyArchived(p))
                .forEach(p -> {
                    Path relative = root.relativize(p);
                    String folder = relative.getParent() != null ? relative.getParent().toString().replace("\\", "/") : "root";
                    
                    ModelInfo info = new ModelInfo();
                    info.setName(p.getFileName().toString());
                    info.setSave_path(folder);
                    info.setType(folder);
                    
                    try {
                        long bytes = Files.size(p);
                        double mb = bytes / (1024.0 * 1024.0);
                        if (mb > 1024) {
                            info.setSize(String.format("%.2f GB", mb / 1024.0));
                        } else {
                            info.setSize(String.format("%.2f MB", mb));
                        }
                    } catch (IOException e) {
                        info.setSize("Unknown");
                    }
                    
                    grouped.computeIfAbsent(folder, k -> new ArrayList<>()).add(info);
                });
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return grouped;
    }

    private boolean isSupportedModel(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".safetensors") || n.endsWith(".sft") || n.endsWith(".ckpt") || n.endsWith(".pth") || n.endsWith(".pt") || n.endsWith(".bin");
    }

    private boolean isAlreadyArchived(Path p) {
        String archivePathStr = configService.getArchivePath();
        if (archivePathStr == null || archivePathStr.isEmpty()) return false;
        Path archivePath = Paths.get(archivePathStr).toAbsolutePath();
        return p.toAbsolutePath().startsWith(archivePath);
    }

    public void moveToArchiveWithProgress(String relativePath, LongConsumer progressUpdate) throws IOException {
        String modelsPath = configService.getModelsPath();
        String archivePath = configService.getArchivePath();
        
        if (modelsPath == null || archivePath == null) throw new IOException("Paths not configured");

        Path source = Paths.get(modelsPath, relativePath);
        Path target = Paths.get(archivePath, relativePath);

        if (!Files.exists(source)) throw new IOException("Source not found: " + source);

        Files.createDirectories(target.getParent());
        moveWithProgress(source, target, progressUpdate);
    }

    public void moveToArchive(String relativePath) throws IOException {
        moveToArchiveWithProgress(relativePath, null);
    }

    public void moveToArchiveWithProgress(String folder, String filename, LongConsumer progressUpdate) throws IOException {
        String normFolder = normalizeFolder(folder);
        String relPath = "root".equals(normFolder) ? filename : Paths.get(normFolder, filename).toString();
        moveToArchiveWithProgress(relPath, progressUpdate);
    }

    public void moveToArchive(String folder, String filename) throws IOException {
        moveToArchiveWithProgress(folder, filename, null);
    }

    public boolean restoreFromArchiveWithProgress(String folder, String filename, LongConsumer progressUpdate) {
        String modelsPathStr = configService.getModelsPath();
        String archivePathStr = configService.getArchivePath();
        
        if (modelsPathStr == null || archivePathStr == null) return false;

        Path modelsPath = Paths.get(modelsPathStr);
        Path archivePath = Paths.get(archivePathStr);

        String normFolder = normalizeFolder(folder);
        
        // Primary location with case-insensitive resolution
        Path archivedFolder = "root".equals(normFolder) ? archivePath : pathResolver.resolveCaseInsensitiveRecursive(archivePath, normFolder);
        Path archived = archivedFolder.resolve(filename);
        
        Path targetFolder = "root".equals(normFolder) ? modelsPath : modelsPath.resolve(normFolder);
        Path target = targetFolder.resolve(filename);

        // Fallback: If not at primary, check root of archive
        if (!Files.exists(archived)) {
            Path rootArchived = archivePath.resolve(filename);
            if (Files.exists(rootArchived)) {
                archived = rootArchived;
            } else {
                // Secondary Fallback: Check if it exists ANYWHERE in the archive (shallow search)
                try (Stream<Path> walk = Files.walk(archivePath, 2)) {
                    java.util.Optional<Path> found = walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().equalsIgnoreCase(filename))
                        .findFirst();
                    if (found.isPresent()) {
                        archived = found.get();
                    } else {
                        System.err.println("Restore failed: " + filename + " not found in archive (checked " + normFolder + " and root)");
                        return false;
                    }
                } catch (IOException e) {
                    return false;
                }
            }
        }

        try {
            if (Files.exists(archived) && Files.size(archived) > 0) {
                Files.createDirectories(target.getParent());
                moveWithProgress(archived, target, progressUpdate);
                return true;
            }
        } catch (IOException e) {
            System.err.println("Error moving file from archive: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public boolean restoreFromArchive(String folder, String filename) {
        return restoreFromArchiveWithProgress(folder, filename, null);
    }

    private void moveWithProgress(Path source, Path target, LongConsumer progressUpdate) throws IOException {
        // Optimization: If on same file store, move is instant
        if (Files.getFileStore(source).equals(Files.getFileStore(target.getParent()))) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            if (progressUpdate != null) progressUpdate.accept(Files.size(target));
            return;
        }

        // Cross-drive move: Copy with progress, then delete
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[1024 * 64]; // 64KB buffer
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                if (progressUpdate != null) progressUpdate.accept((long) bytesRead);
            }
        }
        Files.delete(source);
    }
}
