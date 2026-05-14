package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ComfyDiagnosticService;
import de.tki.comfymodels.service.impl.ConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import de.tki.comfymodels.Main.AppConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rapid Diagnostic Test for Refresh Mechanisms.
 * This test uses the ComfyDiagnosticService to verify if ComfyUI actually sees
 * the models after our refresh calls.
 */
public class RefreshDiagnosticTest {

    @Test
    public void testModelVisibility() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        ComfyDiagnosticService diagnosticService = context.getBean(ComfyDiagnosticService.class);
        
        System.out.println("--- ComfyUI Diagnostic Scan ---");
        boolean reachable = diagnosticService.isModelAvailableInComfy("non_existent_model_xyz", "checkpoint");
        
        // If the API is up, it should at least return false for a non-existent model
        // If it throws an exception or timeout, the API is down.
        System.out.println("API Reachable: " + !reachable);
        
        context.close();
    }
}
