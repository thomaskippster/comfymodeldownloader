package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.PathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class PathResolverTest {

    @TempDir
    Path tempDir;

    private PathResolver pathResolver;
    private Path comfyRoot;

    @BeforeEach
    void setUp() throws IOException {
        comfyRoot = tempDir.resolve("ComfyUI");
        Files.createDirectories(comfyRoot);
        pathResolver = new PathResolver();
        pathResolver.setComfyUIRoot(comfyRoot.toString());
    }

    @Test
    void testResolveRelativeModelsPath() throws IOException {
        Path modelsDir = comfyRoot.resolve("models");
        Files.createDirectories(modelsDir);
        
        Path resolved = pathResolver.resolve("models");
        assertEquals(modelsDir.toAbsolutePath(), resolved.toAbsolutePath());
    }

    @Test
    void testResolveCaseInsensitiveOnLinuxSimulated() throws IOException {
        // We create 'models' but search for 'Models'
        Path modelsDir = comfyRoot.resolve("models");
        Files.createDirectories(modelsDir);
        
        // This should find 'models' even if we ask for 'Models'
        Path resolved = pathResolver.resolveCaseInsensitive(comfyRoot, "Models");
        assertEquals(modelsDir.toAbsolutePath(), resolved.toAbsolutePath());
    }

    @Test
    void testResolveAbsoluteOutsideRoot() throws IOException {
        Path outside = tempDir.resolve("external_models");
        Files.createDirectories(outside);
        
        Path resolved = pathResolver.resolve(outside.toString());
        assertEquals(outside.toAbsolutePath(), resolved.toAbsolutePath());
    }

    @Test
    void testFindCustomNodesRobust() throws IOException {
        Path customNodes = comfyRoot.resolve("custom_nodes");
        Files.createDirectories(customNodes);
        
        Path found = pathResolver.findCustomNodes(comfyRoot);
        assertEquals(customNodes.toAbsolutePath(), found.toAbsolutePath());
        
        // Test nested structure (portable/desktop app)
        Path nestedRoot = tempDir.resolve("portable");
        Path deepCustomNodes = nestedRoot.resolve("ComfyUI/custom_nodes");
        Files.createDirectories(deepCustomNodes);
        
        Path foundDeep = pathResolver.findCustomNodes(nestedRoot);
        assertEquals(deepCustomNodes.toAbsolutePath(), foundDeep.toAbsolutePath());
    }
}
