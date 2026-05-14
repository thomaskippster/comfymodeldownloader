package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.ModelInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for scanning local directories for ComfyUI models.
 */
@Service
public class LocalModelScanner {

    private final ConfigService configService;
    private final PathResolver pathResolver;
    private final Map<String, Path> modelCache = new HashMap<>();

    @Autowired
    public LocalModelScanner(ConfigService configService, PathResolver pathResolver) {
        this.configService = configService;
        this.pathResolver = pathResolver;
    }

    /**
     * Scans all configured models directories (standard and extra) for supported model files.
     */
    public List<ModelInfo> scanLocalModels() {
        List<ModelInfo> foundModels = new ArrayList<>();
        java.util.Set<String> seenPaths = new java.util.HashSet<>();
        
        // 1. Scan Standard Models Path
        String modelsPathStr = configService.getModelsPath();
        if (modelsPathStr != null && !modelsPathStr.isEmpty()) {
            scanDirectory(Paths.get(modelsPathStr), foundModels, seenPaths, null);
        }

        // 2. Scan Extra Model Paths (from extra_model_paths.yaml)
        Map<String, List<Path>> extraPaths = pathResolver.getAllExtraModelPaths();
        for (Map.Entry<String, List<Path>> entry : extraPaths.entrySet()) {
            String type = entry.getKey();
            for (Path extraPath : entry.getValue()) {
                if (Files.exists(extraPath) && Files.isDirectory(extraPath)) {
                    scanDirectory(extraPath, foundModels, seenPaths, type);
                }
            }
        }

        return foundModels;
    }

    private void scanDirectory(Path rootPath, List<ModelInfo> foundModels, java.util.Set<String> seenPaths, String forcedType) {
        Path absRoot = rootPath.toAbsolutePath().normalize();
        try (Stream<Path> walk = Files.walk(absRoot)) {
            List<Path> files = walk
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedModelFile)
                    .filter(p -> !isIgnored(p, absRoot))
                    .collect(Collectors.toList());

            for (Path file : files) {
                String absPath = file.toAbsolutePath().normalize().toString().toLowerCase();
                if (seenPaths.add(absPath)) {
                    modelCache.put(file.getFileName().toString().toLowerCase(), file);
                    foundModels.add(createModelInfo(file, absRoot, forcedType));
                }
            }
        } catch (IOException e) {
            System.err.println("Error scanning directory " + absRoot + ": " + e.getMessage());
        }
    }

    /**
     * Finds a local model by its filename, searching across all configured paths.
     */
    public Optional<Path> findModel(String filename) {
        return findModelWithPrefSize(null, filename, -1);
    }

    /**
     * Finds a model by its filename recursively, searching all configured paths and prioritizing matches with the preferred size.
     */
    public Optional<Path> findModelWithPrefSize(Path specificRoot, String filename, long preferredSize) {
        return findModelWithPrefSizeAndType(specificRoot, filename, preferredSize, null);
    }

    public Optional<Path> findModelWithPrefSizeAndType(Path specificRoot, String filename, long preferredSize, String type) {
        if (filename == null) return Optional.empty();

        // 1. Search in specific root if provided
        if (specificRoot != null) {
            Optional<Path> found = searchInDirectory(specificRoot, filename, preferredSize);
            if (found.isPresent()) return found;
        }

        // 2. Search in Type-Specific Extra Paths (High Priority)
        if (type != null) {
            List<Path> typePaths = pathResolver.getModelPaths(type);
            for (Path p : typePaths) {
                Optional<Path> found = searchInDirectory(p, filename, preferredSize);
                if (found.isPresent()) return found;
            }
        }

        // 3. Search in Standard Models Path
        String modelsPathStr = configService.getModelsPath();
        if (modelsPathStr != null && !modelsPathStr.isEmpty()) {
            Optional<Path> found = searchInDirectory(Paths.get(modelsPathStr), filename, preferredSize);
            if (found.isPresent()) return found;
        }

        // 4. Search in ALL Extra Model Paths (Fallback)
        Map<String, List<Path>> extraPaths = pathResolver.getAllExtraModelPaths();
        for (List<Path> paths : extraPaths.values()) {
            for (Path p : paths) {
                Optional<Path> found = searchInDirectory(p, filename, preferredSize);
                if (found.isPresent()) return found;
            }
        }
        
        return Optional.empty();
    }

    private Optional<Path> searchInDirectory(Path root, String filename, long preferredSize) {
        if (root == null || !Files.exists(root)) return Optional.empty();
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> matches = walk.filter(Files::isRegularFile)
                    .filter(p -> !isIgnored(p, root))
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(filename))
                    .collect(Collectors.toList());
            
            if (matches.isEmpty()) return Optional.empty();
            
            if (preferredSize > 0) {
                for (Path p : matches) {
                    try {
                        if (Files.size(p) == preferredSize) return Optional.of(p);
                    } catch (IOException ignored) {}
                }
            }
            return Optional.of(matches.get(0));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private boolean isSupportedModelFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".safetensors") || name.endsWith(".sft") || name.endsWith(".ckpt") || 
               name.endsWith(".pth") || name.endsWith(".pt") || name.endsWith(".bin");
    }

    private boolean isIgnored(Path path, Path rootPath) {
        // 1. If we are already searching inside an archive folder (e.g. E:\Archive), 
        // we should not filter out anything based on the "archive" name.
        String rootName = rootPath.getFileName() != null ? rootPath.getFileName().toString().toLowerCase() : "";
        if (rootName.contains("archive") || rootName.contains("archived")) {
            return false;
        }

        // 2. Also check if the path is inside the specifically configured archive directory
        try {
            String archivePathStr = configService.getArchivePath();
            if (archivePathStr != null && !archivePathStr.isEmpty()) {
                Path archivePath = Paths.get(archivePathStr).toAbsolutePath().normalize();
                Path targetPath = path.toAbsolutePath().normalize();
                if (targetPath.startsWith(archivePath)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        // 3. Fallback: ignore any subfolder named "archive" or ".venv"
        for (int i = 0; i < path.getNameCount(); i++) {
            String part = path.getName(i).toString().toLowerCase();
            if (part.equals("archive") || part.equals("archived") || part.equals(".venv") || part.equals("venv")) {
                return true;
            }
        }
        return false;
    }

    private ModelInfo createModelInfo(Path file, Path rootPath, String forcedType) {
        String fileName = file.getFileName().toString();
        Path relativePath = rootPath.relativize(file);
        
        // Derive type from the first folder after root
        String type = (forcedType != null) ? forcedType : "unknown";
        if (forcedType == null && relativePath.getNameCount() > 1) {
            type = relativePath.getName(0).toString();
        }

        ModelInfo info = new ModelInfo(type, fileName, "LOCAL");
        
        // Set save_path to the relative parent path
        if (relativePath.getParent() != null) {
            info.setSave_path(relativePath.getParent().toString().replace("\\", "/"));
        }
        
        info.setFilename(fileName);
        
        // Calculate size in MB
        try {
            long bytes = Files.size(file);
            double mb = bytes / (1024.0 * 1024.0);
            info.setSize(String.format("%.2f MB", mb));
        } catch (IOException e) {
            info.setSize("Unknown");
        }

        return info;
    }
}
