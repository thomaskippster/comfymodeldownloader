package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.LocalModelScanner;
import de.tki.comfymodels.service.impl.PathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LocalModelScannerTest {

    @TempDir
    Path tempDir;

    private LocalModelScanner scanner;
    private ConfigService configService;

    @BeforeEach
    public void setup() {
        // Use a simple stub for ConfigService to avoid Mockito issues on Java 25
        configService = new ConfigService(null, new PathResolver()) {
            @Override
            public String getModelsPath() {
                return tempDir.toString();
            }
        };
        scanner = new LocalModelScanner(configService);
    }

    @Test
    public void testScanLocalModels() throws IOException {
        // Arrange: Create folder structure
        // models/checkpoints/test1.safetensors
        // models/loras/test2.ckpt
        // models/archive/old.pt (should be ignored)
        // models/unet/.venv/hidden.safetensors (should be ignored)
        
        Path checkpointsDir = tempDir.resolve("checkpoints");
        Path lorasDir = tempDir.resolve("loras");
        Path archiveDir = tempDir.resolve("archive");
        Path venvDir = tempDir.resolve("unet").resolve(".venv");
        
        Files.createDirectories(checkpointsDir);
        Files.createDirectories(lorasDir);
        Files.createDirectories(archiveDir);
        Files.createDirectories(venvDir);
        
        Files.writeString(checkpointsDir.resolve("test1.safetensors"), "dummy content");
        Files.writeString(lorasDir.resolve("test2.ckpt"), "dummy content");
        Files.writeString(archiveDir.resolve("old.pt"), "dummy content");
        Files.writeString(venvDir.resolve("hidden.safetensors"), "dummy content");

        // Act
        List<ModelInfo> results = scanner.scanLocalModels();

        // Assert
        assertEquals(2, results.size(), "Should find exactly 2 models (excluding archive and .venv)");
        
        ModelInfo m1 = results.stream().filter(m -> m.getName().equals("test1.safetensors")).findFirst().orElse(null);
        assertNotNull(m1);
        assertEquals("checkpoints", m1.getType());

        ModelInfo m2 = results.stream().filter(m -> m.getName().equals("test2.ckpt")).findFirst().orElse(null);
        assertNotNull(m2);
        assertEquals("loras", m2.getType());
        
        // Ensure nothing from .venv or archive was found
        assertFalse(results.stream().anyMatch(m -> m.getName().equals("old.pt")));
        assertFalse(results.stream().anyMatch(m -> m.getName().equals("hidden.safetensors")));
    }

    @Test
    public void testEmptyDirectory() {
        List<ModelInfo> results = scanner.scanLocalModels();
        assertTrue(results.isEmpty());
    }
}
