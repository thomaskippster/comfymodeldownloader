package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import java.util.List;
import java.util.Optional;

/**
 * Service for checking and performing model updates.
 */
public interface IModelUpdateService {
    /**
     * Checks if a newer version of the given model exists on CivitAI.
     */
    Optional<ModelInfo> checkForUpdate(ModelInfo current);

    /**
     * Batch check for updates for a list of models.
     */
    void checkAllForUpdates(List<ModelInfo> currentModels, java.util.function.BiConsumer<Integer, ModelInfo> onUpdateFound, Runnable onFinished);
}
