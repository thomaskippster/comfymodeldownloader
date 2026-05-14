package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import java.util.List;
import java.util.function.BiConsumer;

public interface IDownloadManager {
    void startQueue(List<ModelInfo> models, boolean[] selectedIndices, String baseDir, BiConsumer<Integer, String> statusUpdater, Runnable onFinished);
    void updateSelection(boolean[] selectedIndices);
    void togglePause();
    void stop();
    boolean isPaused();

    /**
     * Triggers a refresh signal to the ComfyUI bridge.
     */
    void notifyComfyUI();

    /**
     * Triggers a refresh signal and optionally forces a browser reload.
     */
    void notifyComfyUI(boolean forceReload);

    /**
     * Returns a map of current statuses for each model in the queue.
     */
    java.util.Map<Integer, String> getQueueStatus();
}
