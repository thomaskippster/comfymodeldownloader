package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Global test suite covering all functional blocks and their status transitions.
 */
public class ComprehensiveServiceTest {

    @TempDir
    Path tempDir;

    private ComfyModelAnalyzer analyzer;
    private WorkflowService workflowService;
    private ModelValidator validator;
    private DefaultDownloadManager downloadManager;
    private ArchiveService archiveService;
    private ConfigService configService;

    @BeforeEach
    public void setup() throws Exception {
        PathResolver pathResolver = new PathResolver();
        configService = new ConfigService(new EncryptionUtils(), pathResolver);
        ReflectionTestUtils.setField(configService, "masterPassword", "test-pass");
        configService.setModelsPath(tempDir.resolve("models").toString());
        configService.setArchivePath(tempDir.resolve("archive").toString());

        analyzer = new ComfyModelAnalyzer();
        workflowService = new WorkflowService();
        validator = new ModelValidator();
        downloadManager = new DefaultDownloadManager();
        archiveService = new ArchiveService(configService, pathResolver);
    }

    // --- 1. ANALYSIS & DETECTION TESTS ---
    @Test
    public void testFullAnalysis_Lora_Checkpoint_VAE() {
        String workflow = "{" +
            "\"1\": {\"class_type\": \"CheckpointLoaderSimple\", \"widgets_values\": [\"sdxl_base.safetensors\"]}," +
            "\"2\": {\"class_type\": \"LoraLoader\", \"widgets_values\": [\"detailer.safetensors\"]}," +
            "\"3\": {\"class_type\": \"VAELoader\", \"widgets_values\": [\"vae_fix.safetensors\"]}" +
            "}";
        
        List<ModelInfo> results = analyzer.analyze(workflow, "comprehensive.json");
        
        assertEquals(3, results.size());
        assertTrue(results.stream().anyMatch(m -> "sdxl_base.safetensors".equals(m.getName()) && "checkpoints".equals(m.getType())));
        assertTrue(results.stream().anyMatch(m -> "detailer.safetensors".equals(m.getName()) && "loras".equals(m.getType())));
        assertTrue(results.stream().anyMatch(m -> "vae_fix.safetensors".equals(m.getName()) && "vae".equals(m.getType())));
    }

    // --- 2. WORKFLOW EXTRACTION TESTS ---
    @Test
    public void testWorkflowExtraction_TextAndJson() throws IOException {
        String rawJson = "{\"test\": \"data\"}";
        File jsonFile = tempDir.resolve("workflow.json").toFile();
        Files.writeString(jsonFile.toPath(), rawJson);
        
        String extracted = workflowService.extractWorkflow(jsonFile);
        assertEquals(rawJson, extracted);
    }

    // --- 3. VALIDATION TESTS ---
    @Test
    public void testValidator_CorruptedFile() throws IOException {
        Path corrupted = tempDir.resolve("corrupted.safetensors");
        Files.writeString(corrupted, "Not a safetensors file");
        
        IModelValidator.ValidationResult res = validator.validateFile(corrupted.toFile());
        assertFalse(res.ok);
        assertTrue(res.message.contains("Header") || res.message.contains("Invalid"));
    }

    // --- 4. ARCHIVE & STATUS TRANSITION TESTS ---
    @Test
    public void testArchiveTransition_MoveToArchive() throws IOException {
        Path modelsDir = tempDir.resolve("models").resolve("checkpoints");
        Files.createDirectories(modelsDir);
        Path modelFile = modelsDir.resolve("test.safetensors");
        Files.writeString(modelFile, "model data");

        // Act: Move to archive
        archiveService.moveToArchive("checkpoints", "test.safetensors");

        // Verify: Status change in file system
        assertFalse(Files.exists(modelFile), "Original file should be gone");
        assertTrue(Files.exists(tempDir.resolve("archive").resolve("checkpoints").resolve("test.safetensors")), "File should be in archive");
    }

    @Test
    public void testRestorationTransition_Success() throws IOException {
        Path archiveDir = tempDir.resolve("archive").resolve("loras");
        Files.createDirectories(archiveDir);
        Path archivedFile = archiveDir.resolve("my_lora.safetensors");
        Files.writeString(archivedFile, "lora data");

        // Act: Restore
        boolean success = archiveService.restoreFromArchive("loras", "my_lora.safetensors");

        // Verify
        assertTrue(success);
        assertTrue(Files.exists(tempDir.resolve("models").resolve("loras").resolve("my_lora.safetensors")));
    }

    // --- 5. DOWNLOAD MANAGER STATUS TRANSITIONS ---
    @Test
    public void testDownloadStatus_TransitionSequence() throws Exception {
        ModelInfo info = new ModelInfo("checkpoints", "down.safetensors", "http://invalid-url-for-test");
        List<ModelInfo> list = List.of(info);
        boolean[] selected = {true};
        
        AtomicReference<String> lastStatus = new AtomicReference<>("Idle");
        
        // This won't actually download but should trigger a failure/error transition
        downloadManager.startQueue(list, selected, tempDir.resolve("models").toString(),
            (idx, status) -> lastStatus.set(status),
            () -> {} // onFinished
        );
        
        // Wait briefly for the first status update
        Thread.sleep(100);
        
        String status = lastStatus.get();
        // Should be either "Connecting...", "Error...", or "Downloading..."
        assertNotNull(status);
        assertNotEquals("Idle", status);
        
        downloadManager.stop();
    }
}
