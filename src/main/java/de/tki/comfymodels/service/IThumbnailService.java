package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;

/**
 * Service for downloading or generating model thumbnails.
 */
public interface IThumbnailService {
    /**
     * Downloads a preview image for the given model and saves it as .preview.png.
     */
    void downloadThumbnail(ModelInfo model, java.util.function.Consumer<Boolean> onResult);
}
