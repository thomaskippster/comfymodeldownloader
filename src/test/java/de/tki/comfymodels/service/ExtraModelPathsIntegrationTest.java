package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ArchiveService;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.LocalModelScanner;
import de.tki.comfymodels.service.impl.PathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ExtraModelPathsIntegrationTest {

    @TempDir
    Path tempDir;

    private LocalModelScanner scanner;
    private ConfigService configService;
    private PathResolver pathResolver;
    private ArchiveService archiveService;

    @BeforeEach
    public void setup() {
        pathResolver = new PathResolver();
        // Custom ConfigService to use our tempDir for everything and avoid complex setup
        configService = new ConfigService(null, pathResolver) {
            private String comfyPath = "";
            private String modelsPath = "";
            private String archivePath = "";

            @Override public String getComfyUIPath() { return comfyPath; }
            @Override public void setComfyUIPath(String path) { this.comfyPath = path; pathResolver.setComfyUIRoot(path); }
            @Override public String getModelsPath() { return modelsPath; }
            @Override public void setModelsPath(String path) { this.modelsPath = path; }
            @Override public String getArchivePath() { return archivePath; }
            @Override public void setArchivePath(String path) { this.archivePath = path; }
            @Override public String getAppDataPath() { return tempDir.toString(); }
            @Override public void save() { /* avoid encryption issues in test */ }
            @Override public boolean isUnlocked() { return true; }
        };
        scanner = new LocalModelScanner(configService, pathResolver);
        archiveService = new ArchiveService(configService, pathResolver);
    }

    @Test
    public void testExtraModelPathsDiscovery() throws IOException {
        // 1. Setup ComfyUI Root and extra_model_paths.yaml
        Path comfyRoot = tempDir.resolve("ComfyUI");
        Files.createDirectories(comfyRoot);
        configService.setComfyUIPath(comfyRoot.toString());

        Path extraModelsDir = tempDir.resolve("ExternalModels");
        Path checkpointsDir = extraModelsDir.resolve("my_checkpoints");
        Files.createDirectories(checkpointsDir);

        // Create a model file in the extra path
        Path modelFile = checkpointsDir.resolve("extra_model.safetensors");
        Files.writeString(modelFile, "dummy");

        // Create the YAML
        String yamlContent = 
            "my_extra_path:\n" +
            "    base_path: " + extraModelsDir.toAbsolutePath().toString().replace("\\", "/") + "\n" +
            "    checkpoints: my_checkpoints\n";
        
        Files.writeString(comfyRoot.resolve("extra_model_paths.yaml"), yamlContent);

        // 2. Load the extra paths
        configService.loadExtraModelPaths();

        // 3. Verify PathResolver knows about it
        List<Path> paths = pathResolver.getModelPaths("checkpoints");
        assertFalse(paths.isEmpty(), "Should have found extra checkpoints path");
        assertTrue(paths.stream().anyMatch(p -> p.toAbsolutePath().equals(checkpointsDir.toAbsolutePath())), "Should contain the correct checkpoints dir");

        // 4. Run Scanner and verify discovery
        List<ModelInfo> results = scanner.scanLocalModels();
        
        boolean found = results.stream().anyMatch(m -> m.getName().equals("extra_model.safetensors"));
        assertTrue(found, "Scanner should find model in extra path");
        
        ModelInfo info = results.stream().filter(m -> m.getName().equals("extra_model.safetensors")).findFirst().get();
        assertEquals("checkpoints", info.getType(), "Should correctly identify as checkpoint");
    }

    @Test
    public void testArchiveRestorationToExtraPath() throws IOException {
        // 1. Setup ComfyUI Root and extra_model_paths.yaml
        Path comfyRoot = tempDir.resolve("ComfyUI");
        Files.createDirectories(comfyRoot);
        configService.setComfyUIPath(comfyRoot.toString());

        Path extraModelsDir = tempDir.resolve("ExternalModels");
        Path checkpointsDir = extraModelsDir.resolve("my_checkpoints");
        Files.createDirectories(checkpointsDir);
        
        // Create the YAML mapping checkpoints to ExternalModels/my_checkpoints
        String yamlContent = 
            "my_extra_path:\n" +
            "    base_path: " + extraModelsDir.toAbsolutePath().toString().replace("\\", "/") + "\n" +
            "    checkpoints: my_checkpoints\n";
        Files.writeString(comfyRoot.resolve("extra_model_paths.yaml"), yamlContent);
        configService.loadExtraModelPaths();

        // 2. Setup Archive
        Path archiveRoot = tempDir.resolve("Archive");
        Files.createDirectories(archiveRoot);
        configService.setArchivePath(archiveRoot.toString());
        
        // Put a model in the archive structure
        Path archivedModel = archiveRoot.resolve("checkpoints").resolve("archived_extra.safetensors");
        Files.createDirectories(archivedModel.getParent());
        Files.writeString(archivedModel, "dummy model data");

        // 3. Run restoration for "checkpoints" folder
        boolean success = archiveService.restoreFromArchiveWithProgress("checkpoints", "archived_extra.safetensors", null);
        
        assertTrue(success, "Restoration should be successful");
        
        // 4. VERIFY: Should be in checkpointsDir (extra path)
        Path expectedTarget = checkpointsDir.resolve("archived_extra.safetensors");
        assertTrue(Files.exists(expectedTarget), "Model should be restored to the extra checkpoints path: " + expectedTarget);
        assertFalse(Files.exists(archivedModel), "Source should be deleted from archive after successful move");
    }

    @Test
    public void testArchiveRestorationToNestedExtraPath() throws IOException {
        // Setup same as above but with nested folder
        Path comfyRoot = tempDir.resolve("ComfyUI");
        Files.createDirectories(comfyRoot);
        configService.setComfyUIPath(comfyRoot.toString());

        Path extraModelsDir = tempDir.resolve("ExternalModels");
        Path checkpointsDir = extraModelsDir.resolve("checkpoints");
        Path sd15Dir = checkpointsDir.resolve("SD15");
        Files.createDirectories(sd15Dir);
        
        String yamlContent = 
            "my_extra_path:\n" +
            "    base_path: " + extraModelsDir.toAbsolutePath().toString().replace("\\", "/") + "\n" +
            "    checkpoints: checkpoints\n";
        Files.writeString(comfyRoot.resolve("extra_model_paths.yaml"), yamlContent);
        configService.loadExtraModelPaths();

        // Setup Archive
        Path archiveRoot = tempDir.resolve("Archive");
        Files.createDirectories(archiveRoot);
        configService.setArchivePath(archiveRoot.toString());
        
        // Model in archive: checkpoints/SD15/nested.safetensors
        Path archivedModel = archiveRoot.resolve("checkpoints/SD15/nested.safetensors");
        Files.createDirectories(archivedModel.getParent());
        Files.writeString(archivedModel, "nested data");

        // Run restoration for "checkpoints/SD15"
        boolean success = archiveService.restoreFromArchiveWithProgress("checkpoints/SD15", "nested.safetensors", null);
        
        assertTrue(success, "Nested restoration should be successful");
        
        // VERIFY: Should be in sd15Dir (extra path + subfolder)
        Path expectedTarget = sd15Dir.resolve("nested.safetensors");
        assertTrue(Files.exists(expectedTarget), "Model should be restored to the nested extra path: " + expectedTarget);
    }
}
