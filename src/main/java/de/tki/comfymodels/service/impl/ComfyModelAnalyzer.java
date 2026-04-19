package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IModelAnalyzer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class ComfyModelAnalyzer implements IModelAnalyzer {
    
    @Autowired(required = false)
    private LocalAIService aiService;

    @Autowired
    private ModelListService modelListService;

    @Autowired(required = false)
    private GeminiAIService geminiService;

    private static final class ExpertPatterns {
        static final Pattern FILE = Pattern.compile("([a-zA-Z0-9_\\-\\.\\/]+\\.(?:safetensors|sft|ckpt|pt|bin|pth|onnx|yaml))", Pattern.CASE_INSENSITIVE);
        static final Pattern COMP_VAE = Pattern.compile("(vae|tokenizer|autoencoder)", Pattern.CASE_INSENSITIVE);
        static final Pattern COMP_CLIP = Pattern.compile("(clip|t5|text_encoder|llama|gemma|mistral)", Pattern.CASE_INSENSITIVE);
        static final Pattern COMP_UNET = Pattern.compile("(unet|diffusion|model|transformer|base|transformer|upscale|esrgan)", Pattern.CASE_INSENSITIVE);
    }

    private final Map<String, String> knownGoodUrls = new HashMap<>();

    @PostConstruct
    public void init() {
        try (InputStream is = getClass().getResourceAsStream("/known_good_urls.json")) {
            if (is != null) {
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JSONObject jo = new JSONObject(json);
                for (String key : jo.keySet()) {
                    knownGoodUrls.put(key.toLowerCase(), jo.getString(key));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load known_good_urls.json: " + e.getMessage());
        }
    }

    @Override
    public List<ModelInfo> analyze(String jsonText, String fileName) {
        // Fallback falls Spring Injection fehlt (Test-Modus)
        if (aiService == null) aiService = new LocalAIService();
        if (knownGoodUrls.isEmpty()) init(); // Fallback für Tests ohne Spring Context

        List<ModelInfo> results = new ArrayList<>();
        if (jsonText == null || jsonText.trim().isEmpty()) return results;
        try {
            JSONObject jo = parseJson(jsonText);
            if (jo == null) return results;
            findModelsMetadata(jo, results);
            scanForModelFiles(jo, null, results);
            applyContext(results, fileName);
            for (ModelInfo info : results) {
                String low = info.getName().toLowerCase();
                Optional<ModelInfo> uploadedMatch = (modelListService != null) ? modelListService.findByFilename(info.getName()) : Optional.empty();
                if (uploadedMatch.isPresent()) {
                    info.setUrl(uploadedMatch.get().getUrl());
                    info.setPopularity("📂 USER DEFINED LIST");
                } else if (knownGoodUrls.containsKey(low)) {
                    info.setUrl(knownGoodUrls.get(low));
                    info.setPopularity("🧠 AI VERIFIED OFFICIAL");
                } else {
                    LocalAIService.Prediction prediction = aiService.predictProvider(info.getName());
                    info.setPopularity(prediction.getLabel());
                }
            }
        } catch (Exception e) {}
        return results;
    }

    private JSONObject parseJson(String text) {
        try {
            String trimmed = text.trim();
            if (trimmed.startsWith("{")) return new JSONObject(trimmed);
            int s = trimmed.indexOf("{"), e = trimmed.lastIndexOf("}");
            if (s != -1 && e != -1) return new JSONObject(trimmed.substring(s, e + 1));
        } catch (Exception e) {}
        return null;
    }

    private void applyContext(List<ModelInfo> res, String fn) {
        if (fn == null) return;
        String ctx = fn.toLowerCase().split("[_\\- ]")[0];
        if (ctx.length() < 3) return;
        for (ModelInfo i : res) if (i.getName().toLowerCase().contains(ctx) && i.getPopularity().contains("Community")) i.setPopularity("📌 Context Match: " + ctx);
    }

    private void findModelsMetadata(Object obj, List<ModelInfo> res) {
        if (obj instanceof JSONObject) {
            JSONObject jo = (JSONObject) obj;
            if (jo.has("models") && jo.get("models") instanceof JSONArray) {
                JSONArray ms = jo.getJSONArray("models");
                for (int i = 0; i < ms.length(); i++) {
                    JSONObject m = ms.optJSONObject(i);
                    if (m != null) {
                        String n = m.optString("name", m.optString("filename"));
                        if (n != null && !n.isEmpty()) addModelInfo(res, new ModelInfo(inferTypeFromKey(m.optString("type", "checkpoints")), n, m.optString("url", "MISSING")));
                    }
                }
            }
            for (String k : jo.keySet()) findModelsMetadata(jo.get(k), res);
        } else if (obj instanceof JSONArray) {
            JSONArray ja = (JSONArray) obj;
            for (int i = 0; i < ja.length(); i++) findModelsMetadata(ja.get(i), res);
        }
    }

    private void scanForModelFiles(Object obj, String ctx, List<ModelInfo> res) {
        if (obj instanceof JSONObject) {
            JSONObject jo = (JSONObject) obj;
            String node = jo.optString("class_type", jo.optString("type", null));
            String curCtx = node != null ? (inferTypeFromNode(node) != null ? inferTypeFromNode(node) : ctx) : ctx;
            if (jo.has("widgets_values")) {
                JSONArray ws = jo.optJSONArray("widgets_values");
                if (ws != null) for (int i = 0; i < ws.length(); i++) if (ws.get(i) instanceof String) processVal((String) ws.get(i), curCtx, node, res);
            }
            for (String k : jo.keySet()) {
                if (k.equals("widgets_values")) continue;
                Object v = jo.get(k);
                if (v instanceof String) processVal((String) v, curCtx, k, res);
                else if (v instanceof JSONObject || v instanceof JSONArray) scanForModelFiles(v, curCtx, res);
            }
        } else if (obj instanceof JSONArray) {
            JSONArray ja = (JSONArray) obj;
            for (int i = 0; i < ja.length(); i++) scanForModelFiles(ja.get(i), ctx, res);
        }
    }

    private void processVal(String v, String ctx, String hint, List<ModelInfo> res) {
        Matcher m = ExpertPatterns.FILE.matcher(v);
        while (m.find()) {
            String f = m.group(1);
            if (f.length() > 3) {
                String type = hint != null ? inferTypeFromKey(hint) : null;
                if (type == null) type = ctx;
                String fLow = f.toLowerCase();
                if (ExpertPatterns.COMP_VAE.matcher(fLow).find()) type = "vae";
                else if (ExpertPatterns.COMP_CLIP.matcher(fLow).find()) type = "clip";
                else if (ExpertPatterns.COMP_UNET.matcher(fLow).find() && (type == null || type.equals("checkpoints"))) type = "unet";
                addModelInfo(res, new ModelInfo(type != null ? type : "checkpoints", f, "MISSING"));
            }
        }
    }

    private String inferTypeFromNode(String nt) {
        nt = nt.toLowerCase();
        if (nt.contains("lora")) return "loras";
        if (nt.contains("vae")) return "vae";
        if (nt.contains("controlnet") || nt.contains("t2i_adapter")) return "controlnet";
        if (nt.contains("upscale") || nt.contains("esrgan")) return "upscale_models";
        if (nt.contains("clip") || nt.contains("text_encoder") || nt.contains("cliploader") || nt.contains("llama") || nt.contains("mistral")) return "clip";
        if (nt.contains("unet") || nt.contains("diffusion") || nt.contains("ltxv") || nt.contains("cosmos") || nt.contains("hunyuan") || nt.contains("wan") || nt.contains("hidream") || nt.contains("aura") || nt.contains("mochi") || nt.contains("svd")) return "unet";
        return null;
    }

    private String inferTypeFromKey(String k) {
        k = k.toLowerCase();
        if (k.contains("lora")) return "loras";
        if (k.contains("vae")) return "vae";
        if (k.contains("clip") || k.contains("text_encoder") || k.contains("llama") || k.contains("mistral")) return "clip";
        if (k.contains("unet") || k.contains("diffusion") || k.contains("hidream") || k.contains("aura") || k.contains("mochi") || k.contains("svd")) return "unet";
        if (k.contains("control") && k.contains("net")) return "controlnet";
        if (k.contains("upscale") || k.contains("esrgan")) return "upscale_models";
        return null;
    }

    private void addModelInfo(List<ModelInfo> res, ModelInfo info) {
        for (ModelInfo e : res) {
            if (e.getName().equalsIgnoreCase(info.getName())) {
                if (e.getUrl().equals("MISSING") && !info.getUrl().equals("MISSING")) e.setUrl(info.getUrl());
                if (e.getType().equals("checkpoints") && !info.getType().equals("checkpoints")) e.setType(info.getType());
                return;
            }
        }
        res.add(info);
    }
}
