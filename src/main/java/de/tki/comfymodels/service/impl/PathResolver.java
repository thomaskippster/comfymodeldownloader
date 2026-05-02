package de.tki.comfymodels.service.impl;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Component
public class PathResolver {

    public static final String MODELS_DIR = "models";
    public static final String ARCHIVE_DIR = "archive";
    public static final String CUSTOM_NODES_DIR = "custom_nodes";

    private String comfyUIRoot;

    public void setComfyUIRoot(String root) {
        this.comfyUIRoot = root;
    }

    public String getComfyUIRoot() {
        return comfyUIRoot;
    }

    /**
     * Resolves a path. If it's absolute, returns it. 
     * If relative, resolves against ComfyUI root.
     */
    public Path resolve(String inputPath) {
        if (inputPath == null || inputPath.isEmpty()) return null;
        
        Path p = Paths.get(inputPath);
        if (p.isAbsolute()) return p;
        
        if (comfyUIRoot == null || comfyUIRoot.isEmpty()) return p.toAbsolutePath();
        
        return Paths.get(comfyUIRoot).resolve(inputPath);
    }

    /**
     * Tries to find a file/folder case-insensitively within a parent directory.
     */
    public Path resolveCaseInsensitive(Path parent, String childName) {
        if (childName == null || childName.isEmpty() || childName.equals("root")) return parent;
        if (!Files.exists(parent) || !Files.isDirectory(parent)) return parent.resolve(childName);
        
        try (Stream<Path> stream = Files.list(parent)) {
            return stream
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(childName))
                    .findFirst()
                    .orElse(parent.resolve(childName));
        } catch (IOException e) {
            return parent.resolve(childName);
        }
    }

    /**
     * Recursively resolves a path case-insensitively.
     * E.g. "MODELS/CHECKPOINTS" -> "models/checkpoints"
     */
    public Path resolveCaseInsensitiveRecursive(Path root, String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return root;
        
        Path current = root;
        String normalizedPath = relativePath.replace("\\", "/");
        String[] segments = normalizedPath.split("/");
        
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".")) continue;
            current = resolveCaseInsensitive(current, segment);
        }
        
        return current;
    }

    /**
     * Robust search for custom_nodes directory starting from a root path.
     */
    public Path findCustomNodes(Path root) {
        if (root == null) return null;
        
        // 1. Direct check: Is the root itself the custom_nodes folder?
        if (root.getFileName() != null && root.getFileName().toString().equalsIgnoreCase(CUSTOM_NODES_DIR)) {
            return root;
        }

        // 2. Immediate candidates (common layouts)
        String[] candidates = {
            "resources/ComfyUI/" + CUSTOM_NODES_DIR,
            "ComfyUI/" + CUSTOM_NODES_DIR,
            CUSTOM_NODES_DIR
        };
        
        for (String c : candidates) {
            Path p = root.resolve(c);
            if (Files.exists(p) && Files.isDirectory(p)) return p;
        }
        
        // 3. Fallback: Search 3 levels deeper for non-standard structures
        return findDeep(root, CUSTOM_NODES_DIR, 3);
    }

    /**
     * Resolves the models path. Tries to find it case-insensitively if it's relative.
     */
    public Path resolveModelsPath(String configPath) {
        if (configPath == null || configPath.isEmpty()) configPath = MODELS_DIR;
        Path p = Paths.get(configPath);
        if (p.isAbsolute()) return p;
        
        if (comfyUIRoot != null && !comfyUIRoot.isEmpty()) {
            return resolveCaseInsensitiveRecursive(Paths.get(comfyUIRoot), configPath);
        }
        return p.toAbsolutePath();
    }

    /**
     * Resolves the archive path. Usually relative to models path if not absolute.
     */
    public Path resolveArchivePath(Path modelsPath, String configPath) {
        if (configPath == null || configPath.isEmpty()) configPath = ARCHIVE_DIR;
        Path p = Paths.get(configPath);
        if (p.isAbsolute()) return p;
        
        return resolveCaseInsensitiveRecursive(modelsPath, configPath);
    }

    /**
     * Resolves a specific model folder (e.g. checkpoints) within the models directory.
     */
    public Path resolveModelFolder(Path modelsPath, de.tki.comfymodels.domain.ModelFolder folder) {
        return resolveCaseInsensitive(modelsPath, folder.getDefaultFolderName());
    }
/**
 * Resolves a specific model folder by string name.
 */
public Path resolveModelFolder(Path modelsPath, String folderName) {
    return resolveCaseInsensitiveRecursive(modelsPath, stripRedundantPrefixes(folderName));
}

private Path findDeep(Path root, String targetName, int maxDepth) {
    try (Stream<Path> stream = Files.walk(root, maxDepth)) {
        return stream
                .filter(p -> Files.isDirectory(p))
                .filter(p -> p.getFileName() != null && p.getFileName().toString().equalsIgnoreCase(targetName))
                .findFirst()
                .orElse(null);
    } catch (IOException e) {
        return null;
    }
}

/**
 * Strips redundant prefixes like "models/" or "archive/" and returns a normalized relative path.
 */
public String stripRedundantPrefixes(String folder) {
    if (folder == null || folder.isEmpty() || folder.equalsIgnoreCase("root")) return "root";

    String normalized = folder.replace("\\", "/");

    if (normalized.toLowerCase().startsWith(ARCHIVE_DIR + "/")) {
        normalized = normalized.substring(ARCHIVE_DIR.length() + 1);
    } else if (normalized.toLowerCase().equals(ARCHIVE_DIR)) {
        return "root";
    }

    if (normalized.toLowerCase().startsWith(MODELS_DIR + "/")) {
        normalized = normalized.substring(MODELS_DIR.length() + 1);
    } else if (normalized.toLowerCase().equals(MODELS_DIR)) {
        return "root";
    }

    if (normalized.isEmpty() || normalized.equals("/") || normalized.equalsIgnoreCase("root")) return "root";

    return normalized;
}
}

