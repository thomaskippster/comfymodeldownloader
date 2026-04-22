package de.tki.comfymodels.service.impl;

import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Service
public class ConfigService {
    private static final String CONFIG_FILE = "app_settings.json";
    private static final String VAULT_FILE = "settings.vault";
    private JSONObject settings = new JSONObject();
    private String masterPassword = null;

    /**
     * Use the current working directory for application data.
     */
    public String getAppDataPath() {
        return System.getProperty("user.dir");
    }

    public File getFileInAppData(String filename) {
        return new File(getAppDataPath(), filename);
    }

    public void unlock(String password) throws Exception {
        File vault = getFileInAppData(VAULT_FILE);
        System.out.println("Attempting to unlock vault at: " + vault.getAbsolutePath());
        
        if (vault.exists()) {
            String encrypted = Files.readString(vault.toPath(), StandardCharsets.UTF_8);
            try {
                String decrypted = EncryptionUtils.decrypt(encrypted, password);
                JSONObject decryptedJson = new JSONObject(decrypted);
                this.settings = decryptedJson;
                this.masterPassword = password;
                System.out.println("Vault unlocked successfully. Keys found: " + settings.keySet());
            } catch (Exception e) {
                System.err.println("Failed to unlock vault: " + e.getMessage());
                throw new Exception("Wrong password or corrupted vault!");
            }
        } else {
            this.masterPassword = password;
            File oldFile = getFileInAppData(CONFIG_FILE);
            if (oldFile.exists()) {
                try {
                    String content = Files.readString(oldFile.toPath(), StandardCharsets.UTF_8);
                    this.settings = new JSONObject(content);
                    save();
                    oldFile.delete();
                    System.out.println("Migrated old settings to encrypted vault.");
                } catch (Exception e) {
                    System.err.println("Failed to migrate old settings: " + e.getMessage());
                }
            } else {
                System.out.println("No vault found, initialized new empty vault at: " + vault.getAbsolutePath());
                save();
            }
        }
    }

    public void save() {
        if (masterPassword == null) {
            System.err.println("Cannot save: Vault not unlocked.");
            return;
        }
        try {
            String encrypted = EncryptionUtils.encrypt(settings.toString(), masterPassword);
            Files.writeString(getFileInAppData(VAULT_FILE).toPath(), encrypted, StandardCharsets.UTF_8);
            System.out.println("Vault saved successfully to: " + getFileInAppData(VAULT_FILE).getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error saving vault: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getGeminiApiKey() { return settings.optString("gemini_api_key", ""); }
    public void setGeminiApiKey(String key) { settings.put("gemini_api_key", key); save(); }

    public String getHfToken() { return settings.optString("hf_token", ""); }
    public void setHfToken(String token) { settings.put("hf_token", token); save(); }

    public String getModelsPath() { return settings.optString("models_path", "C:\\pinokio\\api\\comfy.git\\app\\models"); }
    public void setModelsPath(String path) { settings.put("models_path", path); save(); }
    
    public boolean isUnlocked() {
        return masterPassword != null;
    }
}