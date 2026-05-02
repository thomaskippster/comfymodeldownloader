package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import java.util.List;

/**
 * Service for scanning and cleaning up unused models.
 */
public interface ICleanupService {
    /**
     * Identifies models that haven't been accessed for the given number of months.
     */
    List<ModelInfo> findUnusedModels(int monthsThreshold);

    /**
     * Archives the specified models.
     */
    void archiveModels(List<ModelInfo> models, java.util.function.BiConsumer<Integer, String> onStatusUpdate, Runnable onFinished);
}
