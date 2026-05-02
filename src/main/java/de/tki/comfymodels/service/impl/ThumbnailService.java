package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IThumbnailService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

@Service
public class ThumbnailService implements IThumbnailService {

    private final ConfigService configService;
    private final ModelHashRegistry hashRegistry;
    private final HttpClient httpClient;

    @Autowired
    public ThumbnailService(ConfigService configService, ModelHashRegistry hashRegistry) {
        this.configService = configService;
        this.hashRegistry = hashRegistry;
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    }

    protected String getCivitaiApiBaseUrl() {
        return "https://civitai.com/api/v1";
    }

    @Override
    public void downloadThumbnail(ModelInfo model, Consumer<Boolean> onResult) {
        new Thread(() -> {
            boolean success = performDownload(model);
            if (onResult != null) onResult.accept(success);
        }).start();
    }

    private boolean performDownload(ModelInfo model) {
        try {
            String modelsPath = configService.getModelsPath();
            String subPath = (model.getSave_path() != null ? model.getSave_path() : (model.getType() != null ? model.getType() : "checkpoints"));
            File localFile = new File(modelsPath, subPath + File.separator + model.getName());

            if (!localFile.exists()) return false;

            String hash = hashRegistry.getOrCalculateHash(localFile);
            if (hash == null) return false;

            String hashUrl = getCivitaiApiBaseUrl() + "/model-versions/by-hash/" + hash;
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder().uri(URI.create(hashUrl)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;

            JSONObject version = new JSONObject(response.body());
            JSONArray images = version.optJSONArray("images");
            if (images == null || images.length() == 0) return false;

            // Get first image URL
            String imageUrl = images.getJSONObject(0).optString("url");
            if (imageUrl == null || imageUrl.isEmpty()) return false;

            // Target path: modelname.preview.png
            String baseName = model.getName();
            if (baseName.contains(".")) baseName = baseName.substring(0, baseName.lastIndexOf("."));
            Path previewPath = localFile.getParentFile().toPath().resolve(baseName + ".preview.png");

            // Download image
            try (InputStream in = httpClient.send(HttpRequest.newBuilder().uri(URI.create(imageUrl)).build(), HttpResponse.BodyHandlers.ofInputStream()).body()) {
                Files.copy(in, previewPath, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }

        } catch (Exception e) {
            System.err.println("Error downloading thumbnail for " + model.getName() + ": " + e.getMessage());
        }
        return false;
    }
}
