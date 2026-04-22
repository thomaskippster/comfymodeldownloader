package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ComfyModelAnalyzer;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ModelTypePathTest {

    @Test
    public void testModelTypeMapping() {
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        
        // Mock JSON representing different nodes
        String json = "{" +
                "\"1\": {\"class_type\": \"LoraLoader\", \"widgets_values\": [\"my_lora.safetensors\"]}," +
                "\"2\": {\"class_type\": \"ControlNetApply\", \"widgets_values\": [\"cn_model.pth\"]}," +
                "\"3\": {\"class_type\": \"CLIPLoader\", \"widgets_values\": [\"t5xxl.safetensors\"]}," +
                "\"4\": {\"class_type\": \"VAELoader\", \"widgets_values\": [\"vae_model.safetensors\"]}," +
                "\"5\": {\"class_type\": \"UNETLoader\", \"widgets_values\": [\"diffusion_model.safetensors\"]}," +
                "\"6\": {\"class_type\": \"GLIGENLoader\", \"widgets_values\": [\"gligen_box.safetensors\"]}," +
                "\"7\": {\"class_type\": \"CLIPVisionLoader\", \"widgets_values\": [\"clip_vision.safetensors\"]}," +
                "\"8\": {\"class_type\": \"AudioEncoderLoader\", \"widgets_values\": [\"audio.safetensors\"]}" +
                "}";

        List<ModelInfo> results = analyzer.analyze(json, "test_workflow.json");
        results.forEach(r -> System.out.println("Found: " + r.getName() + " -> " + r.getType()));

        assertPath(results, "my_lora.safetensors", "loras");
        assertPath(results, "cn_model.pth", "controlnet");
        assertPath(results, "t5xxl.safetensors", "clip");
        assertPath(results, "vae_model.safetensors", "vae");
        assertPath(results, "diffusion_model.safetensors", "unet");
        assertPath(results, "gligen_box.safetensors", "gligen");
        assertPath(results, "clip_vision.safetensors", "clip_vision");
        assertPath(results, "audio.safetensors", "audio_encoders");
    }

    private void assertPath(List<ModelInfo> results, String name, String expectedType) {
        for (ModelInfo info : results) {
            if (info.getName().equals(name)) {
                assertEquals(expectedType, info.getType(), "Type mismatch for " + name);
                return;
            }
        }
        throw new AssertionError("Model " + name + " not found in results");
    }
}
