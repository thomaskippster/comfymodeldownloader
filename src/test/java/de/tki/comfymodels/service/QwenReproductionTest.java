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
        // Minimaler Workflow mit der MarkdownNote des Nutzers
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
        
        // Wir prüfen, ob beide Namen gefunden werden
        boolean foundLightning = models.stream().anyMatch(m -> m.getName().equals("Qwen-Image-Lightning-4steps-V1.0.safetensors"));
        boolean foundEdit = models.stream().anyMatch(m -> m.getName().equals("Qwen-Image-Edit-2509-Lightning-4steps-V1.0-bf16.safetensors"));

        assertTrue(foundEdit, "Modell aus dem Link sollte gefunden werden");
        assertTrue(foundLightning, "Modell aus dem Text-Diagramm sollte gefunden werden");
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
        
        ModelInfo editModel = models.stream()
                .filter(m -> m.getName().equals("Qwen-Image-Edit-2509-Lightning-4steps-V1.0-bf16.safetensors"))
                .findFirst().orElse(null);

        assertNotNull(editModel, "Modell sollte gefunden werden");
        assertEquals("https://huggingface.co/lightx2v/Qwen-Image-Lightning/resolve/main/Qwen-Image-Edit-2509/Qwen-Image-Edit-2509-Lightning-4steps-V1.0-bf16.safetensors", 
                     editModel.getUrl(), "URL sollte aus dem Markdown-Link extrahiert werden");
    }
}
