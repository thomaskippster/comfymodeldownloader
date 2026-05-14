package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.ComfyLifecycleService;
import de.tki.comfymodels.service.impl.ComfyDiagnosticService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import de.tki.comfymodels.Main.AppConfig;

public class FinalDampfhammerTest {

    @Test
    public void executeRestartAndVerify() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        ComfyLifecycleService lifecycleService = context.getBean(ComfyLifecycleService.class);
        ComfyDiagnosticService diagnosticService = context.getBean(ComfyDiagnosticService.class);
        
        System.out.println("--- 🔨 EXECUTING FINAL DAMPFHAMMER RESTART ---");

        // 1. Force Kill
        System.out.println("⏹ Terminating existing Python processes...");
        Runtime.getRuntime().exec("taskkill /F /IM python.exe /T").waitFor();
        Thread.sleep(2000);

        // 2. Start via Service
        System.out.println("▶ Starting ComfyUI...");
        lifecycleService.start();

        // 3. Robust Health Check (Wait up to 90 seconds)
        System.out.println("⏳ Waiting for API (http://127.0.0.1:8000)...");
        boolean ready = false;
        for (int i = 0; i < 90; i++) {
            if (lifecycleService.isHealthy()) {
                ready = true;
                break;
            }
            if (i % 10 == 0) System.out.println("Still waiting... (" + i + "s)");
            Thread.sleep(1000);
        }

        if (ready) {
            System.out.println("✅ ComfyUI is ONLINE and HEALTHY.");
            
            // 4. Final verification of the specific model
            String model = "ltx2.3-transition.safetensors";
            if (diagnosticService.isModelAvailableInComfy(model, "loras")) {
                System.out.println("🎯 TARGET ACHIEVED: '" + model + "' is recognized by ComfyUI!");
            } else {
                System.out.println("❌ Model still missing. This implies the file is not in 'models/loras' or extra_model_paths is misconfigured.");
            }
        } else {
            System.out.println("❌ ComfyUI failed to respond. Please check your console/logs.");
        }

        context.close();
    }
}
