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
        
        // Workflow mit "None" und "default" Werten in Widgets
        String json = "{" +
            "\"nodes\": [" +
            "  {\"id\": 1, \"type\": \"CheckpointLoaderSimple\", \"widgets_values\": [\"None\", \"default\"]}," +
            "  {\"id\": 2, \"type\": \"LoraLoader\", \"widgets_values\": [\"undefined\", \"test.safetensors\"]}" +
            "]" +
            "}";
        
        List<ModelInfo> models = analyzer.analyze(json, "test.json");
        
        // Es sollte nur "test.safetensors" gefunden werden
        assertEquals(1, models.size(), "Should only find one real model");
        assertEquals("test.safetensors", models.get(0).getName());
    }

    @Test
    public void testMalformedJsonHandling() {
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        
        // Kaputtes JSON
        String json = "{ \"nodes\": [ { \"id\": 1, \"type\": \"VAELoader\", \"widgets_values\": [\"ae.safetensors\""; // Fehlende Klammern
        
        List<ModelInfo> models = analyzer.analyze(json, "broken.json");
        
        // Sollte nicht abstürzen, sondern einfach 0 oder gefundene Modelle (je nach Parser-Robustheit) zurückgeben
        assertNotNull(models);
    }
}
