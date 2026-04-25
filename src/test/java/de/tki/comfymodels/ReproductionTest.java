package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ComfyModelAnalyzer;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ReproductionTest {

    private final ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();

    @Test
    public void testDeduplicationAndUrlFiltering() {
        String json = "{\n" +
                "  \"nodes\": [\n" +
                "    {\n" +
                "      \"type\": \"MarkdownNote\",\n" +
                "      \"widgets_values\": [\n" +
                "        \"- [qwen_3_4b.safetensors](https://huggingface.co/Comfy-Org/z_image_turbo/resolve/main/split_files/text_encoders/qwen_3_4b.safetensors)\"\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"type\": \"CLIPLoader\",\n" +
                "      \"properties\": {\n" +
                "        \"models\": [\n" +
                "          {\n" +
                "            \"name\": \"qwen_3_4b.safetensors\",\n" +
                "            \"url\": \"https://huggingface.co/Comfy-Org/z_image_turbo/resolve/main/split_files/text_encoders/qwen_3_4b.safetensors\",\n" +
                "            \"directory\": \"text_encoders\"\n" +
                "          }\n" +
                "        ]\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        List<ModelInfo> models = analyzer.analyze(json, "test.json");

        // Expectation: Only ONE entry for qwen_3_4b.safetensors
        // The name must NOT be the URL.
        long count = models.stream()
                .filter(m -> m.getName().toLowerCase().contains("qwen"))
                .count();

        for (ModelInfo m : models) {
            System.out.println("Found: " + m.getName() + " [" + m.getType() + "] URL: " + m.getUrl());
        }

        assertEquals(1, count, "Should only find one entry for qwen, not a second one with the URL as name");
        assertEquals("qwen_3_4b.safetensors", models.get(0).getName());
        assertEquals("text_encoders", models.get(0).getType());
    }
}
