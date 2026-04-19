package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import java.util.List;
import java.util.function.BiConsumer;

public interface IDownloadManager {
    void startQueue(List<ModelInfo> models, String baseDir, BiConsumer<Integer, String> statusUpdater, Runnable onFinished);
    void togglePause();
    void stop();
    boolean isPaused();
}
