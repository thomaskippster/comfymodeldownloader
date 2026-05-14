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

public class StabilityValidationTest {

    @Test
    public void validateStability() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        ComfyDiagnosticService diagnosticService = context.getBean(ComfyDiagnosticService.class);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        
        System.out.println("--- 🛡️ STARTING STABILITY VALIDATION ---");
        
        for (int i = 1; i <= 3; i++) {
            System.out.println("Cycle " + i + ": Triggering Refresh...");
            
            HttpRequest refreshReq = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8000/cmfd/refresh-models"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"force_reload\": false}"))
                .build();
            
            HttpResponse<String> response = client.send(refreshReq, HttpResponse.BodyHandlers.ofString());
            System.out.println("📡 Server Response: " + response.statusCode());
            
            Thread.sleep(2000);
            
            // Check if API is still alive
            if (diagnosticService.isModelAvailableInComfy("ltx2.3-transition.safetensors", "loras")) {
                System.out.println("✅ Cycle " + i + ": Server is healthy and model is visible.");
            } else {
                System.out.println("❌ Cycle " + i + ": Model visibility check failed.");
            }
        }
        
        System.out.println("--- 🛡️ STABILITY VALIDATION COMPLETE ---");
        context.close();
    }
}
