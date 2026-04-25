package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ComfyModelAnalyzer;
import de.tki.comfymodels.service.impl.LocalAIService;
import de.tki.comfymodels.service.impl.ModelListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class HiDreamComprehensiveTest {

    private ComfyModelAnalyzer analyzer;

    @BeforeEach
    public void setup() {
        analyzer = new ComfyModelAnalyzer();
        LocalAIService aiService = new LocalAIService();
        aiService.init(); // Initialize the AI service
        
        ReflectionTestUtils.setField(analyzer, "aiService", aiService);
        ReflectionTestUtils.setField(analyzer, "modelListService", new ModelListService());
    }

    @Test
    public void testHiDreamWorkflowExtraction() {
        String json = "{\n" +
                "  \"id\": \"01d66ae9-78be-4a8d-b737-24eee5e1d447\",\n" +
                "  \"nodes\": [\n" +
                "    {\n" +
                "      \"id\": 55,\n" +
                "      \"type\": \"VAELoader\",\n" +
                "      \"properties\": {\n" +
                "        \"models\": [\n" +
                "          {\n" +
                "            \"name\": \"ae.safetensors\",\n" +
                "            \"url\": \"https://huggingface.co/Comfy-Org/HiDream-I1_ComfyUI/resolve/main/split_files/vae/ae.safetensors\",\n" +
                "            \"directory\": \"vae\"\n" +
                "          }\n" +
                "        ]\n" +
                "      },\n" +
                "      \"widgets_values\": [ \"ae.safetensors\" ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": 54,\n" +
                "      \"type\": \"QuadrupleCLIPLoader\",\n" +
                "      \"properties\": {\n" +
                "        \"models\": [\n" +
                "          { \"name\": \"clip_l_hidream.safetensors\", \"url\": \"https://huggingface.co/Comfy-Org/HiDream-I1_ComfyUI/resolve/main/split_files/text_encoders/clip_l_hidream.safetensors\", \"directory\": \"text_encoders\" }\n" +
                "        ]\n" +
                "      },\n" +
                "      \"widgets_values\": [ \"clip_l_hidream.safetensors\" ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": 72,\n" +
                "      \"type\": \"MarkdownNote\",\n" +
                "      \"widgets_values\": [\n" +
                "        \"* hidream_i1_full_fp16.safetensors\"\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        List<ModelInfo> results = analyzer.analyze(json, "hidream_test.json");

        assertFalse(results.isEmpty());
        
        // Check for extraction from properties.models
        assertTrue(results.stream().anyMatch(m -> m.getName().equals("ae.safetensors")));
        
        // Check for extraction from text in MarkdownNote - SHOULD BE IGNORED NOW
        assertTrue(results.stream().noneMatch(m -> m.getName().equals("hidream_i1_full_fp16.safetensors")), "Model from MarkdownNote should be ignored");

        for (ModelInfo info : results) {
            System.out.println("Extracted: " + info.getName() + " | Type: " + info.getType() + " | URL: " + info.getUrl());
        }
    }
}
