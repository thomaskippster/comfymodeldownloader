package de.tki.comfymodels;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ComfyModelAnalyzer;
import de.tki.comfymodels.service.impl.ModelListService;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ComfyModelAnalyzerTest {

    @Test
    public void testAnalyzeBasicWorkflow() throws Exception {
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        
        // Mock ModelListService
        ModelListService mockListService = new ModelListService() {
            @Override
            public Optional<ModelInfo> findByFilename(String filename) {
                if ("flux1-schnell.safetensors".equalsIgnoreCase(filename)) {
                    return Optional.of(new ModelInfo("checkpoints", "flux1-schnell.safetensors", 
                        "https://huggingface.co/black-forest-labs/FLUX.1-schnell/resolve/main/flux1-schnell.safetensors"));
                }
                return Optional.empty();
            }
        };
        
        Field field = ComfyModelAnalyzer.class.getDeclaredField("modelListService");
        field.setAccessible(true);
        field.set(analyzer, mockListService);

        String json = "{\"nodes\": [{\"type\": \"CheckpointLoaderSimple\", \"widgets_values\": [\"flux1-schnell.safetensors\"]}]}";
        List<ModelInfo> models = analyzer.analyze(json, "test.json");

        assertFalse(models.isEmpty());
        assertEquals("flux1-schnell.safetensors", models.get(0).getName());
        assertEquals("https://huggingface.co/black-forest-labs/FLUX.1-schnell/resolve/main/flux1-schnell.safetensors", models.get(0).getUrl());
    }

    @Test
    public void testAnalyzeWithModelListSize() throws Exception {
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        
        // Mock ModelListService
        ModelListService mockListService = new ModelListService() {
            @Override
            public Optional<ModelInfo> findByFilename(String filename) {
                if ("model_with_size.safetensors".equalsIgnoreCase(filename)) {
                    ModelInfo info = new ModelInfo("checkpoints", "model_with_size.safetensors", "http://example.com/model");
                    info.setSize("1.23 GB");
                    return Optional.of(info);
                }
                return Optional.empty();
            }
        };
        
        Field field = ComfyModelAnalyzer.class.getDeclaredField("modelListService");
        field.setAccessible(true);
        field.set(analyzer, mockListService);

        String json = "{\"nodes\": [{\"type\": \"CheckpointLoaderSimple\", \"widgets_values\": [\"model_with_size.safetensors\"]}]}";
        List<ModelInfo> models = analyzer.analyze(json, "test.json");

        assertFalse(models.isEmpty());
        assertEquals("model_with_size.safetensors", models.get(0).getName());
        assertEquals("1.23 GB", models.get(0).getSize());
    }

    @Test
    public void testInvalidJson() {
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        List<ModelInfo> models = analyzer.analyze("invalid json", "test.json");
        assertTrue(models.isEmpty());
    }
}
