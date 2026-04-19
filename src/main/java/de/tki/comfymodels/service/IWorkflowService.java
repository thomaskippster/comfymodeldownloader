package de.tki.comfymodels.service;

import java.io.File;
import java.io.IOException;

public interface IWorkflowService {
    /**
     * Extracts the workflow JSON string from a file (JSON or PNG).
     */
    String extractWorkflow(File file) throws IOException;
}
