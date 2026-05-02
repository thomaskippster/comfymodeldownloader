package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.ModelInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ModelListService {
    private static final String STORAGE_FILE = "uploaded_models.json";
    private List<ModelInfo> models = new ArrayList<>();

    @Autowired
    private ConfigService configService;

    @PostConstruct
    public void init() {
        loadFromStorage();
    }

    public void importJson(File file) throws Exception {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        importJsonContent(content);
    }

    public void importJsonContent(String content) throws Exception {
        JSONObject root = new JSONObject(content);
        JSONArray modelsArray = root.getJSONArray("models");
        
        List<ModelInfo> newModels = new ArrayList<>();
        for (int i = 0; i < modelsArray.length(); i++) {
            JSONObject m = modelsArray.getJSONObject(i);
            String rawDir = m.optString("directory", m.optString("type", "checkpoints"));
            ModelInfo info = new ModelInfo(
                rawDir,
                m.optString("name", "Unknown"),
                m.optString("url", "MISSING")
            );
            info.setBase(m.optString("base", ""));
            info.setSave_path(m.optString("save_path", rawDir));
            info.setDescription(m.optString("description", ""));
            info.setReference(m.optString("reference", ""));
            info.setFilename(m.optString("filename", ""));
            String sizeStr = m.optString("size", "Unknown");
            info.setSize(sizeStr);
            info.setByteSize(parseSize(sizeStr));
            newModels.add(info);
        }
        
        this.models = newModels;
        saveToStorage();
    }

    public void importFromUrl(String url) {
        if (url == null || url.isEmpty()) return;
        
        // Ensure we use the raw URL if a GitHub blob URL was provided
        final String finalUrl = url.contains("github.com") && url.contains("/blob/") ? 
                               url.replace("github.com", "raw.githubusercontent.com").replace("/blob/", "/") : url;

        new Thread(() -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
                        .connectTimeout(java.time.Duration.ofSeconds(10))
                        .build();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(finalUrl))
                        .build();
                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    importJsonContent(response.body());
                    System.out.println("[Auto-Import] Successfully updated model list (" + models.size() + " models).");
                }
            } catch (Exception ignored) {
                // Silent fail as requested - no stacktrace, no dialog
                System.out.println("[Auto-Import] Optional model list update skipped (source unreachable or invalid).");
            }
        }).start();
    }

    private long parseSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty() || "Unknown".equals(sizeStr)) return -1;
        try {
            String clean = sizeStr.toUpperCase().replaceAll("[^0-9\\.]", "");
            double val = Double.parseDouble(clean);
            if (sizeStr.toUpperCase().contains("GB")) return (long) (val * 1024L * 1024L * 1024L);
            if (sizeStr.toUpperCase().contains("MB")) return (long) (val * 1024L * 1024L);
            if (sizeStr.toUpperCase().contains("KB")) return (long) (val * 1024L);
            return (long) val;
        } catch (Exception e) {
            return -1;
        }
    }

    public Optional<ModelInfo> findByFilename(String filename) {
        if (filename == null) return Optional.empty();
        String low = filename.toLowerCase();
        
        // 1. Direct match
        Optional<ModelInfo> direct = models.stream()
                .filter(m -> filename.equalsIgnoreCase(m.getFilename()) || filename.equalsIgnoreCase(m.getName()))
                .findFirst();
        if (direct.isPresent()) return direct;

        // 2. Strip paths and extensions for fuzzy match
        String lowStrip = low.contains("/") ? low.substring(low.lastIndexOf("/") + 1) : low;
        if (lowStrip.contains("\\")) lowStrip = lowStrip.substring(lowStrip.lastIndexOf("\\") + 1);
        String lowNoExt = lowStrip.contains(".") ? lowStrip.substring(0, lowStrip.lastIndexOf(".")) : lowStrip;

        final String finalLowStrip = lowStrip;
        final String finalLowNoExt = lowNoExt;

        return models.stream().filter(m -> {
            String mf = m.getFilename() != null ? m.getFilename().toLowerCase() : "";
            String mn = m.getName() != null ? m.getName().toLowerCase() : "";
            return mf.equals(finalLowStrip) || mn.equals(finalLowStrip) || 
                   mf.startsWith(finalLowNoExt) || mn.startsWith(finalLowNoExt);
        }).findFirst();
    }

    private void loadFromStorage() {
        try {
            File file = configService != null ? configService.getFileInAppData(STORAGE_FILE) : new File(STORAGE_FILE);
            if (file.exists()) {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                JSONArray array = new JSONArray(content);
                models.clear();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject m = array.getJSONObject(i);
                    ModelInfo info = new ModelInfo(
                        m.optString("type", "checkpoints"),
                        m.optString("name", "Unknown"),
                        m.optString("url", "MISSING")
                    );
                    info.setBase(m.optString("base"));
                    info.setSave_path(m.optString("save_path"));
                    info.setDescription(m.optString("description"));
                    info.setReference(m.optString("reference"));
                    info.setFilename(m.optString("filename"));
                    String sizeStr = m.optString("size", "Unknown");
                    info.setSize(sizeStr);
                    info.setByteSize(parseSize(sizeStr));
                    models.add(info);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveToStorage() {
        try {
            JSONArray array = new JSONArray();
            for (ModelInfo m : models) {
                JSONObject jo = new JSONObject();
                jo.put("type", m.getType());
                jo.put("name", m.getName());
                jo.put("url", m.getUrl());
                jo.put("base", m.getBase());
                jo.put("save_path", m.getSave_path());
                jo.put("description", m.getDescription());
                jo.put("reference", m.getReference());
                jo.put("filename", m.getFilename());
                jo.put("size", m.getSize());
                array.put(jo);
            }
            File file = configService != null ? configService.getFileInAppData(STORAGE_FILE) : new File(STORAGE_FILE);
            Files.writeString(file.toPath(), array.toString(4), StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public List<ModelInfo> getModels() {
        return models;
    }
}
