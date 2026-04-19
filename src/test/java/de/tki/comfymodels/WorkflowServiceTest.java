package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.WorkflowService;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class WorkflowServiceTest {

    private final WorkflowService workflowService = new WorkflowService();

    @Test
    public void testExtractFromPng() throws IOException {
        File pngFile = new File("flux_canny_model_example.png");
        if (pngFile.exists()) {
            String workflow = workflowService.extractWorkflow(pngFile);
            assertNotNull(workflow);
            assertTrue(workflow.contains("{"));
        } else {
            System.out.println("Skipping test: flux_canny_model_example.png not found");
        }
    }
}
