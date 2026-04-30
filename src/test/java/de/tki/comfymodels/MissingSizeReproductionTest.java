package de.tki.comfymodels;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ComfyModelAnalyzer;
import de.tki.comfymodels.service.impl.ModelListService;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class MissingSizeReproductionTest {

    @Test
    public void testNakedUrlSizeIsUnknown() throws Exception {
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        
        // No ModelListService match for this one
        ModelListService mockListService = new ModelListService() {
            @Override
            public Optional<ModelInfo> findByFilename(String filename) {
                return Optional.empty();
            }
        };
        
        Field field = ComfyModelAnalyzer.class.getDeclaredField("modelListService");
        field.setAccessible(true);
        field.set(analyzer, mockListService);

        // A workflow containing a naked URL to a model
        String json = "{\"nodes\": [{\"type\": \"CheckpointLoaderSimple\", \"widgets_values\": [\"https://huggingface.co/Comfy-Org/ERNIE-Image/resolve/main/diffusion_models/ernie-image-turbo.safetensors\"]}]}";
        List<ModelInfo> models = analyzer.analyze(json, "test.json");

        assertFalse(models.isEmpty());
        ModelInfo info = models.get(0);
        assertEquals("ernie-image-turbo.safetensors", info.getName());
        assertEquals("https://huggingface.co/Comfy-Org/ERNIE-Image/resolve/main/diffusion_models/ernie-image-turbo.safetensors", info.getUrl());
        
        // This reproduces the issue: Size is "Unknown" because analyzer doesn't do network calls
        assertEquals("Unknown", info.getSize());
    }

    @Test
    public void testUrlWithUnderscoresIsFound() throws Exception {
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        String json = "{\"nodes\": [{\"type\": \"CheckpointLoaderSimple\", \"widgets_values\": [\"https://huggingface.co/Comfy-Org/ERNIE-Image/resolve/main/diffusion_models/ernie_image_turbo.safetensors\"]}]}";
        List<ModelInfo> models = analyzer.analyze(json, "test.json");

        assertFalse(models.isEmpty(), "Should find URL with underscores");
        assertEquals("ernie_image_turbo.safetensors", models.get(0).getName());
    }

    @Test
    public void testModelListMatchWithoutSize() throws Exception {
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        
        ModelListService mockListService = new ModelListService() {
            @Override
            public Optional<ModelInfo> findByFilename(String filename) {
                if ("ernie-image-turbo.safetensors".equalsIgnoreCase(filename)) {
                    // Entry in model list has URL but NO size (or "Unknown")
                    ModelInfo info = new ModelInfo("diffusion_models", "ernie-image-turbo.safetensors", 
                        "https://huggingface.co/Comfy-Org/ERNIE-Image/resolve/main/diffusion_models/ernie-image-turbo.safetensors");
                    info.setSize("Unknown"); 
                    return Optional.of(info);
                }
                return Optional.empty();
            }
        };
        
        Field field = ComfyModelAnalyzer.class.getDeclaredField("modelListService");
        field.setAccessible(true);
        field.set(analyzer, mockListService);

        String json = "{\"nodes\": [{\"type\": \"CheckpointLoaderSimple\", \"widgets_values\": [\"ernie-image-turbo.safetensors\"]}]}";
        List<ModelInfo> models = analyzer.analyze(json, "test.json");

        assertFalse(models.isEmpty());
        ModelInfo info = models.get(0);
        assertEquals("ernie-image-turbo.safetensors", info.getName());
        assertEquals("Unknown", info.getSize());
    }
}
