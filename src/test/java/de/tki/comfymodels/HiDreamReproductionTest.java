package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ComfyModelAnalyzer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class HiDreamReproductionTest {

    @Test
    public void testHiDreamModelDetection() {
        String json = "{" +
            "\"id\": \"01d66ae9-78be-4a8d-b737-24eee5e1d447\"," +
            "\"nodes\": [" +
            "  {\"id\": 55, \"type\": \"VAELoader\", \"widgets_values\": [\"ae.safetensors\"]}," +
            "  {\"id\": 69, \"type\": \"UNETLoader\", \"widgets_values\": [\"hidream_i1_dev_bf16.safetensors\", \"default\"]}," +
            "  {\"id\": 54, \"type\": \"QuadrupleCLIPLoader\", \"widgets_values\": [" +
            "    \"clip_l_hidream.safetensors\", \"clip_g_hidream.safetensors\", " +
            "    \"t5xxl_fp8_e4m3fn_scaled.safetensors\", \"llama_3.1_8b_instruct_fp8_scaled.safetensors\"" +
            "  ]}," +
            "  {\"id\": 72, \"type\": \"MarkdownNote\", \"widgets_values\": [\"## Official sampling settings\\n\\n### HiDream Full\\n* hidream_i1_full_fp16.safetensors\"]}" +
            "]" +
            "}";
        
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        List<ModelInfo> models = analyzer.analyze(json, "workflow.json");

        // Wir erwarten 7 Modelle: 1 UNET, 4 CLIPs, 1 VAE (aus Loadern) UND 1 UNET (aus der MarkdownNote)
        assertEquals(7, models.size(), "Should detect 7 models (including one from MarkdownNote)");

        boolean foundUnet = false;
        int clipsFound = 0;
        boolean foundVae = false;

        for (ModelInfo m : models) {
            if (m.getName().equals("hidream_i1_dev_bf16.safetensors")) {
                assertEquals("unet", m.getType());
                foundUnet = true;
            } else if (m.getName().equals("ae.safetensors")) {
                assertEquals("vae", m.getType());
                foundVae = true;
            } else if (m.getName().contains("clip") || m.getName().contains("t5xxl") || m.getName().contains("llama")) {
                assertEquals("clip", m.getType(), "Type for " + m.getName() + " should be clip");
                clipsFound++;
            }
        }

        assertTrue(foundUnet, "UNET from Loader not found");
        assertTrue(foundVae, "VAE not found");
        assertEquals(4, clipsFound, "Should find 4 CLIP/TextEncoder models");
        
        // Überprüfe ob der Kontext "hidream" erkannt wurde
        boolean contextDetected = false;
        for (ModelInfo m : models) {
            System.out.println("Model: " + m.getName() + " | Popularity: " + m.getPopularity());
            if (m.getPopularity() != null && m.getPopularity().toLowerCase().contains("hidream")) {
                contextDetected = true;
                break;
            }
        }
        assertTrue(contextDetected, "Context 'hidream' should be detected from workflow content");
    }
}
