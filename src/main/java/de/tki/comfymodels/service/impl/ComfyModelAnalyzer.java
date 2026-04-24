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
    
    @Autowired(required = false)
    private LocalAIService aiService;

    @Autowired
    private ModelListService modelListService;

    @Autowired(required = false)
    private GeminiAIService geminiService;

    private static final class ExpertPatterns {
        static final Pattern FILE = Pattern.compile("([a-zA-Z0-9_\\-\\.\\/]+\\.(?:safetensors|sft|ckpt|pth|pt|bin|onnx|yaml))", Pattern.CASE_INSENSITIVE);
        static final Pattern COMP_VAE = Pattern.compile("(vae|tokenizer|autoencoder)", Pattern.CASE_INSENSITIVE);
        static final Pattern COMP_CLIP = Pattern.compile("(clip|t5|text_encoder|llama|gemma|mistral|t5xxl|clip_l|clip_g|qwen)", Pattern.CASE_INSENSITIVE);
        static final Pattern COMP_UNET = Pattern.compile("(unet|diffusion|model|transformer|base|transformer|upscale|esrgan|z_image|lumina|flux|dit|mochi|ltx|cosmos|hunyuan|hy|wan|hidream|aura|svd)", Pattern.CASE_INSENSITIVE);
        static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]+)\\]\\((https?://[^\\)]+)\\)", Pattern.CASE_INSENSITIVE);
        static final Pattern NAKED_URL = Pattern.compile("(https?://[a-zA-Z0-9\\-\\.\\/\\?\\=\\&\\%]+(?:\\.(?:safetensors|sft|ckpt|pth|pt|bin|onnx|yaml))(?:\\?[^\\s\\\"\\']*)?)", Pattern.CASE_INSENSITIVE);
    }

    @Override
    public List<ModelInfo> analyze(String jsonText, String fileName) {
        List<ModelInfo> results = new ArrayList<>();
        if (jsonText == null || jsonText.trim().isEmpty()) return results;
        try {
            JSONObject jo = parseJson(jsonText);
            if (jo == null) return results;

            // NEW: Globale Kontext-Erkennung vor der Analyse
            String globalContext = extractGlobalContext(jo, fileName);

            findModelsMetadata(jo, results);
            scanForModelFiles(jo, null, results);
            
            // Wende globalen Kontext auf alle Ergebnisse an
            applyGlobalContext(results, globalContext);

            for (ModelInfo info : results) {
                String name = info.getName();

                Optional<ModelInfo> listMatch = (modelListService != null) ? modelListService.findByFilename(name) : Optional.empty();
                if (listMatch.isPresent()) {
                    ModelInfo match = listMatch.get();
                    info.setUrl(match.getUrl());
                    info.setSave_path(match.getSave_path());
                    info.setPopularity("📂 MODEL LIST MATCH");
                } else {
                    // Fallback to AI prediction if not in list
                    if (aiService == null) aiService = new LocalAIService();
                    LocalAIService.Prediction prediction = aiService.predictProvider(info.getName());
                    info.setPopularity(prediction.getLabel());
                    
                    // Falls die Popularität noch generisch ist, füge den Kontext hinzu
                    if (globalContext != null && !globalContext.isEmpty() && info.getPopularity().contains("Community")) {
                        info.setPopularity("✨ Context: " + globalContext + " (" + info.getPopularity() + ")");
                    }
                }
            }
        } catch (Exception e) {}
        return results;
    }

    private String extractGlobalContext(JSONObject jo, String fileName) {
        Map<String, Integer> hints = new HashMap<>();
        
        // 1. Aus Dateiname (höchste Prio)
        if (fileName != null && !fileName.toLowerCase().startsWith("workflow")) {
            String base = fileName.split("[_\\- ]")[0].toLowerCase();
            if (base.length() >= 3) return base;
        }

        // 2. Scan den ganzen JSON nach signifikanten Schlüsselwörtern
        String fullText = jo.toString().toLowerCase();
        String[] keywords = {"hidream", "flux", "auraflow", "sd3", "hunyuan", "mochi", "ltxv", "cosmos", "wan2.1"};
        for (String kw : keywords) {
            if (fullText.contains(kw)) hints.put(kw, hints.getOrDefault(kw, 0) + 5);
        }

        // 3. Scan Node-Typen
        String nodesText = fullText;
        if (nodesText.contains("hidream")) hints.put("hidream", hints.getOrDefault("hidream", 0) + 10);
        
        return hints.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private void applyGlobalContext(List<ModelInfo> res, String ctx) {
        if (ctx == null) return;
        for (ModelInfo i : res) {
            // Wenn der Dateiname den Kontext enthält oder der Dateiname sehr generisch ist (wie ae.safetensors)
            String n = i.getName().toLowerCase();
            if (n.contains(ctx) || n.equals("ae.safetensors") || n.contains("clip_l") || n.contains("t5xxl")) {
                if (i.getPopularity() == null || i.getPopularity().isEmpty()) {
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
                            // Strikte Übernahme des Pfades aus den Metadaten
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
        
        // Ignoriere Standardwerte, die keine Dateinamen sind
        if (vLow.equals("none") || vLow.equals("default") || vLow.equals("undefined") || vLow.length() < 4) return;

        // 1. Suche nach Markdown Links: [Name](URL)
        Matcher mdMatcher = ExpertPatterns.MD_LINK.matcher(v);
        while (mdMatcher.find()) {
            String name = mdMatcher.group(1);
            String url = fixUrl(mdMatcher.group(2));
            if (name.contains(".")) { // Nur wenn der Linktext wie ein Dateiname aussieht
                String type = hint != null ? inferTypeFromKey(hint) : null;
                if ((type == null || type.equals("checkpoints")) && url.contains("/resolve/main/split_files/")) {
                    String sub = url.substring(url.indexOf("/split_files/") + 13);
                    if (sub.contains("/")) type = sub.substring(0, sub.indexOf("/"));
                } else if ((type == null || type.equals("checkpoints")) && url.contains("/resolve/main/")) {
                    String sub = url.substring(url.indexOf("/resolve/main/") + 14);
                    if (sub.contains("/")) {
                        String folder = sub.substring(0, sub.indexOf("/")).toLowerCase();
                        if (folder.equals("loras") || folder.equals("vae") || folder.equals("checkpoints") || folder.equals("text_encoders") || folder.equals("diffusion_models") || folder.equals("controlnet")) {
                            type = folder;
                        }
                    }
                }
                if (type == null) type = ctx;
                addModelInfo(res, new ModelInfo(type != null ? type : "checkpoints", name, url));
            }
        }

        // 2. Suche nach nackten URLs
        Matcher nakedMatcher = ExpertPatterns.NAKED_URL.matcher(v);
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

        Matcher m = ExpertPatterns.FILE.matcher(v);
        while (m.find()) {
            String match = m.group(1);
            
            // Ignoriere offensichtliche URLs (wir wollen nur Dateinamen)
            if (match.startsWith("http") || match.startsWith("//")) continue;

            // Extrahiere nur den reinen Dateinamen, falls ein Pfad gematcht wurde
            String f = match;
            if (f.contains("/")) f = f.substring(f.lastIndexOf("/") + 1);
            if (f.contains("\\")) f = f.substring(f.lastIndexOf("\\") + 1);

            if (f.length() > 3) {
                // Erneuter Check nach Pfad-Extraktion
                String fLow = f.toLowerCase();
                if (fLow.equals("none.safetensors") || fLow.equals("default.safetensors")) continue;

                String type = hint != null ? inferTypeFromKey(hint) : null;
                if (type == null) type = ctx;
                
                // Verfeinerung basierend auf Dateinamen (Höchste Priorität für Spezialmodelle)
                if (fLow.contains("qwen-image-lightning")) type = "unet"; 
                else if (fLow.contains("vae_approx") || fLow.contains("vae-approx")) type = "vae_approx";
                else if (ExpertPatterns.COMP_VAE.matcher(fLow).find() && (type == null || type.equals("checkpoints"))) type = "vae";
                else if (fLow.contains("clip_vision")) type = "clip_vision";
                else if (ExpertPatterns.COMP_CLIP.matcher(fLow).find() && (type == null || type.equals("checkpoints"))) type = "clip";
                else if (ExpertPatterns.COMP_UNET.matcher(fLow).find() && (type == null || type.equals("checkpoints") || (type.equals("clip") && !fLow.contains("clip_l") && !fLow.contains("clip_g")))) type = "unet";
                
                addModelInfo(res, new ModelInfo(type != null ? type : "checkpoints", f, "MISSING"));
            }
        }
    }

    private String inferTypeFromNode(String nt) {
        nt = nt.toLowerCase();
        if (nt.contains("lora")) return "loras";
        if (nt.contains("vae_approx") || nt.contains("vae-approx")) return "vae_approx";
        if (nt.contains("vae")) return "vae";
        if (nt.contains("controlnet") || nt.contains("t2i_adapter")) return "controlnet";
        if (nt.contains("upscale") || nt.contains("esrgan")) return "upscale_models";
        if (nt.contains("latent_upscale")) return "latent_upscale_models";
        if (nt.contains("clip_vision")) return "clip_vision";
        if (nt.contains("clip") || nt.contains("text_encoder") || nt.contains("cliploader") || nt.contains("llama") || nt.contains("mistral") || nt.contains("t5") || nt.contains("qwen")) return "clip";
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
        if (k.contains("vae_approx") || k.contains("vae-approx")) return "vae_approx";
        if (k.contains("vae")) return "vae";
        if (k.contains("clip_vision")) return "clip_vision";
        if (k.contains("clip") || k.contains("text_encoder") || k.contains("llama") || k.contains("mistral") || k.contains("t5") || k.contains("qwen")) return "clip";
        if (k.contains("diffusion_model")) return "diffusion_models";
        if (k.contains("unet") || k.contains("diffusion") || k.contains("hidream") || k.contains("aura") || k.contains("mochi") || k.contains("svd") || k.contains("z_image") || k.contains("lumina") || k.contains("flux") || k.contains("dit")) return "unet";
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
                if (!info.getUrl().equals("MISSING")) {
                    e.setUrl(info.getUrl());
                    e.setType(info.getType());
                    e.setSave_path(info.getSave_path());
                } else if (e.getUrl().equals("MISSING")) {
                    boolean isEWeak = e.getType() == null || e.getType().equals("checkpoints") || e.getType().equals("clip");
                    boolean isInfoStrong = info.getType() != null && (info.getType().equals("unet") || info.getType().equals("checkpoints"));
                    
                    if (isEWeak && isInfoStrong) {
                        e.setType(info.getType());
                        e.setSave_path(info.getSave_path());
                    }
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
