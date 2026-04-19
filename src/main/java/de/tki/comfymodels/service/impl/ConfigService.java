package de.tki.comfymodels.service.impl;

import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class ConfigService {
    private static final String CONFIG_FILE = "app_settings.json";
    private JSONObject settings;

    public ConfigService() {
        load();
    }

    private void load() {
        try {
            File file = new File(CONFIG_FILE);
            if (file.exists()) {
                settings = new JSONObject(Files.readString(file.toPath()));
            } else {
                settings = new JSONObject();
            }
        } catch (Exception e) {
            settings = new JSONObject();
        }
    }

    public void save() {
        try {
            Files.writeString(Paths.get(CONFIG_FILE), settings.toString(4));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getGeminiApiKey() { return settings.optString("gemini_api_key", ""); }
    public void setGeminiApiKey(String key) { settings.put("gemini_api_key", key); save(); }

    public String getHfToken() { return settings.optString("hf_token", ""); }
    public void setHfToken(String token) { settings.put("hf_token", token); save(); }
}
