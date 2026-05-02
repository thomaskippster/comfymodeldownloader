package de.tki.comfymodels.service;

import java.util.List;
import java.util.Set;

/**
 * Service for identifying and synchronizing missing custom nodes.
 */
public interface INodeSyncService {
    /**
     * Parses a workflow JSON and returns a set of required but missing custom nodes.
     */
    Set<String> findMissingNodes(String workflowJson);

    /**
     * Attempts to find installation instructions or repositories for missing nodes.
     */
    void resolveMissingNodes(Set<String> missingNodes, java.util.function.BiConsumer<String, String> onResolved, Runnable onFinished);
}
