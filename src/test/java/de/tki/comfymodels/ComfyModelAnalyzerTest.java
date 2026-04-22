package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ComfyModelAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ComfyModelAnalyzerTest {

    private final ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();

    @Test
    public void testAnalyzeBasicWorkflow() {
        String json = "{\"nodes\": [{\"type\": \"CheckpointLoaderSimple\", \"widgets_values\": [\"flux1-schnell.sft\"]}]}";
        List<ModelInfo> models = analyzer.analyze(json, "test.json");
        
        assertNotNull(models);
        assertFalse(models.isEmpty());
        
        ModelInfo flux = models.stream()
                .filter(m -> m.getName().equals("flux1-schnell.sft"))
                .findFirst()
                .orElse(null);
        
        assertNotNull(flux);
        assertEquals("unet", flux.getType());
        assertEquals("https://huggingface.co/black-forest-labs/FLUX.1-schnell/resolve/main/flux1-schnell.safetensors", flux.getUrl());
    }

    @Test
    public void testAnalyzeLora() {
        String json = "{\"nodes\": [{\"type\": \"LoraLoader\", \"widgets_values\": [\"my_cool_lora.safetensors\"]}]}";
        List<ModelInfo> models = analyzer.analyze(json, "test.json");
        
        ModelInfo lora = models.stream()
                .filter(m -> m.getName().equals("my_cool_lora.safetensors"))
                .findFirst()
                .orElse(null);
        
        assertNotNull(lora);
        assertEquals("loras", lora.getType());
    }
}
