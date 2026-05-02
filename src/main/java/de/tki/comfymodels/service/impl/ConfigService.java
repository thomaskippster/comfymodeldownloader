package de.tki.comfymodels.service.impl;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class ConfigService {
    private final String CONFIG_FILE = "app_settings.json";
    private final String VAULT_FILE = "settings.vault";
    private JSONObject settings = new JSONObject();
    private String masterPassword = null;
    private boolean vaultFresh = false;

    private final EncryptionUtils encryptionUtils;
    private final PathResolver pathResolver;

    @Autowired
    public ConfigService(EncryptionUtils encryptionUtils, PathResolver pathResolver) {
        this.encryptionUtils = encryptionUtils;
        this.pathResolver = pathResolver;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        pathResolver.setComfyUIRoot(getComfyUIPath());
    }

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
                String decrypted = encryptionUtils.decrypt(encrypted, password);
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
                this.vaultFresh = true;
                save();
            }
        }
    }

    public boolean isVaultFresh() {
        return vaultFresh;
    }

    public void save() {
        if (masterPassword == null) {
            System.err.println("Cannot save: Vault not unlocked.");
            return;
        }
        try {
            String encrypted = encryptionUtils.encrypt(settings.toString(), masterPassword);
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

    public String getModelsPath() { 
        String path = settings.optString("models_path", PathResolver.MODELS_DIR);
        return pathResolver.resolveModelsPath(path).toString();
    }
    public void setModelsPath(String path) { settings.put("models_path", path); save(); }

    public String getArchivePath() { 
        String path = settings.optString("archive_path", PathResolver.ARCHIVE_DIR);
        Path modelsPath = Paths.get(getModelsPath());
        return pathResolver.resolveArchivePath(modelsPath, path).toString();
    }
    public void setArchivePath(String path) { settings.put("archive_path", path); save(); }

    public boolean isBackgroundModeEnabled() { return settings.optBoolean("background_mode", false); }
    public void setBackgroundModeEnabled(boolean enabled) { settings.put("background_mode", enabled); save(); }

    public boolean isShutdownAfterDownloadEnabled() { return settings.optBoolean("shutdown_after_download", false); }
    public void setShutdownAfterDownloadEnabled(boolean enabled) { settings.put("shutdown_after_download", enabled); save(); }

    public boolean isDarkMode() { return settings.optBoolean("dark_mode", false); }
    public void setDarkMode(boolean enabled) { settings.put("dark_mode", enabled); save(); }

    public void savePendingDownloads(String json) {
        try {
            Files.writeString(getFileInAppData("pending_downloads.json").toPath(), json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Error saving pending downloads: " + e.getMessage());
        }
    }

    public String loadPendingDownloads() {
        try {
            File file = getFileInAppData("pending_downloads.json");
            if (file.exists()) {
                return Files.readString(file.toPath(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            System.err.println("Error loading pending downloads: " + e.getMessage());
        }
        return null;
    }

    public String getComfyUIPath() { return settings.optString("comfyui_path", ""); }
    public void setComfyUIPath(String path) { 
        settings.put("comfyui_path", path); 
        pathResolver.setComfyUIRoot(path);
        save(); 
    }

    public String getApiToken() {
        String token = settings.optString("api_token", "");
        if (token.isEmpty() && isUnlocked()) {
            token = java.util.UUID.randomUUID().toString().replace("-", "");
            settings.put("api_token", token);
            save();
        }
        return token;
    }

    public boolean isUnlocked() {
        return masterPassword != null;
    }

    public boolean hasVault() {
        return getFileInAppData(VAULT_FILE).exists();
    }

    public void resetVault() {
        File vault = getFileInAppData(VAULT_FILE);
        if (vault.exists()) {
            vault.delete();
        }
        this.settings = new JSONObject();
        this.masterPassword = null;
        this.vaultFresh = true;
        System.out.println("Vault has been reset.");
    }
}
