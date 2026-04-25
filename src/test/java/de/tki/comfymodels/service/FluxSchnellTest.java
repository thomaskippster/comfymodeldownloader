package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ComfyModelAnalyzer;
import de.tki.comfymodels.service.impl.LocalAIService;
import de.tki.comfymodels.service.impl.ModelListService;
import de.tki.comfymodels.service.impl.ModelSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FluxSchnellTest {

    private ComfyModelAnalyzer analyzer;
    private ModelSearchService searchService;

    @BeforeEach
    public void setup() {
        analyzer = new ComfyModelAnalyzer();
        LocalAIService aiService = new LocalAIService();
        aiService.init();
        
        ReflectionTestUtils.setField(analyzer, "aiService", aiService);
        ReflectionTestUtils.setField(analyzer, "modelListService", new ModelListService());
        
        searchService = new ModelSearchService();
    }

    @Test
    public void testFluxSchnellExtractionAndSizeValidation() {
        String json = "{\n" +
                "  \"nodes\": [\n" +
                "    {\n" +
                "      \"id\": 30,\n" +
                "      \"type\": \"CheckpointLoaderSimple\",\n" +
                "      \"properties\": {\n" +
                "        \"models\": [\n" +
                "          {\n" +
                "            \"name\": \"flux1-schnell-fp8.safetensors\",\n" +
                "            \"url\": \"https://huggingface.co/Comfy-Org/flux1-schnell/resolve/main/flux1-schnell-fp8.safetensors?download=true\",\n" +
                "            \"directory\": \"checkpoints\"\n" +
                "          }\n" +
                "        ]\n" +
                "      },\n" +
                "      \"widgets_values\": [ \"flux1-schnell-fp8.safetensors\" ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        List<ModelInfo> results = analyzer.analyze(json, "flux_schnell.json");

        assertFalse(results.isEmpty(), "Should extract at least one model");
        ModelInfo flux = results.get(0);
        
        assertEquals("flux1-schnell-fp8.safetensors", flux.getName());
        assertEquals("checkpoints", flux.getType());
        assertNotEquals("MISSING", flux.getUrl());

        // Size validation logic test
        // 1. Valid size
        assertEquals("1.00 GB", searchService.formatSize(1024L * 1024L * 1024L));
        
        // 2. Zero size check (Crucial: Should NOT return "0.00 MB")
        String zeroResult = searchService.formatSize(0);
        assertNotEquals("0.00 MB", zeroResult, "Size 0 should be handled as Unknown or Error, not 0.00 MB");
        assertEquals("Unknown", zeroResult);
        
        // 3. Negative size (Error)
        assertEquals("Unknown", searchService.formatSize(-1));
        
        // 4. Auth required
        assertEquals("🔒 Auth Required", searchService.formatSize(-401));
    }
}
