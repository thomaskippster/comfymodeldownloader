package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.ComfyLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify the full ComfyUI server lifecycle:
 * Auto-discovery -> Start -> Reachability Check -> Stop -> Reachability Check.
 */
public class ComfyLifecycleIntegrationTest {

    @Test
    public void testFullServerLifecycle() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class);
        ConfigService config = context.getBean(ConfigService.class);
        IComfyLifecycleService lifecycle = context.getBean(IComfyLifecycleService.class);

        System.out.println("\n--- [START] Lifecycle Integration Test ---");

        // 1. Discovery Phase
        System.out.println("Step 1: Running path discovery...");
        // Simulate real environment conditions
        config.autoDiscoverPaths();
        
        String root = config.getComfyUIPath();
        String command = config.getComfyLaunchCommand();
        String models = config.getModelsPath();
        
        System.out.println("Detected Root: " + root);
        System.out.println("Detected Command: " + command);
        System.out.println("Detected Models: " + models);
        
        assertNotNull(root, "ComfyUI Root should not be null");
        assertFalse(root.isEmpty(), "ComfyUI Root should not be empty");
        assertTrue(new File(root, "main.py").exists(), "main.py MUST exist in discovered root: " + root);
        assertNotNull(command, "Launch command should not be null");
        assertFalse(command.isEmpty(), "Launch command should not be empty");
        assertNotNull(models, "Models path should not be null");
        assertTrue(new File(models).exists(), "Models path MUST exist: " + models);

        // 2. Startup Phase
        System.out.println("Step 2: Starting server...");
        lifecycle.start();
        
        // Wait for server to become healthy (API response)
        boolean started = false;
        for (int i = 0; i < 30; i++) {
            System.out.println("Waiting for health (Attempt " + (i + 1) + "/30)... Status: " + lifecycle.getStatus());
            if (lifecycle.isHealthy()) {
                started = true;
                break;
            }
            Thread.sleep(2000); // 2s steps for ComfyUI
        }
        
        assertTrue(started, "Server should be healthy after start. Current Status: " + lifecycle.getStatus());
        assertTrue(lifecycle.isRunning(), "Server should be running according to Lifecycle Service");

        // 3. Stop Phase
        System.out.println("Step 3: Stopping server...");
        lifecycle.stop();
        
        // Wait a bit for port release
        Thread.sleep(5000);
        
        assertFalse(lifecycle.isHealthy(), "Server should NO LONGER be healthy after stop");
        assertFalse(lifecycle.isRunning(), "Server should NO LONGER be running after stop");
        
        System.out.println("--- [SUCCESS] Lifecycle Integration Test PASSED ---\n");
        context.close();
    }

    @Configuration
    @ComponentScan("de.tki.comfymodels")
    public static class TestConfig {
        // Standard scan picks up all components
    }
}
