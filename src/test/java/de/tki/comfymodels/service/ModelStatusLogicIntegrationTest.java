package de.tki.comfymodels.service;

import de.tki.comfymodels.Main;
import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.*;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class ModelStatusLogicIntegrationTest {

    @TempDir
    Path tempDir;

    private Main main;
    private ConfigService configService;
    private PathResolver pathResolver;
    private ArchiveService archiveService;
    private LocalModelScanner localScanner;
    private ComfyModelAnalyzer analyzer;

    @BeforeEach
    public void setup() throws Exception {
        pathResolver = new PathResolver();
        configService = new ConfigService(null, pathResolver) {
            private String modelsPath = "";
            private String archivePath = "";
            @Override public String getModelsPath() { return modelsPath; }
            @Override public void setModelsPath(String path) { this.modelsPath = path; }
            @Override public String getArchivePath() { return archivePath; }
            @Override public void setArchivePath(String path) { this.archivePath = path; }
            @Override public String getAppDataPath() { return tempDir.toString(); }
            @Override public boolean isUnlocked() { return true; }
            @Override public void save() {}
        };

        analyzer = Mockito.mock(ComfyModelAnalyzer.class);
        localScanner = new LocalModelScanner(configService, pathResolver);
        archiveService = new ArchiveService(configService, pathResolver);
        
        main = new Main(
            analyzer,
            Mockito.mock(DefaultDownloadManager.class),
            Mockito.mock(WorkflowService.class),
            Mockito.mock(ModelSearchService.class),
            Mockito.mock(ModelValidator.class),
            Mockito.mock(RestBridgeService.class),
            archiveService,
            Mockito.mock(ComfyLifecycleService.class),
            Mockito.mock(ComfyDiagnosticService.class)
        );

        // Inject dependencies via reflection since they are @Autowired in Main
        setField(main, "configService", configService);
        setField(main, "localScanner", localScanner);
        setField(main, "pathResolver", pathResolver);
        setField(main, "archiveService", archiveService);

        // Initialize enough UI for analyzeJsonContent to work
        setField(main, "tableModel", new DefaultTableModel(new String[]{"S", "T", "N", "Sz", "Src", "P", "U", "Status"}, 0));
        JTextArea area = new JTextArea("dummy workflow");
        setField(main, "jsonInputArea", area);
        setField(main, "statusLabel", new JLabel());
        setField(main, "downloadButton", new JButton());
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    public void testStatusTransitions() throws IOException, Exception {
        Path modelsDir = tempDir.resolve("models");
        Files.createDirectories(modelsDir.resolve("checkpoints"));
        configService.setModelsPath(modelsDir.toString());

        Path archiveDir = tempDir.resolve("archive");
        Files.createDirectories(archiveDir.resolve("checkpoints"));
        configService.setArchivePath(archiveDir.toString());

        // Prepare 3 scenarios
        List<ModelInfo> models = new ArrayList<>();
        models.add(new ModelInfo("checkpoints", "local_only.safetensors", "COMMUNITY"));
        models.get(0).setUrl("http://example.com/1");
        
        models.add(new ModelInfo("checkpoints", "archived_only.safetensors", "COMMUNITY"));
        models.get(1).setUrl("http://example.com/2");
        
        models.add(new ModelInfo("checkpoints", "both_locations.safetensors", "COMMUNITY"));
        models.get(2).setUrl("http://example.com/3");

        models.add(new ModelInfo("checkpoints", "new_model.safetensors", "COMMUNITY"));
        models.get(3).setUrl("http://example.com/4");

        when(analyzer.analyze(anyString(), anyString())).thenReturn(models);

        // Create the files
        Files.writeString(modelsDir.resolve("checkpoints/local_only.safetensors"), "data");
        Files.writeString(archiveDir.resolve("checkpoints/archived_only.safetensors"), "data");
        Files.writeString(modelsDir.resolve("checkpoints/both_locations.safetensors"), "data");
        Files.writeString(archiveDir.resolve("checkpoints/both_locations.safetensors"), "data");

        // Execute analysis
        java.lang.reflect.Method method = main.getClass().getDeclaredMethod("analyzeJsonContent");
        method.setAccessible(true);
        method.invoke(main);

        DefaultTableModel model = (DefaultTableModel) getField(main, "tableModel");

        // Verify Statuses
        assertEquals("✅ Already exists", model.getValueAt(0, 7), "Should be local only");
        assertEquals("📦 Archived", model.getValueAt(1, 7), "Should be in archive only");
        assertEquals("✅ Already exists", model.getValueAt(2, 7), "Should prioritize local over archive (obsolete status removed)");
        assertEquals("✅ Known Good", model.getValueAt(3, 7), "Should be new/downloadable");
    }

    private Object getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
