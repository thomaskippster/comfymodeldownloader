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
        Map<String, List<ModelInfo>> allModels = new TreeMap<>();
        java.util.Set<String> seenPaths = new java.util.HashSet<>();

        // 1. Scan Standard Models Path
        Map<String, List<ModelInfo>> standardModels = scanDirectory(configService.getModelsPath(), true, seenPaths);
        allModels.putAll(standardModels);

        // 2. Scan Extra Model Paths
        Map<String, List<Path>> extraPaths = pathResolver.getAllExtraModelPaths();
        for (Map.Entry<String, List<Path>> entry : extraPaths.entrySet()) {
            String type = entry.getKey();
            for (Path extraPath : entry.getValue()) {
                if (Files.exists(extraPath) && Files.isDirectory(extraPath)) {
                    Map<String, List<ModelInfo>> extraModels = scanDirectory(extraPath.toString(), true, seenPaths);
                    // Merge extra models. 
                    for (Map.Entry<String, List<ModelInfo>> extraEntry : extraModels.entrySet()) {
                        String folder = extraEntry.getKey();
                        String targetFolder = "root".equals(folder) ? type : type + "/" + folder;
                        allModels.computeIfAbsent(targetFolder, k -> new ArrayList<>()).addAll(extraEntry.getValue());
                    }
                }
            }
        }

        return allModels;
    }

    public Map<String, List<ModelInfo>> getArchivedModelsGroupedByFolder() {
        String archivePath = configService.getArchivePath();
        if (archivePath == null || archivePath.trim().isEmpty()) {
            return new TreeMap<>();
        }
        return scanDirectory(archivePath, false, new java.util.HashSet<>());
    }

    private Map<String, List<ModelInfo>> scanDirectory(String pathStr, boolean excludeArchived, java.util.Set<String> seenPaths) {
        Map<String, List<ModelInfo>> grouped = new TreeMap<>();
        if (pathStr == null || pathStr.isEmpty()) return grouped;
        
        Path root = Paths.get(pathStr).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            return grouped;
        }

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> {
                    try {
                        // Ignore hidden directories and .venv / venv / archive (if excluding)
                        Path relativeToRoot = root.relativize(p);
                        for (Path part : relativeToRoot) {
                            String name = part.toString().toLowerCase();
                            if (name.startsWith(".") || name.equals("venv") || name.equals(".venv") || name.equals("__pycache__")) {
                                return false;
                            }
                            if (excludeArchived && (name.equals("archive") || name.equals("archived"))) {
                                return false;
                            }
                        }
                        return true;
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .filter(Files::isRegularFile)
                .filter(this::isSupportedModel)
                .filter(p -> !excludeArchived || !isAlreadyArchived(p))
                .forEach(p -> {
                    String absPath = p.toAbsolutePath().normalize().toString().toLowerCase();
                    if (!seenPaths.add(absPath)) return;

                    Path relative = root.relativize(p);
                    String folder = relative.getParent() != null ? relative.getParent().toString().replace("\\", "/") : "root";
                    
                    ModelInfo info = new ModelInfo();
                    info.setName(p.getFileName().toString());
                    info.setSave_path(folder);
                    info.setType(folder);
                    
                    try {
                        long bytes = Files.size(p);
                        info.setByteSize(bytes);
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
            System.err.println("Error walking directory " + pathStr + ": " + e.getMessage());
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
        try {
            Path archivePath = Paths.get(archivePathStr).toAbsolutePath().normalize();
            Path targetPath = p.toAbsolutePath().normalize();
            return targetPath.startsWith(archivePath);
        } catch (Exception e) {
            return false;
        }
    }

    public void moveToArchiveWithProgress(String relativePath, LongConsumer progressUpdate) throws IOException {
        Path p = Paths.get(relativePath);
        String filename = p.getFileName().toString();
        String folder = p.getParent() != null ? p.getParent().toString().replace("\\", "/") : "root";
        moveToArchiveWithProgress(folder, filename, progressUpdate);
    }

    public void moveToArchive(String relativePath) throws IOException {
        moveToArchiveWithProgress(relativePath, null);
    }

    public void moveToArchive(String folder, String filename) throws IOException {
        moveToArchiveWithProgress(folder, filename, null);
    }

    public void moveToArchiveWithProgress(String folder, String filename, LongConsumer progressUpdate) throws IOException {
        String archivePathStr = configService.getArchivePath();
        if (archivePathStr == null) throw new IOException("Archive path not configured");
        Path archivePath = Paths.get(archivePathStr);

        String normFolder = normalizeFolder(folder);
        
        // Find source path by checking standard and extra paths
        Path source = findActualSourcePath(normFolder, filename);
        if (source == null || !Files.exists(source)) {
            throw new IOException("Source model not found: " + filename + " in folder " + normFolder);
        }

        // Target in archive follows the relative structure
        String relPath = "root".equals(normFolder) ? filename : Paths.get(normFolder, filename).toString();
        Path target = archivePath.resolve(relPath);

        Files.createDirectories(target.getParent());
        moveWithProgress(source, target, progressUpdate);
    }

    private Path findActualSourcePath(String folder, String filename) {
        String modelsPathStr = configService.getModelsPath();
        if (modelsPathStr == null) return null;
        
        Path modelsPath = Paths.get(modelsPathStr);
        
        // 1. Check standard path
        Path standardPath = "root".equals(folder) ? modelsPath.resolve(filename) : modelsPath.resolve(folder).resolve(filename);
        if (Files.exists(standardPath)) return standardPath;
        
        // 2. Check extra paths for this type/folder
        List<Path> extraPaths = pathResolver.getModelPaths(folder);
        for (Path extraRoot : extraPaths) {
            // Since extraRoot often ALREADY points to the type folder (e.g. .../checkpoints)
            // we check both extraRoot/filename AND extraRoot/folder/filename
            Path p1 = extraRoot.resolve(filename);
            if (Files.exists(p1)) return p1;
            
            Path p2 = extraRoot.resolve(folder).resolve(filename);
            if (Files.exists(p2)) return p2;
        }
        
        return null;
    }

    public boolean restoreFromArchiveWithProgress(String folder, String filename, LongConsumer progressUpdate) {
        String modelsPathStr = configService.getModelsPath();
        String archivePathStr = configService.getArchivePath();
        
        if (modelsPathStr == null || archivePathStr == null) return false;

        Path modelsPath = Paths.get(modelsPathStr);
        Path archivePath = Paths.get(archivePathStr);

        String normFolder = normalizeFolder(folder);
        
        // Primary location in archive
        Path archivedFolder = "root".equals(normFolder) ? archivePath : pathResolver.resolveCaseInsensitiveRecursive(archivePath, normFolder);
        Path archived = archivedFolder.resolve(filename);
        
        // Determine target folder: prefer extra paths if configured
        Path targetFolder = "root".equals(normFolder) ? modelsPath : modelsPath.resolve(normFolder);
        List<Path> extraPaths = pathResolver.getModelPaths(normFolder);
        if (!extraPaths.isEmpty()) {
            targetFolder = extraPaths.get(0);
        }
        
        Path target = targetFolder.resolve(filename);

        // Fallback: If not at primary archive location, check root of archive or search
        if (!Files.exists(archived)) {
            Path rootArchived = archivePath.resolve(filename);
            if (Files.exists(rootArchived)) {
                archived = rootArchived;
            } else {
                try (Stream<Path> walk = Files.walk(archivePath, 2)) {
                    java.util.Optional<Path> found = walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().equalsIgnoreCase(filename))
                        .findFirst();
                    if (found.isPresent()) {
                        archived = found.get();
                    } else {
                        System.err.println("Restore failed: " + filename + " not found in archive");
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
