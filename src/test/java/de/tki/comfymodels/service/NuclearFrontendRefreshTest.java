package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.ComfyDiagnosticService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import de.tki.comfymodels.Main.AppConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.InputStream;

public class NuclearFrontendRefreshTest {

    @Test
    public void triggerReload() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        ConfigService configService = context.getBean(ConfigService.class);
        ComfyDiagnosticService diagnosticService = context.getBean(ComfyDiagnosticService.class);
        HttpClient client = HttpClient.newBuilder().build();
        
        System.out.println("--- ☢️ TRIGGERING NUCLEAR FRONTEND REFRESH ---");

        // 1. Sync Bridge to Disk (Ensure latest JS/Python is there)
        String comfyPath = "C:\\AI\\ComfyUI\\resources\\ComfyUI";
        Path bridgeDir = Paths.get(comfyPath, "custom_nodes", "comfyui-model-downloader");
        System.out.println("📦 Syncing Bridge to: " + bridgeDir);
        Files.createDirectories(bridgeDir.resolve("web"));
        
        copyResource("/comfyui-bridge/__init__.py", bridgeDir.resolve("__init__.py"));
        copyResource("/comfyui-bridge/web/downloader.js", bridgeDir.resolve("web/downloader.js"));
        Files.writeString(bridgeDir.resolve("web/config.json"), "{\"token\": \"" + configService.getApiToken() + "\"}");

        // 2. Send the "Nuclear" REST Call
        System.out.println("🚀 Sending REST signal with 'force_reload: true'...");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8000/cmfd/refresh-models"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"force_reload\": true}"))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("📡 Server Response: " + response.statusCode() + " " + response.body());
        } catch (Exception e) {
            System.out.println("❌ Failed to reach ComfyUI: " + e.getMessage());
        }

        // 3. Verify Backend
        String model = "ltx2.3-transition.safetensors";
        if (diagnosticService.isModelAvailableInComfy(model, "loras")) {
            System.out.println("✅ BACKEND VERIFIED: '" + model + "' is present in the API.");
        } else {
            System.out.println("❌ BACKEND FAILED: Model still not in API.");
        }

        context.close();
    }

    private void copyResource(String name, Path target) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(name)) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
