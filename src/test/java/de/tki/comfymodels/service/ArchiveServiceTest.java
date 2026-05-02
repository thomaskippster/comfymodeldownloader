package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ArchiveService;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.PathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ArchiveServiceTest {

    @TempDir
    Path tempDir;

    private ArchiveService archiveService;
    private ConfigService configService;

    private Path customArchiveDir;

    @BeforeEach
    public void setup() throws IOException {
        customArchiveDir = tempDir.resolve("my_custom_archive");
        Files.createDirectories(customArchiveDir);

        PathResolver pathResolver = new PathResolver();
        configService = new ConfigService(null, pathResolver) {
            private String modelsPath = tempDir.toString();
            private String archivePath = customArchiveDir.toString();

            @Override public String getModelsPath() { return modelsPath; }
            @Override public void setModelsPath(String p) { this.modelsPath = p; }
            @Override public String getArchivePath() { return archivePath; }
            @Override public void setArchivePath(String p) { this.archivePath = p; }
        };
        archiveService = new ArchiveService(configService, pathResolver);
    }

    @Test
    public void testMoveToCustomArchive() throws IOException {
        Path checkpointsDir = tempDir.resolve("checkpoints");
        Files.createDirectories(checkpointsDir);
        Path modelFile = checkpointsDir.resolve("model1.safetensors");
        Files.writeString(modelFile, "test content");

        archiveService.moveToArchive("checkpoints", "model1.safetensors");

        assertFalse(Files.exists(modelFile));
        Path archivedFile = customArchiveDir.resolve("checkpoints").resolve("model1.safetensors");
        assertTrue(Files.exists(archivedFile), "File should be in custom archive path");
    }

    @Test
    public void testRestoreFromArchive() throws IOException {
        Path archivedFolder = customArchiveDir.resolve("loras");
        Files.createDirectories(archivedFolder);
        Path archivedFile = archivedFolder.resolve("lora1.safetensors");
        Files.writeString(archivedFile, "test content");

        boolean restored = archiveService.restoreFromArchive("loras", "lora1.safetensors");

        assertTrue(restored);
        assertFalse(Files.exists(archivedFile));
        assertTrue(Files.exists(tempDir.resolve("loras").resolve("lora1.safetensors")));
    }

    @Test
    public void testBulkArchiveSimulation() throws IOException {
        Path checkpointsDir = tempDir.resolve("checkpoints");
        Files.createDirectories(checkpointsDir);
        Path m1 = checkpointsDir.resolve("m1.safetensors");
        Path m2 = checkpointsDir.resolve("m2.safetensors");
        Files.writeString(m1, "content1");
        Files.writeString(m2, "content2");

        archiveService.moveToArchive("checkpoints", "m1.safetensors");
        archiveService.moveToArchive("checkpoints", "m2.safetensors");

        assertFalse(Files.exists(m1));
        assertFalse(Files.exists(m2));
        assertTrue(Files.exists(customArchiveDir.resolve("checkpoints").resolve("m1.safetensors")));
        assertTrue(Files.exists(customArchiveDir.resolve("checkpoints").resolve("m2.safetensors")));
    }

    @Test
    public void testGetModelsGroupedByFolder() throws IOException {
        Path checkpointsDir = tempDir.resolve("checkpoints");
        Path lorasDir = tempDir.resolve("loras");
        Files.createDirectories(checkpointsDir);
        Files.createDirectories(lorasDir);
        
        Files.writeString(checkpointsDir.resolve("model1.safetensors"), "c1");
        Files.writeString(lorasDir.resolve("lora1.safetensors"), "l1");
        Files.writeString(tempDir.resolve("root_model.ckpt"), "r1");

        Map<String, List<ModelInfo>> grouped = archiveService.getModelsGroupedByFolder();

        assertEquals(3, grouped.size());
        assertTrue(grouped.containsKey("checkpoints"));
        assertTrue(grouped.containsKey("loras"));
        assertTrue(grouped.containsKey("root"));
        
        assertEquals(1, grouped.get("checkpoints").size());
        assertEquals("model1.safetensors", grouped.get("checkpoints").get(0).getName());
    }

    @Test
    public void testMoveToArchive() throws IOException {
        Path checkpointsDir = tempDir.resolve("checkpoints");
        Files.createDirectories(checkpointsDir);
        Path modelFile = checkpointsDir.resolve("model1.safetensors");
        Files.writeString(modelFile, "test content");

        archiveService.moveToArchive("checkpoints", "model1.safetensors");

        assertFalse(Files.exists(modelFile));
        Path archivedFile = customArchiveDir.resolve("checkpoints").resolve("model1.safetensors");
        assertTrue(Files.exists(archivedFile));
    }

    @Test
    public void testMoveRootModelToArchive() throws IOException {
        Path modelFile = tempDir.resolve("root_model.ckpt");
        Files.writeString(modelFile, "test content");

        archiveService.moveToArchive("root", "root_model.ckpt");

        assertFalse(Files.exists(modelFile));
        Path archivedFile = customArchiveDir.resolve("root_model.ckpt");
        assertTrue(Files.exists(archivedFile));
    }

    @Test
    public void testArchivedModelsAreIgnored() throws IOException {
        Path archiveDir = customArchiveDir.resolve("checkpoints");
        Files.createDirectories(archiveDir);
        Files.writeString(archiveDir.resolve("old_model.safetensors"), "old");

        Map<String, List<ModelInfo>> grouped = archiveService.getModelsGroupedByFolder();
        
        grouped.values().forEach(list -> {
            list.forEach(m -> assertNotEquals("old_model.safetensors", m.getName()));
        });
    }
}
