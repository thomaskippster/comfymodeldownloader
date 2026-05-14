package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ComfyDiagnosticService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import de.tki.comfymodels.Main.AppConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ModelPresenceVerificationTest {

    @Test
    public void verifyModelPresence() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        ComfyDiagnosticService diagnosticService = context.getBean(ComfyDiagnosticService.class);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        
        String modelName = "ltx2.3-transition.safetensors";
        String type = "loras";
        
        System.out.println("--- Starting Automated Refresh Loop for: " + modelName + " ---");
        
        boolean found = false;
        for (int i = 1; i <= 5; i++) {
            System.out.println("Attempt " + i + ": Triggering Refresh...");
            
            // Trigger Refresh
            try {
                HttpRequest refreshReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:8000/cmfd/refresh-models"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
                client.send(refreshReq, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                System.out.println("Refresh ping failed (Bridge maybe not installed?): " + e.getMessage());
            }

            // Wait for processing
            Thread.sleep(3000);
            
            // Check Presence
            if (diagnosticService.isModelAvailableInComfy(modelName, type)) {
                System.out.println("✅ SUCCESS: Model '" + modelName + "' is now visible in ComfyUI!");
                found = true;
                break;
            } else {
                System.out.println("❌ Model not found yet.");
            }
        }
        
        if (!found) {
            System.out.println("⚠️ FAILURE: Model still not visible after 5 attempts.");
        }
        
        context.close();
    }
}
