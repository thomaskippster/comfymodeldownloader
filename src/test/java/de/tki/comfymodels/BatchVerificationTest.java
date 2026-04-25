package de.tki.comfymodels;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ComfyModelAnalyzer;
import de.tki.comfymodels.service.impl.WorkflowService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Ported from BatchVerification.java main method.
 * This test is disabled by default as it requires local files.
 */
public class BatchVerificationTest {

    @Test
    @Disabled("Requires local ComfyUI workflow files")
    public void testBatchVerification() throws Exception {
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        WorkflowService workflowService = new WorkflowService();
        
        // Path can be adjusted as needed
        Path root = Paths.get("C:/pinokio/api/comfy.git/workflows/ComfyUI_examples");
        
        if (!Files.exists(root)) {
            System.out.println("Root path does not exist, skipping batch verification: " + root);
            return;
        }

        System.out.println("=== BATCH VERIFICATION START ===");
        
        Files.walk(root)
            .filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".png"))
            .limit(30) 
            .forEach(path -> {
                try {
                    System.out.println("\nFILE: " + path.getFileName());
                    String content = workflowService.extractWorkflow(path.toFile());

                    if (content == null) {
                        System.out.println("  [!] No workflow found");
                        return;
                    }

                    List<ModelInfo> results = analyzer.analyze(content, path.getFileName().toString());
                    if (results.isEmpty()) {
                        System.out.println("  [?] No models detected");
                    } else {
                        for (ModelInfo info : results) {
                            System.out.printf("  [%s] %s | URL: %s | Source: %s\n", 
                                info.getType(), info.getName(), 
                                info.getUrl().equals("MISSING") ? "Missing" : "FOUND",
                                info.getPopularity());
                        }
                    }
                } catch (Exception e) {
                    System.out.println("  [ERROR] " + e.getMessage());
                }
            });
        
        System.out.println("\n=== BATCH VERIFICATION END ===");
    }
}
