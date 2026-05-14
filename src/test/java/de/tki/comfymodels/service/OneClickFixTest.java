package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.ComfyLifecycleService;
import de.tki.comfymodels.service.impl.ComfyDiagnosticService;
import de.tki.comfymodels.Main;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import de.tki.comfymodels.Main.AppConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class OneClickFixTest {

    @Test
    public void performNuclearFix() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        ConfigService configService = context.getBean(ConfigService.class);
        ComfyLifecycleService lifecycleService = context.getBean(ComfyLifecycleService.class);
        ComfyDiagnosticService diagnosticService = context.getBean(ComfyDiagnosticService.class);
        
        System.out.println("--- ☢️ NUCLEAR ONE-CLICK FIX STARTING ---");

        // 1. Reinstall Bridge to the correct location
        String comfyPath = "C:\\AI\\ComfyUI\\resources\\ComfyUI";
        Path bridgeDir = Paths.get(comfyPath, "custom_nodes", "comfyui-model-downloader");
        System.out.println("📦 Reinstalling Bridge to: " + bridgeDir);
        Files.createDirectories(bridgeDir);
        Files.createDirectories(bridgeDir.resolve("web"));
        
        copyResource("/comfyui-bridge/__init__.py", bridgeDir.resolve("__init__.py"));
        copyResource("/comfyui-bridge/web/downloader.js", bridgeDir.resolve("web/downloader.js"));
        
        String configJson = "{\"token\": \"" + configService.getApiToken() + "\"}";
        Files.writeString(bridgeDir.resolve("web/config.json"), configJson);
        System.out.println("✅ Bridge reinstalled.");

        // 2. Kill existing process
        System.out.println("⏹ Stopping ComfyUI...");
        lifecycleService.stop(); 
        // Fallback kill for PID 14544 just in case
        Runtime.getRuntime().exec("taskkill /F /PID 14544").waitFor();
        System.out.println("✅ ComfyUI stopped.");

        // 3. Start via Lifecycle Service
        System.out.println("▶ Starting ComfyUI...");
        lifecycleService.start();
        
        // 4. Wait for Health
        System.out.println("⏳ Waiting for API to become healthy...");
        boolean healthy = false;
        for (int i = 0; i < 60; i++) {
            if (lifecycleService.isHealthy()) {
                healthy = true;
                break;
            }
            Thread.sleep(1000);
        }
        
        if (healthy) {
            System.out.println("✅ ComfyUI is back online!");
            
            // 5. Final Diagnostic
            String model = "ltx2.3-transition.safetensors";
            if (diagnosticService.isModelAvailableInComfy(model, "loras")) {
                System.out.println("🎯 TARGET VERIFIED: '" + model + "' is now fully recognized!");
            } else {
                System.out.println("❌ Diagnostic failed even after restart. Please check if the file is in 'models/loras'.");
            }
        } else {
            System.out.println("❌ ComfyUI failed to start within 60 seconds.");
        }

        context.close();
    }

    private void copyResource(String name, Path target) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(name)) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
