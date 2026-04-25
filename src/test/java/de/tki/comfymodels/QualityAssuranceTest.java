package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ComfyModelAnalyzer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class QualityAssuranceTest {

    @Test
    public void testFalsePositiveFiltering() {
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        
        // Workflow with "None" and "default" values in widgets
        String json = "{" +
            "\"nodes\": [" +
            "  {\"id\": 1, \"type\": \"CheckpointLoaderSimple\", \"widgets_values\": [\"None\", \"default\"]}," +
            "  {\"id\": 2, \"type\": \"LoraLoader\", \"widgets_values\": [\"undefined\", \"test.safetensors\"]}" +
            "]" +
            "}";
        
        List<ModelInfo> models = analyzer.analyze(json, "test.json");
        
        // Only "test.safetensors" should be found
        assertEquals(1, models.size(), "Should only find one real model");
        assertEquals("test.safetensors", models.get(0).getName());
    }

    @Test
    public void testMalformedJsonHandling() {
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        
        // Broken JSON
        String json = "{ \"nodes\": [ { \"id\": 1, \"type\": \"VAELoader\", \"widgets_values\": [\"ae.safetensors\""; // Missing brackets
        
        List<ModelInfo> models = analyzer.analyze(json, "broken.json");
        
        // Should not crash, but simply return 0 or found models (depending on parser robustness)
        assertNotNull(models);
    }
}
