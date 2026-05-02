package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IModelSearchService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

@Service
public class ModelSearchService implements IModelSearchService {

    @Autowired
    private ConfigService configService;

    @Autowired
    private GeminiAIService geminiService;

    @Autowired
    private ModelListService modelListService;

    private static final Set<String> OFFICIAL_AUTHORS = new HashSet<>(Arrays.asList(
            "black-forest-labs", "stabilityai", "runwayml", "comfyanonymous", "Comfy-Org",
            "lllyasviel", "Kwai-Kolors", "Kwai-VGI", "InstantX", "ByteDance", "apple",
            "google", "facebook", "TencentARC", "DeepFloyd", "microsoft", "nvidia", "fal", "genmo",
            "ostris", "XLabs-AI", "THUDM", "city96", "Kijai", "BartoszGawlik"
    ));

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private final ExecutorService searchExecutor = Executors.newFixedThreadPool(4);

    @Override
    public void searchOnline(List<ModelInfo> modelsToDownload, boolean[] selectedIndices, String workflowContext, String fileName,
                             BiConsumer<Integer, String> onStatusUpdate,
                             BiConsumer<Integer, ModelInfo> onModelFound,
                             Runnable onFinished) {
        if (modelsToDownload == null) {
            if (onFinished != null) onFinished.run();
            return;
        }

        List<Integer> targetIndices = new ArrayList<>();
        for (int i = 0; i < modelsToDownload.size(); i++) {
            if (selectedIndices != null && i < selectedIndices.length && !selectedIndices[i]) continue;
            targetIndices.add(i);
        }

        if (targetIndices.isEmpty()) {
            if (onFinished != null) onFinished.run();
            return;
        }

        AtomicInteger remaining = new AtomicInteger(targetIndices.size());

        for (int index : targetIndices) {
            ModelInfo info = modelsToDownload.get(index);
            searchExecutor.submit(() -> {
                try {
                    performSearch(info, index, fileName, workflowContext, onStatusUpdate, onModelFound);
                } catch (Exception e) {
                    onStatusUpdate.accept(index, "Error: " + e.getMessage());
                } finally {
                    if (remaining.decrementAndGet() == 0 && onFinished != null) {
                        onFinished.run();
                    }
                }
            });
        }
    }

    @Autowired
    private de.tki.comfymodels.service.impl.ModelHashRegistry hashRegistry;

    @Autowired
    private de.tki.comfymodels.service.IModelValidator modelValidator;

    protected String getHfApiBaseUrl() {
        return "https://huggingface.co/api";
    }

    protected String getCivitaiApiBaseUrl() {
        return "https://civitai.com/api/v1";
    }

    protected String getHfResolveBaseUrl() {
        return "https://huggingface.co";
    }

    private void performSearch(ModelInfo info, int index, String fileName, String workflowContext,
                               BiConsumer<Integer, String> onStatusUpdate,
                               BiConsumer<Integer, ModelInfo> onModelFound) {
        // Priority 1: User defined Model List
        Optional<ModelInfo> manualMatch = modelListService.findByFilename(info.getName());
        if (manualMatch.isPresent() && !manualMatch.get().getUrl().equals("MISSING")) {
            onStatusUpdate.accept(index, "📂 Found in Model List");
            if (validateAndSetUrl(info, index, manualMatch.get().getUrl(), "📂 USER DEFINED", onStatusUpdate, onModelFound)) return;
        }

        // Priority 2: Civitai Hash Lookup
        String modelsPath = configService.getModelsPath();
        java.io.File localFile = new java.io.File(modelsPath, (info.getSave_path() != null ? info.getSave_path() : (info.getType() != null ? info.getType() : de.tki.comfymodels.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName())) + java.io.File.separator + info.getName());
        if (localFile.exists()) {
            onStatusUpdate.accept(index, "🔍 Hashing for Civitai...");
            String hash = hashRegistry.getOrCalculateHash(localFile);
            if (hash != null) {
                if (fetchCivitaiUrlByHash(info, index, hash, onStatusUpdate, onModelFound)) return;
            }
        }

        onStatusUpdate.accept(index, "✨ Gemini Scouting...");
        String aiHint = geminiService.discoverBestRepo(info.getName(), fileName, workflowContext);
        if (aiHint != null && !aiHint.equalsIgnoreCase("UNKNOWN")) {
            if (aiHint.startsWith("http")) {
                if (validateAndSetUrl(info, index, aiHint, "✨ AI DIRECT", onStatusUpdate, onModelFound)) return;
            }
            onStatusUpdate.accept(index, "🔍 Validating Repo: " + aiHint);
            if (fetchHuggingFaceUrlInSpecificRepo(info, index, aiHint, onStatusUpdate, onModelFound)) return;
        }

        String modelName = info.getName();
        String cleanName = modelName.replaceAll("(_fp8|_fp16|_bf16|_v\\d+|\\d+v|_fix|\\.safetensors|\\.sft|\\.ckpt)", "");
        
        LinkedHashSet<String> attempts = new LinkedHashSet<>();
        attempts.add(modelName);
        attempts.add(cleanName);

        // Official HF
        for (String query : attempts) {
            if (query.length() < 3) continue;
            onStatusUpdate.accept(index, "🔍 Searching Official: " + query);
            if (fetchHuggingFaceUrl(info, index, query, true, onStatusUpdate, onModelFound)) return;
        }
        
        // Community HF
        for (String query : attempts) {
            if (query.length() < 3) continue;
            onStatusUpdate.accept(index, "🔍 Searching Community: " + query);
            if (fetchHuggingFaceUrl(info, index, query, false, onStatusUpdate, onModelFound)) return;
        }

        // Civitai Fallback
        for (String query : attempts) {
            if (query.length() < 3) continue;
            onStatusUpdate.accept(index, "🔍 Searching Civitai: " + query);
            if (fetchCivitaiUrl(info, index, query, onStatusUpdate, onModelFound)) return;
        }
        
        onStatusUpdate.accept(index, "❌ No trusted match found");
    }

