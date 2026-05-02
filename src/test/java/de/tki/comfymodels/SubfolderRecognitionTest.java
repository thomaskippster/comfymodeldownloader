package de.tki.comfymodels;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.LocalModelScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class SubfolderRecognitionTest {

    @TempDir
    Path tempModels;

    private LocalModelScanner localScanner;
    
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        configService = new ConfigService(null, null) {
            @Override public String getModelsPath() { return tempModels.toString(); }
            @Override public String getArchivePath() { return "archive_mock"; }
        };
        localScanner = new LocalModelScanner(configService);
    }

    @Test
    void testFindModelInDeepSubfolder() throws IOException {
        // Setup deep subfolder structure
        Path deepFolder = tempModels.resolve("diffusion_models/Wan2.2");
        Files.createDirectories(deepFolder);
        Path modelFile = deepFolder.resolve("wan2.2_test.safetensors");
        Files.createFile(modelFile);

        // Test the generic recursive search method
        Optional<Path> found = localScanner.findModelInDirectory(tempModels, "wan2.2_test.safetensors");
        assertTrue(found.isPresent(), "Model should be found in deep subfolder");
        assertEquals(modelFile.toAbsolutePath(), found.get().toAbsolutePath(), "Found path should match actual file path");
        
        // Test the convenience method that uses configService
        Optional<Path> foundViaConfig = localScanner.findModel("wan2.2_test.safetensors");
        assertTrue(foundViaConfig.isPresent(), "Model should be found via config path recursive search");
    }

    @Test
    void testFindModelWithPreferredSize() throws IOException {
        // Setup two files with same name but different sizes
        Path folderA = tempModels.resolve("folderA");
        Files.createDirectories(folderA);
        Path fileA = folderA.resolve("size_test.safetensors");
        Files.write(fileA, new byte[100]); // 100 bytes

        Path folderB = tempModels.resolve("folderB");
        Files.createDirectories(folderB);
        Path fileB = folderB.resolve("size_test.safetensors");
        Files.write(fileB, new byte[200]); // 200 bytes

        // Search for 200 bytes version
        Optional<Path> found = localScanner.findModelWithPrefSize(tempModels, "size_test.safetensors", 200);
        assertTrue(found.isPresent());
        assertEquals(fileB.toAbsolutePath(), found.get().toAbsolutePath(), "Should find the file with matching size");

        // Search for 100 bytes version
        Optional<Path> foundA = localScanner.findModelWithPrefSize(tempModels, "size_test.safetensors", 100);
        assertTrue(foundA.isPresent());
        assertEquals(fileA.toAbsolutePath(), foundA.get().toAbsolutePath(), "Should find the file with matching size");
    }

    @Test
    void testOnlyInArchive() throws IOException {
        Path archiveDir = tempModels.resolve("ExternalArchive");
        Files.createDirectories(archiveDir);
        Path archivedFile = archiveDir.resolve("exclusive_model.safetensors");
        Files.write(archivedFile, new byte[1234]);

        // Mock config to have separate models and archive paths
        ConfigService dualConfig = new ConfigService(null, null) {
            @Override public String getModelsPath() { return tempModels.toString(); }
            @Override public String getArchivePath() { return archiveDir.toString(); }
        };
        LocalModelScanner dualScanner = new LocalModelScanner(dualConfig);

        // Scan in models dir - should NOT find it
        Optional<Path> foundLocal = dualScanner.findModelInDirectory(tempModels, "exclusive_model.safetensors");
        assertFalse(foundLocal.isPresent(), "Should not find archived model in local models scan");

        // Scan in archive dir - SHOULD find it
        Optional<Path> foundArchive = dualScanner.findModelInDirectory(archiveDir, "exclusive_model.safetensors");
        assertTrue(foundArchive.isPresent(), "Should find model in archive scan");
    }
}
