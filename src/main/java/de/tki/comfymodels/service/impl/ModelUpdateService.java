package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IModelUpdateService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

@Service
public class ModelUpdateService implements IModelUpdateService {

    private final ConfigService configService;
    private final ModelHashRegistry hashRegistry;
    private final HttpClient httpClient;
    private final ExecutorService updateExecutor = Executors.newFixedThreadPool(4);

    @Autowired
    public ModelUpdateService(ConfigService configService, ModelHashRegistry hashRegistry) {
        this.configService = configService;
        this.hashRegistry = hashRegistry;
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    }

    protected String getCivitaiApiBaseUrl() {
        return "https://civitai.com/api/v1";
    }

    @Override
    public Optional<ModelInfo> checkForUpdate(ModelInfo current) {
        String modelsPath = configService.getModelsPath();
        String subPath = (current.getSave_path() != null ? current.getSave_path() : (current.getType() != null ? current.getType() : "checkpoints"));
        File localFile = new File(modelsPath, subPath + File.separator + current.getName());

        if (!localFile.exists()) {
            return Optional.empty();
        }

        String hash = hashRegistry.getOrCalculateHash(localFile);
        if (hash == null) return Optional.empty();

        try {
            String hashUrl = getCivitaiApiBaseUrl() + "/model-versions/by-hash/" + hash;
            HttpResponse<String> hashRes = httpClient.send(HttpRequest.newBuilder().uri(URI.create(hashUrl)).build(), HttpResponse.BodyHandlers.ofString());
            if (hashRes.statusCode() != 200) return Optional.empty();

            JSONObject currentVersion = new JSONObject(hashRes.body());
            int modelId = currentVersion.optInt("modelId", -1);
            int currentVersionId = currentVersion.optInt("id", -1);
            if (modelId == -1 || currentVersionId == -1) return Optional.empty();

            String modelUrl = getCivitaiApiBaseUrl() + "/models/" + modelId;
            HttpResponse<String> modelRes = httpClient.send(HttpRequest.newBuilder().uri(URI.create(modelUrl)).build(), HttpResponse.BodyHandlers.ofString());
            if (modelRes.statusCode() != 200) return Optional.empty();

            JSONObject modelData = new JSONObject(modelRes.body());
            JSONArray versions = modelData.optJSONArray("modelVersions");
            if (versions == null || versions.length() == 0) return Optional.empty();

            // CivitAI usually returns versions in descending order of creation (newest first)
            JSONObject latestVersion = versions.getJSONObject(0);
            int latestVersionId = latestVersion.optInt("id", -1);

            if (latestVersionId != -1 && latestVersionId != currentVersionId) {
                JSONArray files = latestVersion.optJSONArray("files");
                if (files != null) {
                    for (int i = 0; i < files.length(); i++) {
                        JSONObject file = files.getJSONObject(i);
                        // We prefer primary files, or the first one if only one exists
                        if (file.optBoolean("primary", false) || files.length() == 1) {
                            ModelInfo updated = new ModelInfo();
                            updated.setType(current.getType());
                            updated.setSave_path(current.getSave_path());
                            updated.setName(file.getString("name"));
                            updated.setUrl(file.getString("downloadUrl"));
                            updated.setPopularity("⭐ UPDATE: " + latestVersion.optString("name", "New Version"));
                            
                            // Check if the updated file has a size
                            if (file.has("sizeKB")) {
                                long kb = file.getLong("sizeKB");
                                updated.setByteSize(kb * 1024);
                                updated.setSize(String.format("%.2f GB", kb / 1024.0 / 1024.0));
                            }
                            
                            return Optional.of(updated);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking for update for " + current.getName() + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void checkAllForUpdates(List<ModelInfo> currentModels, BiConsumer<Integer, ModelInfo> onUpdateFound, Runnable onFinished) {
        if (currentModels == null || currentModels.isEmpty()) {
            if (onFinished != null) onFinished.run();
            return;
        }

        AtomicInteger remaining = new AtomicInteger(currentModels.size());
        for (int i = 0; i < currentModels.size(); i++) {
            final int index = i;
            ModelInfo info = currentModels.get(i);
            updateExecutor.submit(() -> {
                try {
                    checkForUpdate(info).ifPresent(update -> onUpdateFound.accept(index, update));
                } catch (Exception ignored) {
                } finally {
                    if (remaining.decrementAndGet() == 0 && onFinished != null) {
                        onFinished.run();
                    }
                }
            });
        }
    }
}
