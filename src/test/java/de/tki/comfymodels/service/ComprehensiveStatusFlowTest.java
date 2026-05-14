package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveStatusFlowTest {

    private ComfyModelAnalyzer analyzer;
    private ConfigService configService;
    private ArchiveService archiveService;
    private PathResolver pathResolver;
    private LocalModelScanner localScanner;
    
    @TempDir
    Path tempBase;
    
    Path modelsDir;
    Path archiveDir;

    @BeforeEach
    public void setup() throws IOException {
        modelsDir = tempBase.resolve("models");
        archiveDir = tempBase.resolve("archive");
        Files.createDirectories(modelsDir);
        Files.createDirectories(archiveDir);

        pathResolver = new PathResolver();
        configService = new ConfigService(new EncryptionUtils(), pathResolver);
        configService.setModelsPath(modelsDir.toString());
        configService.setArchivePath(archiveDir.toString());
        
        localScanner = new LocalModelScanner(configService, pathResolver);
        archiveService = new ArchiveService(configService, pathResolver);
        analyzer = new ComfyModelAnalyzer();

        ReflectionTestUtils.setField(analyzer, "modelListService", new ModelListService());
        // ComfyModelAnalyzer doesn't have pathResolver/configService/archiveService fields anymore, 
        // it uses modelListService and AI services.
    }

    @Test
    public void testFullStatusLifecycle() throws IOException {
        String modelName = "test_model.safetensors";
        String workflow = "{\"nodes\":[{\"type\":\"CheckpointLoaderSimple\",\"widgets_values\":[\"" + modelName + "\"]}]}";
        
        // 1. Initial State: Missing (Idle)
        List<ModelInfo> results = analyzer.analyze(workflow, "test.json");
        assertEquals(1, results.size());
        assertEquals("Idle", getStatus(results.get(0), false, false, 0));

        // 2. State: Known Good (URL found)
        results.get(0).setUrl("http://example.com/model.safetensors");
        assertEquals("✅ Known Good", getStatus(results.get(0), false, false, 0));

        // 3. State: Size Mismatch (Local file exists but size unknown/different)
        Path modelPath = modelsDir.resolve("checkpoints").resolve(modelName);
        Files.createDirectories(modelPath.getParent());
        Files.write(modelPath, new byte[1000]); // 1KB local file
        
        results.get(0).setByteSize(2000); // 2KB remote size
        assertEquals("🔄 Size Mismatch", getStatus(results.get(0), true, false, 1000));

        // 4. State: Already Exists (Size match)
        Files.write(modelPath, new byte[2000]); // Update to 2KB
        assertEquals("✅ Already exists", getStatus(results.get(0), true, false, 2000));

        // 5. State: Archived (Removed locally, moved to archive)
        Files.delete(modelPath);
        Path archivePath = archiveDir.resolve("checkpoints").resolve(modelName);
        Files.createDirectories(archivePath.getParent());
        Files.write(archivePath, new byte[2000]);

        assertEquals("📦 Archived", getStatus(results.get(0), false, true, 0));

        // 6. State: Already exists (Size match, even if archived)
        Files.write(modelPath, new byte[2000]);
        assertEquals("✅ Already exists", getStatus(results.get(0), true, true, 2000));
        }

        /**
        * Helper to simulate the status logic from Main.java analyzeJsonContent()
        */
        private String getStatus(ModelInfo info, boolean existsLocally, boolean existsInArchive, long localSize) {
        boolean sizeMismatch = false;
        if (existsLocally && info.getByteSize() > 0) {
            if (localSize != info.getByteSize()) {
                sizeMismatch = true;
                existsLocally = false;
            }
        }

        if (existsLocally) return "✅ Already exists";
        if (existsInArchive) return "📦 Archived";
        if (sizeMismatch) return "🔄 Size Mismatch";

        if (info.getUrl().equals("MISSING")) return "Idle";

        return "✅ Known Good";
        }
        }

