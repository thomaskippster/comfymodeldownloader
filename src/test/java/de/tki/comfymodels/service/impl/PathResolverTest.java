package de.tki.comfymodels.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PathResolverTest {

    private PathResolver pathResolver;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pathResolver = new PathResolver();
    }

    @Test
    void testResolveAbsolute() {
        Path absolutePath = tempDir.resolve("absolute").toAbsolutePath();
        assertEquals(absolutePath, pathResolver.resolve(absolutePath.toString()));
    }

    @Test
    void testResolveRelativeWithRoot() {
        pathResolver.setComfyUIRoot(tempDir.toString());
        Path resolved = pathResolver.resolve("models/checkpoints");
        assertEquals(tempDir.resolve("models/checkpoints"), resolved);
    }

    @Test
    void testResolveCaseInsensitive() throws IOException {
        Path models = Files.createDirectory(tempDir.resolve("Models"));
        Path checkpoints = Files.createDirectory(models.resolve("Checkpoints"));
        
        Path resolved = pathResolver.resolveCaseInsensitive(tempDir, "models");
        assertEquals(models, resolved);
        
        Path resolvedCheckpoints = pathResolver.resolveCaseInsensitive(models, "checkpoints");
        assertEquals(checkpoints, resolvedCheckpoints);
    }

    @Test
    void testFindCustomNodes() throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("ComfyUI_Root"));
        Path customNodes = Files.createDirectories(root.resolve("custom_nodes"));
        
        assertEquals(customNodes, pathResolver.findCustomNodes(root));
        
        // Portable layout
        Path portableRoot = Files.createDirectory(tempDir.resolve("Portable"));
        Path portableCustomNodes = Files.createDirectories(portableRoot.resolve("resources/ComfyUI/custom_nodes"));
        
        assertEquals(portableCustomNodes, pathResolver.findCustomNodes(portableRoot));
    }
    
    @Test
    void testResolvePathWithMultipleSegments() throws IOException {
        Files.createDirectories(tempDir.resolve("models/checkpoints"));
        pathResolver.setComfyUIRoot(tempDir.toString());
        
        // This should find 'models/checkpoints' even if input is 'MODELS/CHECKPOINTS'
        // if we implement recursive case-insensitive resolution.
        Path resolved = pathResolver.resolveCaseInsensitiveRecursive(tempDir, "MODELS/CHECKPOINTS");
        assertEquals(tempDir.resolve("models/checkpoints"), resolved);
    }
}
