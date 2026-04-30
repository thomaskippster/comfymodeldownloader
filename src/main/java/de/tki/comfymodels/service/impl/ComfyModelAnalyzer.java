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

@Service
public class ComfyModelAnalyzer implements IModelAnalyzer {
    
    @Autowired
    private LocalAIService aiService;

    @Autowired
    private ModelListService modelListService;

    @Autowired(required = false)
    private GeminiAIService geminiService;

    private final Pattern FILE_PATTERN = Pattern.compile("([a-zA-Z0-9_\\-\\.\\/]+\\.(?:safetensors|sft|ckpt|pth|pt|bin|onnx|yaml))", Pattern.CASE_INSENSITIVE);
    private final Pattern COMP_VAE_PATTERN = Pattern.compile("(vae|tokenizer|autoencoder|encoder|decoder)", Pattern.CASE_INSENSITIVE);
    private final Pattern COMP_CLIP_PATTERN = Pattern.compile("(clip|text.encoder|t5|llama|gemma|mistral|qwen|bert|vit|embed)", Pattern.CASE_INSENSITIVE);
    private final Pattern COMP_UNET_PATTERN = Pattern.compile("(unet|diffusion|transformer|dit|model|base|upscale|esrgan|resnet|sampling)", Pattern.CASE_INSENSITIVE);
    private final Pattern MD_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\((https?://[^\\)]+)\\)", Pattern.CASE_INSENSITIVE);
    private final Pattern NAKED_URL_PATTERN = Pattern.compile("(https?://[a-zA-Z0-9\\-\\._\\/\\?\\=\\&\\%]+(?:\\.(?:safetensors|sft|ckpt|pth|pt|bin|onnx|yaml))(?:\\?[^\\s\\\"\\']*)?)", Pattern.CASE_INSENSITIVE);

    @Override
    public List<ModelInfo> analyze(String jsonText, String fileName) {
        List<ModelInfo> results = new ArrayList<>();
        if (jsonText == null || jsonText.trim().isEmpty()) return results;
        try {
            JSONObject jo = parseJson(jsonText);
            if (jo == null) return results;

            // Global context detection based on frequency/filename rather than hardcoded list
            String globalContext = extractGlobalContext(jo, fileName);

            findModelsMetadata(jo, results);
            scanForModelFiles(jo, null, results);
            
            applyGlobalContext(results, globalContext);

            for (ModelInfo info : results) {
                String name = info.getName();

                Optional<ModelInfo> listMatch = (modelListService != null) ? modelListService.findByFilename(name) : Optional.empty();
                if (listMatch.isPresent()) {
                    ModelInfo match = listMatch.get();
                    info.setUrl(match.getUrl());
                    info.setSave_path(match.getSave_path());
                    info.setSize(match.getSize());
                    info.setPopularity("📂 MODEL LIST MATCH");
                } else {
                    if (aiService != null) {
                        LocalAIService.Prediction prediction = aiService.predictProvider(info.getName());
                        info.setPopularity(prediction.getLabel());
                    }
                    
                    if (globalContext != null && !globalContext.isEmpty() && info.getPopularity() != null && info.getPopularity().contains("Community")) {
                        info.setPopularity("✨ Context: " + globalContext + " (" + info.getPopularity() + ")");
                    }
                }
            }
        } catch (Exception e) {}
        return results;
    }

    private String extractGlobalContext(JSONObject jo, String fileName) {
        // Generic approach: extract the most significant prefix from filename or content
        if (fileName != null && !fileName.toLowerCase().startsWith("workflow")) {
            String base = fileName.split("[_\\- ]")[0].toLowerCase();
            if (base.length() >= 3) return base;
        }

        // Scan for frequently occurring specific terms in class_types (that are not standard keywords)
        Map<String, Integer> counts = new HashMap<>();
        String fullText = jo.toString().toLowerCase();
        // Look for common architecture names as hints
        String[] archHints = {"flux", "sdxl", "sd15", "sd3", "hunyuan", "mochi", "ltx", "cosmos", "wan", "hidream", "aura", "lumina"};
        for (String hint : archHints) {
            if (fullText.contains(hint)) counts.put(hint, counts.getOrDefault(hint, 0) + 1);
        }

        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private void applyGlobalContext(List<ModelInfo> res, String ctx) {
        if (ctx == null) return;
        for (ModelInfo i : res) {
            String n = i.getName().toLowerCase();
            // If filename matches context or is a generic model file
            if (n.contains(ctx) || n.startsWith("ae.") || n.contains("clip_") || n.contains("t5") || n.contains("diffusion")) {
                if (i.getPopularity() == null || i.getPopularity().isEmpty() || i.getPopularity().contains("Community")) {
                    i.setPopularity("✨ Context: " + ctx);
                }
            }
        }
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

    private void findModelsMetadata(Object obj, List<ModelInfo> res) {
        if (obj instanceof JSONObject) {
            JSONObject jo = (JSONObject) obj;
            if (jo.has("models") && jo.get("models") instanceof JSONArray) {
                JSONArray ms = jo.getJSONArray("models");
                for (int i = 0; i < ms.length(); i++) {
                    JSONObject m = ms.optJSONObject(i);
                    if (m != null) {
                        String n = m.optString("name", m.optString("filename"));
                        if (n != null && !n.isEmpty()) {
                            String rawPath = m.optString("directory", m.optString("type", "checkpoints"));
                            ModelInfo info = new ModelInfo(rawPath, n, m.optString("url", "MISSING"));
                            info.setSave_path(rawPath);
                            addModelInfo(res, info);
                        }
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
            
            // Ignore information from markdown notes as requested
            if (node != null && (node.equalsIgnoreCase("MarkdownNote") || node.equalsIgnoreCase("Note"))) {
                return;
            }

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
        if (v == null || v.trim().isEmpty()) return;
        String vLow = v.toLowerCase().trim();
        
        if (vLow.equals("none") || vLow.equals("default") || vLow.equals("undefined") || vLow.length() < 4) return;

        Matcher mdMatcher = MD_LINK_PATTERN.matcher(v);
        while (mdMatcher.find()) {
            String name = mdMatcher.group(1);
            String url = fixUrl(mdMatcher.group(2));
            if (name.contains(".")) {
                String type = hint != null ? inferTypeFromKey(hint) : null;
                if ((type == null || type.equals("checkpoints")) && url.contains("/resolve/main/")) {
                    String sub = url.substring(url.indexOf("/resolve/main/") + 14);
                    if (sub.contains("/")) {
                        String folder = sub.substring(0, sub.indexOf("/")).toLowerCase();
                        if (folder.matches("(loras|vae|checkpoints|text_encoders|diffusion_models|controlnet|embeddings|upscale_models)")) {
                            type = folder;
                        }
                    }
                }
                if (type == null) type = ctx;
                addModelInfo(res, new ModelInfo(type != null ? type : "checkpoints", name, url));
            }
        }

        Matcher nakedMatcher = NAKED_URL_PATTERN.matcher(v);
        while (nakedMatcher.find()) {
            String url = fixUrl(nakedMatcher.group(1));
            String name = url;
            if (name.contains("?")) name = name.substring(0, name.indexOf("?"));
            if (name.contains("/")) name = name.substring(name.lastIndexOf("/") + 1);
            
            if (name.length() > 3) {
                String type = hint != null ? inferTypeFromKey(hint) : null;
                if (type == null) type = ctx;
                addModelInfo(res, new ModelInfo(type != null ? type : "checkpoints", name, url));
            }
        }

        Matcher m = FILE_PATTERN.matcher(v);
        while (m.find()) {
            String match = m.group(1);
            if (match.startsWith("http") || match.startsWith("//")) continue;

            String f = match;
            if (f.contains("/")) f = f.substring(f.lastIndexOf("/") + 1);
            if (f.contains("\\")) f = f.substring(f.lastIndexOf("\\") + 1);

            if (f.length() > 3) {
                String fLow = f.toLowerCase();
                if (fLow.equals("none.safetensors") || fLow.equals("default.safetensors")) continue;

                String type = hint != null ? inferTypeFromKey(hint) : null;
                if (type == null) type = ctx;
                
                // Generic refinement based on structural keywords
                if (COMP_VAE_PATTERN.matcher(fLow).find() && (type == null || type.equals("checkpoints"))) type = "vae";
                else if (COMP_CLIP_PATTERN.matcher(fLow).find() && (type == null || type.equals("checkpoints"))) type = "clip";
                else if (COMP_UNET_PATTERN.matcher(fLow).find() && (type == null || type.equals("checkpoints") || (type.equals("clip") && !fLow.contains("clip_l") && !fLow.contains("clip_g")))) type = "unet";
                
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
        if (nt.contains("latent_upscale")) return "latent_upscale_models";
        if (nt.contains("clip_vision") || nt.contains("clipvision")) return "clip_vision";
        if (nt.contains("clip") || nt.contains("text_encoder") || nt.contains("tokenizer")) return "clip";
        if (nt.contains("diffusion_model")) return "diffusion_models";
        if (nt.contains("unet") || nt.contains("diffusion")) return "unet";
        if (nt.contains("gligen")) return "gligen";
        if (nt.contains("embeddings") || nt.contains("textual_inversion")) return "embeddings";
        if (nt.contains("hypernetwork")) return "hypernetworks";
        if (nt.contains("photomaker")) return "photomaker";
        if (nt.contains("style_model")) return "style_models";
        if (nt.contains("audio") && nt.contains("encoder")) return "audio_encoders";
        if (nt.contains("diffusers")) return "diffusers";
        if (nt.contains("configs")) return "configs";
        if (nt.contains("model_patch")) return "model_patches";
        return null;
    }

    private String inferTypeFromKey(String k) {
        k = k.toLowerCase();
        if (k.contains("lora")) return "loras";
        if (k.contains("vae")) return "vae";
        if (k.contains("clip_vision") || k.contains("clipvision")) return "clip_vision";
        if (k.contains("clip") || k.contains("text_encoder") || k.contains("tokenizer")) return "clip";
        if (k.contains("diffusion_model")) return "diffusion_models";
        if (k.contains("unet") || k.contains("diffusion")) return "unet";
        if (k.contains("control") && k.contains("net")) return "controlnet";
        if (k.contains("upscale") || k.contains("esrgan")) return "upscale_models";
        if (k.contains("embedding")) return "embeddings";
        if (k.contains("hypernetwork")) return "hypernetworks";
        if (k.contains("gligen")) return "gligen";
        if (k.contains("photomaker")) return "photomaker";
        if (k.contains("style_model")) return "style_models";
        if (k.contains("audio") && k.contains("encoder")) return "audio_encoders";
        if (k.contains("config")) return "configs";
        return null;
    }

    private void addModelInfo(List<ModelInfo> res, ModelInfo info) {
        for (ModelInfo e : res) {
            if (e.getName().equalsIgnoreCase(info.getName())) {
                // If the existing entry is generic "checkpoints" but we found a more specific type
                if ("checkpoints".equals(e.getType()) && !"checkpoints".equals(info.getType())) {
                    e.setType(info.getType());
                    if (info.getSave_path() != null) e.setSave_path(info.getSave_path());
                }
                
                // Always take URL if the current one is MISSING
                if (e.getUrl().equals("MISSING") && !info.getUrl().equals("MISSING")) {
                    e.setUrl(info.getUrl());
                    if (info.getSave_path() != null) e.setSave_path(info.getSave_path());
                    if (info.getType() != null && !"checkpoints".equals(info.getType())) e.setType(info.getType());
                }
                return;
            }
        }
        res.add(info);
    }

    private String fixUrl(String url) {
        if (url == null) return null;
        if (url.contains("huggingface.co") && url.contains("/blob/")) {
            return url.replace("/blob/", "/resolve/");
        }
        return url;
    }
}
