package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import java.util.function.BiConsumer;
import java.util.List;

public interface IModelSearchService {
    /**
     * Searches for models online.
     * @param modelsToDownload List of models to check.
     * @param selectedIndices Boolean array indicating which models are selected for search.
     * @param workflowContext The context of the workflow (JSON string).
     * @param fileName The name of the file being processed.
     * @param onStatusUpdate Callback for status updates (index, status).
     * @param onModelFound Callback when a model URL is found (index, modelInfo).
     */
    void searchOnline(List<ModelInfo> modelsToDownload, boolean[] selectedIndices, String workflowContext, String fileName, 
                             BiConsumer<Integer, String> onStatusUpdate, 
                             BiConsumer<Integer, ModelInfo> onModelFound);

    long getRemoteSize(String url);
    String formatSize(long bytes);
}
