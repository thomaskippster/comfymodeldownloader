package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ArchiveService;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.EncryptionUtils;
import de.tki.comfymodels.service.impl.PathResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RestorationStatusTest {

    private ArchiveService archiveService;
    private ConfigService configService;
    private Path tempModelsDir;
    private Path tempArchiveDir;

    // Status Constants (as used in Main.java)
    public static final String STATUS_FOUND_IN_ARCHIVE = "📦 Found in Archive";
    public static final String STATUS_RESTORING = "📦 Restoring...";
    public static final String STATUS_RESTORED = "✅ Restored";
    public static final String STATUS_RESTORE_FAILED = "❌ Restore Failed";

    @BeforeEach
    public void setup() throws IOException {
        tempModelsDir = Files.createTempDirectory("models_root");
        tempArchiveDir = Files.createTempDirectory("archive_root");

        PathResolver pathResolver = new PathResolver();
        configService = new ConfigService(new EncryptionUtils(), pathResolver);
        configService.setModelsPath(tempModelsDir.toString());
        configService.setArchivePath(tempArchiveDir.toString());

        archiveService = new ArchiveService(configService, pathResolver);
    }

    @AfterEach
    public void tearDown() throws IOException {
        deleteDirectory(tempModelsDir);
        deleteDirectory(tempArchiveDir);
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                 .sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(File::delete);
        }
    }

    @Test
    public void testTransition_Found_To_Restoring_To_Restored() throws IOException {
        String modelName = "test_model.safetensors";
        String folder = "checkpoints";
        
        // 1. Setup: Place model in archive
        Path archivedFolder = tempArchiveDir.resolve(folder);
        Files.createDirectories(archivedFolder);
        Path archivedFile = archivedFolder.resolve(modelName);
        Files.writeString(archivedFile, "dummy content");

        // 2. Define transitions tracker
        List<String> statusSequence = new ArrayList<>();
        
        // Initial state (determined by UI/Logic)
        statusSequence.add(STATUS_FOUND_IN_ARCHIVE);
        assertEquals(STATUS_FOUND_IN_ARCHIVE, statusSequence.get(0));

        // 3. Act: Start Restoration
        statusSequence.add(STATUS_RESTORING);
        assertEquals(STATUS_RESTORING, statusSequence.get(1));

        boolean success = archiveService.restoreFromArchive(folder, modelName);

        // 4. Validate Result and final status
        if (success) {
            statusSequence.add(STATUS_RESTORED);
        } else {
            statusSequence.add(STATUS_RESTORE_FAILED);
        }

        assertTrue(success, "Restoration should be successful");
        assertEquals(STATUS_RESTORED, statusSequence.get(2));
        
        // Verify file was moved
        assertFalse(Files.exists(archivedFile), "File should be removed from archive");
        assertTrue(Files.exists(tempModelsDir.resolve(folder).resolve(modelName)), "File should exist in models directory");
    }

    @Test
    public void testTransition_Found_To_Restoring_To_Failed() throws IOException {
        String modelName = "non_existent.safetensors";
        String folder = "checkpoints";

        // 1. Setup: No file in archive

        // 2. Define transitions tracker
        List<String> statusSequence = new ArrayList<>();
        statusSequence.add(STATUS_FOUND_IN_ARCHIVE);
        statusSequence.add(STATUS_RESTORING);

        // 3. Act: Attempt Restoration
        boolean success = archiveService.restoreFromArchive(folder, modelName);

        // 4. Validate Result and final status
        if (success) {
            statusSequence.add(STATUS_RESTORED);
        } else {
            statusSequence.add(STATUS_RESTORE_FAILED);
        }

        assertFalse(success, "Restoration should fail as file is missing");
        assertEquals(STATUS_RESTORE_FAILED, statusSequence.get(2));
        
        // Verify file does not exist in models directory
        assertFalse(Files.exists(tempModelsDir.resolve(folder).resolve(modelName)));
    }

    @Test
    public void testTransition_Found_In_Root_To_Restored() throws IOException {
        String modelName = "root_model.safetensors";
        String folder = "root";
        
        // 1. Setup: Place model in archive root
        Path archivedFile = tempArchiveDir.resolve(modelName);
        Files.writeString(archivedFile, "root content");

        // 2. Act
        boolean success = archiveService.restoreFromArchive(folder, modelName);

        // 3. Validate
        assertTrue(success);
        assertTrue(Files.exists(tempModelsDir.resolve(modelName)));
        assertFalse(Files.exists(archivedFile));
    }

    @Test
    public void testTransition_Found_To_Restoring_To_Failed_ZeroBytes() throws IOException {
        String modelName = "corrupted.safetensors";
        String folder = "checkpoints";
        
        // 1. Setup: Place a 0-byte model in archive
        Path archivedFolder = tempArchiveDir.resolve(folder);
        Files.createDirectories(archivedFolder);
        Path archivedFile = archivedFolder.resolve(modelName);
        Files.createFile(archivedFile); // 0 bytes

        // 2. Act: Attempt Restoration
        boolean success = archiveService.restoreFromArchive(folder, modelName);

        // 3. Validate
        assertFalse(success, "Restoration should fail for 0-byte files");
        assertFalse(Files.exists(tempModelsDir.resolve(folder).resolve(modelName)), "File should not be moved");
    }
}
