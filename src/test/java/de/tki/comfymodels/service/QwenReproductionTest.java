package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ComfyModelAnalyzer;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class QwenReproductionTest {

    @Test
    public void testQwenMarkdownModelDetection() {
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        // Minimal workflow with the user's MarkdownNote
        String workflow = "{\n" +
                "  \"nodes\": [\n" +
                "    {\n" +
                "      \"id\": 99,\n" +
                "      \"type\": \"MarkdownNote\",\n" +
                "      \"widgets_values\": [\n" +
                "        \"## Model links\\n\\n**LoRA**\\n\\n- [Qwen-Image-Edit-2509-Lightning-4steps-V1.0-bf16.safetensors](https://huggingface.co/lightx2v/Qwen-Image-Lightning/resolve/main/Qwen-Image-Edit-2509/Qwen-Image-Edit-2509-Lightning-4steps-V1.0-bf16.safetensors)\\n\\n📂 loras/\\n├── 📂 loras/\\n│   └── Qwen-Image-Lightning-4steps-V1.0.safetensors\"\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        List<ModelInfo> models = analyzer.analyze(workflow, "test.json");
        
        // We expect NO models because MarkdownNote is ignored
        assertTrue(models.isEmpty(), "No models should be found from MarkdownNote");
    }

    @Test
    public void testMarkdownLinkUrlExtraction() {
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        String workflow = "{\n" +
                "  \"nodes\": [\n" +
                "    {\n" +
                "      \"type\": \"MarkdownNote\",\n" +
                "      \"widgets_values\": [\n" +
                "        \"- [Qwen-Image-Edit-2509-Lightning-4steps-V1.0-bf16.safetensors](https://huggingface.co/lightx2v/Qwen-Image-Lightning/resolve/main/Qwen-Image-Edit-2509/Qwen-Image-Edit-2509-Lightning-4steps-V1.0-bf16.safetensors)\"\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        List<ModelInfo> models = analyzer.analyze(workflow, "test.json");
        
        assertTrue(models.isEmpty(), "No models should be found from MarkdownNote, even with links");
    }
}
