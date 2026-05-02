package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.service.INodeSyncService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.BiConsumer;

@Service
public class NodeSyncService implements INodeSyncService {

    private final ConfigService configService;
    
    // Minimal set of built-in nodes to avoid false positives
    private static final Set<String> BUILTIN_NODES = new HashSet<>(Arrays.asList(
            "KSampler", "CheckpointLoaderSimple", "CLIPTextEncode", "EmptyLatentImage",
            "VAEDecode", "VAEEncode", "ControlNetApply", "CLIPVisionEncode",
            "ImageUpscaleWithModel", "LoraLoader", "PreviewImage", "SaveImage",
            "ConditioningAverage", "ConditioningCombine", "ConditioningConcat",
            "LatentUpscale", "ModelSamplingDiscrete", "SetLatentNoiseMask"
            // ... truncated for brevity, in a real app this would be much larger or loaded from a file
    ));

    @Autowired
    public NodeSyncService(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public Set<String> findMissingNodes(String workflowJson) {
        Set<String> missing = new HashSet<>();
        if (workflowJson == null || workflowJson.isEmpty()) return missing;

        try {
            JSONObject jo = new JSONObject(workflowJson);
            // ComfyUI workflows can be in API format or UI format
            if (jo.has("nodes")) {
                // UI format
                org.json.JSONArray nodes = jo.getJSONArray("nodes");
                for (int i = 0; i < nodes.length(); i++) {
                    String type = nodes.getJSONObject(i).optString("type");
                    if (type != null && !type.isEmpty() && !BUILTIN_NODES.contains(type)) {
                        missing.add(type);
                    }
                }
            } else {
                // API format
                for (String key : jo.keySet()) {
                    JSONObject node = jo.optJSONObject(key);
                    if (node != null && node.has("class_type")) {
                        String type = node.getString("class_type");
                        if (!BUILTIN_NODES.contains(type)) {
                            missing.add(type);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return missing;
    }

    @Override
    public void resolveMissingNodes(Set<String> missingNodes, BiConsumer<String, String> onResolved, Runnable onFinished) {
        // In a real implementation, this might search ComfyUI-Manager's database or GitHub
        new Thread(() -> {
            for (String node : missingNodes) {
                if (onResolved != null) {
                    onResolved.accept(node, "https://github.com/search?q=" + node + "+ComfyUI&type=repositories");
                }
            }
            if (onFinished != null) onFinished.run();
        }).start();
    }
}
