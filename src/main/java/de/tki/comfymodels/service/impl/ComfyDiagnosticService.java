package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.ModelInfo;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class ComfyDiagnosticService {

    @Autowired
    private ConfigService configService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    /**
     * Checks if a specific model is currently known to ComfyUI.
     * Queries /object_info to see if the model appears in the checkpoint/lora lists.
     */
    public boolean isModelAvailableInComfy(String modelName, String type) {
        String url = configService.getComfyUIUrl();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/object_info"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;

            JSONObject info = new JSONObject(response.body());
            
            // Typical node names for models
            String[] nodesToCheck = {"CheckpointLoaderSimple", "LoraLoader", "CheckpointLoader"};
            
            for (String node : nodesToCheck) {
                if (info.has(node)) {
                    JSONObject nodeInfo = info.getJSONObject(node);
                    if (nodeInfo.has("input")) {
                        JSONObject input = nodeInfo.getJSONObject("input");
                        if (input.has("required")) {
                            JSONObject required = input.getJSONObject("required");
                            for (String key : required.keySet()) {
                                Object val = required.get(key);
                                if (val instanceof org.json.JSONArray) {
                                    org.json.JSONArray outerArray = (org.json.JSONArray) val;
                                    if (outerArray.length() > 0) {
                                        Object firstElement = outerArray.get(0);
                                        if (firstElement instanceof org.json.JSONArray) {
                                            org.json.JSONArray options = (org.json.JSONArray) firstElement;
                                            for (int i = 0; i < options.length(); i++) {
                                                if (options.get(i) instanceof String && options.getString(i).equals(modelName)) return true;
                                            }
                                        } else if (firstElement instanceof String) {
                                            // Handle cases where the array itself is the list of options
                                            for (int i = 0; i < outerArray.length(); i++) {
                                                if (outerArray.get(i) instanceof String && outerArray.getString(i).equals(modelName)) return true;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Diagnostic] API Check failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Bulk check for a list of models.
     */
    public List<String> getMissingModels(List<ModelInfo> models) {
        List<String> missing = new ArrayList<>();
        for (ModelInfo info : models) {
            if (!isModelAvailableInComfy(info.getName(), info.getType())) {
                missing.add(info.getName());
            }
        }
        return missing;
    }
}