    @Override
    public Optional<ModelInfo> checkForUpdate(ModelInfo current) {
        String modelsPath = configService.getModelsPath();
        String subPath = (current.getSave_path() != null ? current.getSave_path() : (current.getType() != null ? current.getType() : de.tki.comfymodels.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName()));
        java.io.File localFile = new java.io.File(modelsPath, subPath + java.io.File.separator + current.getName());

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

            JSONObject latestVersion = versions.getJSONObject(0);
            int latestVersionId = latestVersion.optInt("id", -1);

            if (latestVersionId != -1 && latestVersionId != currentVersionId) {
                JSONArray files = latestVersion.optJSONArray("files");
                if (files != null) {
                    for (int i = 0; i < files.length(); i++) {
                        JSONObject file = files.getJSONObject(i);
                        if (file.optBoolean("primary", false) || files.length() == 1) {
                            ModelInfo updated = new ModelInfo();
                            updated.setType(current.getType());
                            updated.setSave_path(current.getSave_path());
                            updated.setName(file.getString("name"));
                            updated.setUrl(file.getString("downloadUrl"));

                            long size = getRemoteSize(updated.getUrl());
                            updated.setByteSize(size);
                            updated.setSize(formatSize(size));
                            updated.setPopularity("⭐ UPDATE: " + latestVersion.optString("name", "New Version"));
                            return Optional.of(updated);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fail silently
        }
        return Optional.empty();
    }

    private boolean validateAndSetUrl(ModelInfo info, int rowIndex, String url, String popPrefix,
                                     BiConsumer<Integer, String> onStatusUpdate,
                                     BiConsumer<Integer, ModelInfo> onModelFound) {
        if (url == null) return false;
        
        if (url.contains("huggingface.co") && url.contains("/blob/")) {
            url = url.replace("/blob/", "/resolve/");
        }

        long size = getRemoteSize(url);
        if (size > 100) {
            info.setUrl(url);
            info.setPopularity(popPrefix);
            info.setByteSize(size);
            info.setSize(formatSize(size));
            onModelFound.accept(rowIndex, info);
            return true;
        }
        return false;
    }

    @Override
    public long getRemoteSize(String url) {
        try {
            if (url.contains("huggingface.co") && url.contains("/resolve/")) {
                String apiUrl = url.replace("/resolve/", "/api/models/").replace("/main/", "/file/");
                if (!apiUrl.contains("/api/models/")) {
                    // Fallback for direct resolve URLs
                    apiUrl = getHfApiBaseUrl() + "/models/" + url.split("huggingface.co/")[1].replace("/resolve/main/", "/file/");
                }
                HttpRequest.Builder apiBuilder = HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET().header("User-Agent", "Mozilla/5.0");
                String token = configService.getHfToken();
                if (!token.isEmpty()) apiBuilder.header("Authorization", "Bearer " + token);

                HttpResponse<String> apiRes = httpClient.send(apiBuilder.build(), HttpResponse.BodyHandlers.ofString());
                if (apiRes.statusCode() == 200) {
                    JSONObject fileData = new JSONObject(apiRes.body());
                    if (fileData.has("size")) return fileData.getLong("size");
                }
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).method("HEAD", HttpRequest.BodyPublishers.noBody()).header("User-Agent", "Mozilla/5.0");
            String token = configService.getHfToken();
            if (url.contains("huggingface.co") && !token.isEmpty()) builder.header("Authorization", "Bearer " + token);

            HttpResponse<Void> res = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() == 401 || res.statusCode() == 403) return -401;

            return res.headers().firstValueAsLong("Content-Length").orElse(-1L);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public String formatSize(long bytes) {
        if (bytes == -401) return "🔒 Auth Required";
        if (bytes <= 0) return "Unknown";
        if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
        return String.format(Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0);
    }

    private boolean fetchHuggingFaceUrlInSpecificRepo(ModelInfo info, int rowIndex, String repoId,
                                                       BiConsumer<Integer, String> onStatusUpdate,
                                                       BiConsumer<Integer, ModelInfo> onModelFound) {
        try {
            String treeUrl = getHfApiBaseUrl() + "/models/" + repoId + "/tree/main?recursive=true";
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(treeUrl)).GET();
            String token = configService.getHfToken();
            if (!token.isEmpty()) builder.header("Authorization", "Bearer " + token);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONArray files = new JSONArray(response.body());
                String targetName = info.getName().toLowerCase();
                
                for (int j = 0; j < files.length(); j++) {
                    JSONObject file = files.getJSONObject(j);
                    String path = file.optString("path");
                    if ("file".equals(file.optString("type"))) {
                        String fileNameOnly = path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
                        if (path.equalsIgnoreCase(info.getName()) || fileNameOnly.equalsIgnoreCase(targetName)) {
                            String downloadUrl = getHfResolveBaseUrl() + "/" + repoId + "/resolve/main/" + path;
                            return validateAndSetUrl(info, rowIndex, downloadUrl, "✨ AI RECURSIVE", onStatusUpdate, onModelFound);
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return false;
    }

    private boolean fetchHuggingFaceUrl(ModelInfo info, int rowIndex, String query, boolean officialOnly,
                                         BiConsumer<Integer, String> onStatusUpdate,
                                         BiConsumer<Integer, ModelInfo> onModelFound) {
        try {
            String searchUrl = getHfApiBaseUrl() + "/models?search=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&sort=downloads&direction=-1&limit=100";
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder().uri(URI.create(searchUrl)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONArray models = new JSONArray(response.body());
                for (int i = 0; i < models.length(); i++) {
                    String modelId = models.getJSONObject(i).getString("id");
                    String author = modelId.split("/")[0];
                    if (officialOnly && !OFFICIAL_AUTHORS.contains(author)) continue;
                    if (fetchHuggingFaceUrlInSpecificRepo(info, rowIndex, modelId, onStatusUpdate, onModelFound)) return true;
                }
            }
        } catch (Exception e) {}
        return false;
    }

    private boolean fetchCivitaiUrlByHash(ModelInfo info, int rowIndex, String hash,
                                         BiConsumer<Integer, String> onStatusUpdate,
                                         BiConsumer<Integer, ModelInfo> onModelFound) {
        try {
            String searchUrl = getCivitaiApiBaseUrl() + "/model-versions/by-hash/" + hash;
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder().uri(URI.create(searchUrl)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject version = new JSONObject(response.body());
                JSONArray files = version.optJSONArray("files");
                if (files != null) {
                    for (int j = 0; j < files.length(); j++) {
                        JSONObject file = files.getJSONObject(j);
                        if (file.optString("name").equalsIgnoreCase(info.getName()) || file.optBoolean("primary", false)) {
                            return validateAndSetUrl(info, rowIndex, file.optString("downloadUrl"), "🛡️ Civitai (Hash Match)", onStatusUpdate, onModelFound);
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return false;
    }

    private boolean fetchCivitaiUrl(ModelInfo info, int rowIndex, String query,
                                     BiConsumer<Integer, String> onStatusUpdate,
                                     BiConsumer<Integer, ModelInfo> onModelFound) {
        try {
            String searchUrl = getCivitaiApiBaseUrl() + "/models?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&sort=Most+Downloaded&limit=20";
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder().uri(URI.create(searchUrl)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                JSONArray items = json.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONArray versions = items.getJSONObject(i).optJSONArray("modelVersions");
                        if (versions != null) {
                            for (int v = 0; v < versions.length(); v++) {
                                JSONArray files = versions.getJSONObject(v).optJSONArray("files");
                                if (files != null) {
                                    for (int j = 0; j < files.length(); j++) {
                                        JSONObject file = files.getJSONObject(j);
                                        if (file.optString("name").equalsIgnoreCase(info.getName()) || file.optString("name").toLowerCase().contains(query.toLowerCase())) {
                                            return validateAndSetUrl(info, rowIndex, file.optString("downloadUrl"), "🛡️ Civitai", onStatusUpdate, onModelFound);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return false;
    }
}
