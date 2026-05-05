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
    private final Map<String, Path> modelCache = new HashMap<>();

    @Autowired
    public LocalModelScanner(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * Scans the configured models directory for supported model files.
     * 
     * @return A list of ModelInfo objects representing the found local models.
     */
    public List<ModelInfo> scanLocalModels() {
        List<ModelInfo> foundModels = new ArrayList<>();
        String modelsPathStr = configService.getModelsPath();
        if (modelsPathStr == null || modelsPathStr.isEmpty()) {
            return foundModels;
        }

        Path rootPath = Paths.get(modelsPathStr);
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            return foundModels;
        }

        modelCache.clear();
        try (Stream<Path> walk = Files.walk(rootPath)) {
            List<Path> files = walk
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedModelFile)
                    .filter(p -> !isIgnored(p, rootPath))
                    .collect(Collectors.toList());

            for (Path file : files) {
                modelCache.put(file.getFileName().toString().toLowerCase(), file);
                foundModels.add(createModelInfo(file, rootPath));
            }
        } catch (IOException e) {
            System.err.println("Error scanning local models: " + e.getMessage());
        }

        return foundModels;
    }

    /**
     * Finds a local model by its filename, regardless of subfolder.
     */
    public Optional<Path> findModel(String filename) {
        String modelsPathStr = configService.getModelsPath();
        if (modelsPathStr == null || modelsPathStr.isEmpty()) return Optional.empty();
        return findModelInDirectory(Paths.get(modelsPathStr), filename);
    }

    /**
     * Finds a model by its filename recursively starting from the given root directory.
     */
    public Optional<Path> findModelInDirectory(Path root, String filename) {
        return findModelWithPrefSize(root, filename, -1);
    }

    /**
     * Finds a model by its filename recursively, prioritizing matches with the preferred size.
     */
    public Optional<Path> findModelWithPrefSize(Path root, String filename, long preferredSize) {
        if (filename == null || root == null || !Files.exists(root)) return Optional.empty();
        
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> matches = walk.filter(Files::isRegularFile)
                    .filter(p -> !isIgnored(p, root))
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(filename))
                    .collect(Collectors.toList());
            
            if (matches.isEmpty()) return Optional.empty();
            
            // 1. Try to find a match with exact size if preferredSize is known
            if (preferredSize > 0) {
                for (Path p : matches) {
                    try {
                        if (Files.size(p) == preferredSize) return Optional.of(p);
                    } catch (IOException ignored) {}
                }
            }
            
            // 2. Return first match if no size match found or size unknown
            return Optional.of(matches.get(0));
        } catch (IOException e) {
            System.err.println("Error searching in " + root + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isSupportedModelFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".safetensors") || name.endsWith(".ckpt") || name.endsWith(".pt");
    }

    private boolean isIgnored(Path path, Path rootPath) {
        // 1. If we are already searching inside an archive folder (e.g. E:\Archive), 
        // we should not filter out anything based on the "archive" name.
        String rootName = rootPath.getFileName() != null ? rootPath.getFileName().toString().toLowerCase() : "";
        if (rootName.contains("archive")) {
            return false;
        }

        // 2. Also check if the path is inside the specifically configured archive directory
        try {
            String archivePathStr = configService.getArchivePath();
            if (archivePathStr != null && !archivePathStr.isEmpty()) {
                Path archivePath = Paths.get(archivePathStr).toAbsolutePath();
                if (path.toAbsolutePath().startsWith(archivePath)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        // 3. Fallback: ignore any subfolder named "archive" or ".venv"
        int rootCount = rootPath.getNameCount();
        for (int i = rootCount; i < path.getNameCount(); i++) {
            String part = path.getName(i).toString().toLowerCase();
            if (part.equals("archive") || part.equals(".venv")) {
                return true;
            }
        }
        return false;
    }

    private ModelInfo createModelInfo(Path file, Path rootPath) {
        String fileName = file.getFileName().toString();
        Path relativePath = rootPath.relativize(file);
        
        // Derive type from the first folder after root
        String type = "unknown";
        if (relativePath.getNameCount() > 1) {
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
