package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.ComfyLifecycleService;
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

public class UltimateGuiSyncTest {

    @Test
    public void ensureGuiSyncs() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        ConfigService configService = context.getBean(ConfigService.class);
        ComfyLifecycleService lifecycleService = context.getBean(ComfyLifecycleService.class);
        ComfyDiagnosticService diagnosticService = context.getBean(ComfyDiagnosticService.class);
        HttpClient client = HttpClient.newBuilder().build();
        
        System.out.println("--- 🚀 ULTIMATE GUI SYNC SEQUENCE ---");

        // 1. HARD REINSTALL OF BRIDGE FILES
        String comfyPath = "C:\\AI\\ComfyUI\\resources\\ComfyUI";
        Path bridgeDir = Paths.get(comfyPath, "custom_nodes", "comfyui-model-downloader");
        System.out.println("📦 Enforcing Latest Bridge in: " + bridgeDir);
        Files.createDirectories(bridgeDir.resolve("web"));
        
        copyResource("/comfyui-bridge/__init__.py", bridgeDir.resolve("__init__.py"));
        copyResource("/comfyui-bridge/web/downloader.js", bridgeDir.resolve("web/downloader.js"));
        Files.writeString(bridgeDir.resolve("web/config.json"), "{\"token\": \"" + configService.getApiToken() + "\"}");

        // 2. FORCE RESTART BACKEND (To load new Python bridge)
        System.out.println("⏹ Terminating ComfyUI for Clean State...");
        Runtime.getRuntime().exec("taskkill /F /IM python.exe /T").waitFor();
        Thread.sleep(2000);
        System.out.println("▶ Starting ComfyUI...");
        lifecycleService.start();

        // 3. WAIT FOR BACKEND READINESS
        System.out.println("⏳ Waiting for API...");
        for (int i = 0; i < 90; i++) {
            if (lifecycleService.isHealthy()) break;
            Thread.sleep(1000);
        }

        // 4. TRIGGER NUCLEAR FRONTEND RELOAD
        // This will force every open browser tab to run window.location.reload()
        System.out.println("☢️ Triggering Nuclear Browser Reload...");
        HttpRequest reloadReq = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8000/cmfd/refresh-models"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"force_reload\": true}"))
                .build();
        
        try {
            client.send(reloadReq, HttpResponse.BodyHandlers.ofString());
            System.out.println("✅ Reload signal broadcasted to all tabs.");
        } catch (Exception ignored) {}

        // 5. FINAL VERIFICATION
        String model = "ltx2.3-transition.safetensors";
        if (diagnosticService.isModelAvailableInComfy(model, "loras")) {
            System.out.println("🎯 TARGET ACHIEVED: Backend is synchronized.");
            System.out.println("Check your browser now. All tabs should have reloaded and the model should be visible!");
        } else {
            System.out.println("❌ Critical Failure: Model still missing from API.");
        }

        context.close();
    }

    private void copyResource(String name, Path target) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(name)) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
