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

    private void performSearch(ModelInfo info, int index, String fileName, String workflowContext,
                               BiConsumer<Integer, String> onStatusUpdate,
                               BiConsumer<Integer, ModelInfo> onModelFound) {
        // Priority 1: User defined Model List
        Optional<ModelInfo> manualMatch = modelListService.findByFilename(info.getName());
        if (manualMatch.isPresent() && !manualMatch.get().getUrl().equals("MISSING")) {
            onStatusUpdate.accept(index, "📂 Found in Model List");
            if (validateAndSetUrl(info, index, manualMatch.get().getUrl(), "📂 USER DEFINED", onStatusUpdate, onModelFound)) return;
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
        String[] nameParts = cleanName.split("[_\\- ]");
        String baseName = nameParts.length > 0 ? nameParts[0] : cleanName;
        
        String contextPrefix = "";
        if (fileName != null && !fileName.isEmpty()) {
            String[] fileParts = fileName.toLowerCase().split("[_\\- ]");
            if (fileParts.length > 0) {
                contextPrefix = fileParts[0];
                if (contextPrefix.length() < 3 || contextPrefix.startsWith("workflow")) {
                    contextPrefix = "";
                }
            }
        }
        
        if (contextPrefix.isEmpty() && info.getPopularity() != null && info.getPopularity().contains("Context: ")) {
            String[] popParts = info.getPopularity().split("Context: ");
            if (popParts.length > 1) {
                contextPrefix = popParts[1].split(" ")[0].toLowerCase();
            }
        }

        LinkedHashSet<String> attempts = new LinkedHashSet<>();
        if (!contextPrefix.isEmpty()) {
            attempts.add(contextPrefix + " " + modelName);
            attempts.add(contextPrefix + " " + cleanName);
        }
        attempts.add(modelName);
        attempts.add(cleanName);
        attempts.add(baseName);

        // Suche in offiziellen Repos (Top 100)
        for (String query : attempts) {
            if (query.length() < 3) continue;
            onStatusUpdate.accept(index, "🔍 Searching Official: " + query);
            if (fetchHuggingFaceUrl(info, index, query, true, onStatusUpdate, onModelFound)) return;
        }
        
        // Suche in Community Repos (Top 100)
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

    private boolean validateAndSetUrl(ModelInfo info, int rowIndex, String url, String popPrefix,
                                     BiConsumer<Integer, String> onStatusUpdate,
                                     BiConsumer<Integer, ModelInfo> onModelFound) {
        if (url == null) return false;
        
        // Normalize Hugging Face blob URLs to resolve URLs
        if (url.contains("huggingface.co") && url.contains("/blob/")) {
            url = url.replace("/blob/", "/resolve/");
        }

        long size = getRemoteSize(url);
        if (size > 100) {
            info.setUrl(url);
            info.setPopularity(popPrefix);
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

            long bytes = res.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (bytes <= 2000) {
                HttpRequest.Builder rBuilder = HttpRequest.newBuilder().uri(URI.create(url)).header("Range", "bytes=0-0").header("User-Agent", "Mozilla/5.0");
                if (url.contains("huggingface.co") && !token.isEmpty()) rBuilder.header("Authorization", "Bearer " + token);
                HttpResponse<Void> rRes = httpClient.send(rBuilder.build(), HttpResponse.BodyHandlers.discarding());
                String cr = rRes.headers().firstValue("Content-Range").orElse("");
                bytes = cr.contains("/") ? Long.parseLong(cr.substring(cr.lastIndexOf("/") + 1)) : rRes.headers().firstValueAsLong("Content-Length").orElse(-1L);
            }
            return bytes;
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public String formatSize(long bytes) {
        if (bytes == -401) return "🔒 Auth Required";
        if (bytes <= 0) return "Unknown";
        if (bytes > 1024L * 1024L * 1024L) return String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
        return String.format("%.2f MB", bytes / 1024.0 / 1024.0);
    }

    private boolean fetchHuggingFaceUrlInSpecificRepo(ModelInfo info, int rowIndex, String repoId,
                                                       BiConsumer<Integer, String> onStatusUpdate,
                                                       BiConsumer<Integer, ModelInfo> onModelFound) {
        try {
            String treeUrl = "https://huggingface.co/api/models/" + repoId + "/tree/main?recursive=true";
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
                    String type = file.optString("type");
                    if ("file".equals(type)) {
                        String fileNameOnly = path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
                        if (path.equalsIgnoreCase(info.getName()) || fileNameOnly.equalsIgnoreCase(targetName)) {
                            String downloadUrl = "https://huggingface.co/" + repoId + "/resolve/main/" + path;
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
            String searchUrl = "https://huggingface.co/api/models?search=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&sort=downloads&direction=-1&limit=100";
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

    private boolean fetchCivitaiUrl(ModelInfo info, int rowIndex, String query,
                                     BiConsumer<Integer, String> onStatusUpdate,
                                     BiConsumer<Integer, ModelInfo> onModelFound) {
        try {
            String searchUrl = "https://civitai.com/api/v1/models?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&sort=Most+Downloaded&limit=20";
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
