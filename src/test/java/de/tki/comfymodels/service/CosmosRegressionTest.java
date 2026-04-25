package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ComfyModelAnalyzer;
import de.tki.comfymodels.service.impl.LocalAIService;
import de.tki.comfymodels.service.impl.ModelListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CosmosRegressionTest {

    private ComfyModelAnalyzer analyzer;

    @BeforeEach
    public void setup() {
        analyzer = new ComfyModelAnalyzer();
        LocalAIService aiService = new LocalAIService();
        aiService.init();
        
        ReflectionTestUtils.setField(analyzer, "aiService", aiService);
        ReflectionTestUtils.setField(analyzer, "modelListService", new ModelListService());
    }

    @Test
    public void testFullCosmosWorkflow() {
        String json = "{\n" +
                "  \"last_node_id\": 84,\n" +
                "  \"last_link_id\": 198,\n" +
                "  \"nodes\": [\n" +
                "    {\n" +
                "      \"id\": 38,\n" +
                "      \"type\": \"CLIPLoader\",\n" +
                "      \"widgets_values\": [\n" +
                "        \"oldt5_xxl_fp8_e4m3fn_scaled.safetensors\",\n" +
                "        \"cosmos\",\n" +
                "        \"default\"\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": 39,\n" +
                "      \"type\": \"VAELoader\",\n" +
                "      \"widgets_values\": [\n" +
                "        \"cosmos_cv8x8x8_1.0.safetensors\"\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": 37,\n" +
                "      \"type\": \"UNETLoader\",\n" +
                "      \"widgets_values\": [\n" +
                "        \"Cosmos-1_0-Diffusion-7B-Video2World.safetensors\",\n" +
                "        \"default\"\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": 83,\n" +
                "      \"type\": \"CosmosImageToVideoLatent\",\n" +
                "      \"widgets_values\": [ 1024, 1024, 121, 1 ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        List<ModelInfo> results = analyzer.analyze(json, "full_cosmos.json");

        // Expectations
        boolean foundT5 = results.stream().anyMatch(m -> m.getName().equals("oldt5_xxl_fp8_e4m3fn_scaled.safetensors"));
        boolean foundDiffusion = results.stream().anyMatch(m -> m.getName().equals("Cosmos-1_0-Diffusion-7B-Video2World.safetensors"));
        boolean foundVae = results.stream().anyMatch(m -> m.getName().equals("cosmos_cv8x8x8_1.0.safetensors"));

        assertTrue(foundT5, "T5 XXL not found");
        assertTrue(foundDiffusion, "Cosmos Diffusion not found");
        assertTrue(foundVae, "Cosmos VAE not found");
        
        assertEquals(3, results.size());
    }
}
