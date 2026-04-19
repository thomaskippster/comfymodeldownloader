package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.ModelInfo;
import org.json.JSONArray;
import org.json.JSONObject;
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

    @PostConstruct
    public void init() {
        loadFromStorage();
    }

    public void importJson(File file) throws Exception {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(content);
        JSONArray modelsArray = root.getJSONArray("models");
        
        List<ModelInfo> newModels = new ArrayList<>();
        for (int i = 0; i < modelsArray.length(); i++) {
            JSONObject m = modelsArray.getJSONObject(i);
            ModelInfo info = new ModelInfo(
                m.optString("type", "Unknown"),
                m.optString("name", "Unknown"),
                m.optString("url", "MISSING")
            );
            info.setBase(m.optString("base", ""));
            info.setSave_path(m.optString("save_path", ""));
            info.setDescription(m.optString("description", ""));
            info.setReference(m.optString("reference", ""));
            info.setFilename(m.optString("filename", ""));
            info.setSize(m.optString("size", "Unknown"));
            newModels.add(info);
        }
        
        this.models = newModels;
        saveToStorage();
    }

    public Optional<ModelInfo> findByFilename(String filename) {
        return models.stream()
                .filter(m -> filename.equalsIgnoreCase(m.getFilename()))
                .findFirst();
    }

    private void loadFromStorage() {
        try {
            File file = new File(STORAGE_FILE);
            if (file.exists()) {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                JSONArray array = new JSONArray(content);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject m = array.getJSONObject(i);
                    ModelInfo info = new ModelInfo(
                        m.optString("type"),
                        m.optString("name"),
                        m.optString("url")
                    );
                    info.setBase(m.optString("base"));
                    info.setSave_path(m.optString("save_path"));
                    info.setDescription(m.optString("description"));
                    info.setReference(m.optString("reference"));
                    info.setFilename(m.optString("filename"));
                    info.setSize(m.optString("size"));
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
            Files.writeString(new File(STORAGE_FILE).toPath(), array.toString(4), StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public List<ModelInfo> getModels() {
        return models;
    }
}
