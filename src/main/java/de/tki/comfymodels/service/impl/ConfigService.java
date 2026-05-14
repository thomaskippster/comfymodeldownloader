package de.tki.comfymodels.service.impl;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

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
        autoDiscoverPaths();
        pathResolver.setComfyUIRoot(getComfyUIPath());
        loadExtraModelPaths();
    }

    public void loadExtraModelPaths() {
        pathResolver.clearExtraModelPaths();
        String comfyRoot = getComfyUIPath();
        if (comfyRoot == null || comfyRoot.isEmpty()) return;

        Path extraPathsFile = Paths.get(comfyRoot).resolve("extra_model_paths.yaml");
        if (!Files.exists(extraPathsFile)) {
            // Check AppData common location as provided in user hint
            String userHome = System.getProperty("user.home");
            extraPathsFile = Paths.get(userHome, "AppData/Roaming/ComfyUI/extra_models_config.yaml");
        }

        if (Files.exists(extraPathsFile)) {
            System.out.println("📄 [Config] Loading extra model paths from: " + extraPathsFile.toAbsolutePath());
            try (InputStream is = new FileInputStream(extraPathsFile.toFile())) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(is);
                if (data != null) {
                    for (Map.Entry<String, Object> entry : data.entrySet()) {
                        if (entry.getValue() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> section = (Map<String, Object>) entry.getValue();
                            String basePathStr = (String) section.get("base_path");
                            if (basePathStr == null) continue;
                            
                            // base_path can be relative to the YAML file
                            Path yamlDir = extraPathsFile.getParent();
                            Path basePath = Paths.get(basePathStr);
                            if (!basePath.isAbsolute()) {
                                basePath = yamlDir.resolve(basePath).toAbsolutePath().normalize();
                            }
                            
                            System.out.println("📂 [Config] Section '" + entry.getKey() + "' base_path: " + basePath);

                            for (Map.Entry<String, Object> config : section.entrySet()) {
                                if (config.getKey().equals("base_path")) continue;
                                
                                String type = config.getKey();
                                Object value = config.getValue();
                                
                                if (value instanceof String) {
                                    String[] folders = ((String) value).split("\\R");
                                    for (String folder : folders) {
                                        String trimmed = folder.trim();
                                        if (!trimmed.isEmpty()) {
                                            Path fullPath = basePath.resolve(trimmed).toAbsolutePath().normalize();
                                            if (!Files.exists(fullPath)) {
                                                System.err.println("   ⚠️ [Config] Warning: Path does not exist: " + fullPath);
                                            }
                                            pathResolver.addExtraModelPath(type, fullPath);
                                            System.out.println("   -> Mapping [" + type + "] to: " + fullPath);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ [Config] Error parsing extra_model_paths.yaml: " + e.getMessage());
            }
        }
    }

    public void autoDiscoverPaths() {
        String root = getComfyUIPath();
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");

        System.out.println("🔍 [Config] Starting auto-discovery. Current Root: " + root);

        // 1. VALIDATE AND DISCOVER COMFYUI ROOT
        boolean rootValid = !root.isEmpty() && new File(root, "main.py").exists();
        
        if (!rootValid && !root.isEmpty()) {
            System.out.println("⚠️ [Config] main.py not found in current root. Checking subdirectories...");
            // Check for nested Pinokio structure: resources/ComfyUI
            File nested = new File(root, "resources/ComfyUI");
            if (new File(nested, "main.py").exists()) {
                root = nested.getAbsolutePath();
                rootValid = true;
                System.out.println("✨ [Config] Found main.py in nested Pinokio path: " + root);
                setComfyUIPath(root);
            } else {
                // Current root is definitively wrong, reset it to allow re-discovery
                System.out.println("🚫 [Config] Current root is invalid. Resetting for re-discovery.");
                root = "";
            }
        }

        if (root.isEmpty()) {
            // Priority 1: Environment Variables
            String[] envVars = {"COMFYUI_PATH", "COMFYUI_HOME", "PINOKIO_BIN"};
            for (String var : envVars) {
                String val = System.getenv(var);
                if (val != null && !val.isEmpty()) {
                    File f = new File(val);
                    if (new File(f, "main.py").exists()) {
                        root = f.getAbsolutePath();
                        System.out.println("✨ [Config] Found Root via Env Var " + var + ": " + root);
                        break;
                    }
                }
            }

            // Priority 2: Check if we are running INSIDE a ComfyUI directory
            if (root.isEmpty()) {
                File possibleRoot = findMainPyNearby(new File(currentDir));
                if (possibleRoot != null) {
                    root = possibleRoot.getAbsolutePath();
                    System.out.println("✨ [Config] Found Root via Nearby Search: " + root);
                }
            }
            
            // Priority 3: Check common Pinokio/Home locations
            if (root.isEmpty()) {
                String[] commonPatterns = {
                    "C:\\AI\\ComfyUI\\resources\\ComfyUI",
                    "C:\\AI\\ComfyUI",
                    userHome + "\\AppData\\Roaming\\pinokio\\bin\\ComfyUI",
                    userHome + "/pinokio/bin/ComfyUI",
                    userHome + "/ComfyUI",
                    userHome + "/AI/ComfyUI"
                };
                for (String p : commonPatterns) {
                    File f = new File(p);
                    if (new File(f, "main.py").exists()) {
                        root = f.getAbsolutePath();
                        System.out.println("✨ [Config] Found Root via Pattern: " + root);
                        break;
                    } else {
                        // Try nested resources/ComfyUI for patterns too
                        File nested = new File(f, "resources/ComfyUI");
                        if (new File(nested, "main.py").exists()) {
                            root = nested.getAbsolutePath();
                            System.out.println("✨ [Config] Found Root via Nested Pattern: " + root);
                            break;
                        }
                    }
                }
            }

            if (!root.isEmpty()) {
                setComfyUIPath(root);
            }
        }

        // 2. DISCOVER MODELS DIR
        String currentModelsPath = settings.optString("models_path", "");
        if (currentModelsPath.isEmpty() || currentModelsPath.equals(PathResolver.MODELS_DIR)) {
            // Check for Pinokio data structure: sibling of ComfyUI's main installation folder
            // e.g. C:\AI\ComfyUI (binary) -> C:\AI\comfyuidata\models (data)
            File comfyFolder = new File(root);
            while (comfyFolder != null) {
                File parent = comfyFolder.getParentFile();
                if (parent != null) {
                    File dataModels = new File(parent, "comfyuidata/models");
                    if (dataModels.exists() && dataModels.isDirectory()) {
                        System.out.println("✨ [Config] Found models via Pinokio Data Path: " + dataModels.getAbsolutePath());
                        setModelsPath(dataModels.getAbsolutePath());
                        break;
                    }
                    // Try case-insensitive variant or simplified name
                    File altData = new File(parent, "data/models");
                    if (altData.exists() && altData.isDirectory()) {
                        System.out.println("✨ [Config] Found models via Data Path: " + altData.getAbsolutePath());
                        setModelsPath(altData.getAbsolutePath());
                        break;
                    }
                }
                comfyFolder = comfyFolder.getParentFile();
                if (comfyFolder != null && comfyFolder.getName().equalsIgnoreCase("AI")) break; // Don't go too high
            }
        }

        // 3. DISCOVER WORKING DIR
        if ((getComfyWorkingDir().isEmpty() || !rootValid) && !root.isEmpty()) {
            setComfyWorkingDir(root);
            System.out.println("📁 [Config] Set Working Dir: " + root);
        }

        // 3. DISCOVER PYTHON & CONSTRUCT COMMAND
        // Always re-validate command if root changed or command is empty
        String python = getPythonPath();
        if (!isPythonValid(python)) {
            python = discoverPython(root);
            if (isPythonValid(python) && !python.equals("python") && !python.equals("python3")) {
                setPythonPath(python);
            }
        }

        if ((getComfyLaunchCommand().isEmpty() || !rootValid) && !root.isEmpty()) {
            File mainPy = new File(root, "main.py");
            
            if (mainPy.exists()) {
                int port = getComfyUIUrl().contains(":8000") ? 8000 : 8188;
                String cmd = String.format("\"%s\" \"%s\" --listen 127.0.0.1 --port %d --enable-manager", 
                    python, mainPy.getAbsolutePath(), port);
                setComfyLaunchCommand(cmd);
                System.out.println("🚀 [Config] Generated Launch Command: " + cmd);
            } else {
                System.err.println("❌ [Config] Could not find main.py in " + root);
            }
        } else if (!getComfyLaunchCommand().isEmpty()) {
            System.out.println("✅ [Config] Launch command already present and likely valid: " + getComfyLaunchCommand());
        }

        // 4. RELOAD EXTRA PATHS
        loadExtraModelPaths();
    }

    private boolean isPythonValid(String python) {
        if (python == null || python.isEmpty()) return false;
        if (python.equalsIgnoreCase("python") || python.equalsIgnoreCase("python3")) return true;
        return new File(python).exists();
    }

    private File findMainPyNearby(File start) {
        File current = start;
        while (current != null) {
            if (new File(current, "main.py").exists()) return current;
            if (current.getName().equalsIgnoreCase("custom_nodes")) {
                File parent = current.getParentFile();
                if (parent != null && new File(parent, "main.py").exists()) return parent;
            }
            current = current.getParentFile();
        }
        return null;
    }

    public String discoverPython(String comfyRoot) {
        System.out.println("🐍 [Config] Searching for Python in/near: " + comfyRoot);
        // Priority 1: Check for .venv in comfy root or parent (Pinokio style)
        File[] venvLocations = {
            new File(comfyRoot, ".venv"),
            new File(new File(comfyRoot).getParentFile(), ".venv"),
            new File(new File(comfyRoot).getParentFile().getParentFile(), ".venv"), // Try 2 levels up
            new File("C:\\AI\\comfyuidata\\.venv")
        };

        for (File venv : venvLocations) {
            if (venv != null && venv.exists()) {
                System.out.println("📂 [Config] Found venv at: " + venv.getAbsolutePath());
                boolean isWin = isWindows();
                File bin = new File(venv, isWin ? "Scripts/python.exe" : "bin/python3");
                if (bin.exists()) return bin.getAbsolutePath();
                bin = new File(venv, isWin ? "Scripts/python" : "bin/python");
                if (bin.exists()) return bin.getAbsolutePath();
            }
        }
        
        // Priority 2: Check for python_embeded (Portable ComfyUI style)
        File portablePython = new File(new File(comfyRoot).getParentFile(), "python_embeded/python.exe");
        if (portablePython.exists()) {
            System.out.println("📂 [Config] Found portable python at: " + portablePython.getAbsolutePath());
            return portablePython.getAbsolutePath();
        }

        System.out.println("⚠️ [Config] No venv found, falling back to system python.");
        return isWindows() ? "python" : "python3";
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
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
        loadExtraModelPaths(); // Ensure extra paths are reloaded when root changes
    }

    public String getPythonPath() { return settings.optString("python_path", ""); }
    public void setPythonPath(String path) { settings.put("python_path", path); save(); }

    public String getComfyLaunchCommand() { return settings.optString("comfy_launch_command", ""); }
    public void setComfyLaunchCommand(String cmd) { settings.put("comfy_launch_command", cmd); save(); }


    public String getComfyWorkingDir() { return settings.optString("comfy_working_dir", ""); }
    public void setComfyWorkingDir(String dir) { settings.put("comfy_working_dir", dir); save(); }

    public boolean isRestartAfterDownloadEnabled() { return settings.optBoolean("restart_after_download", false); }
    public void setRestartAfterDownloadEnabled(boolean enabled) { settings.put("restart_after_download", enabled); save(); }

    public String getComfyUIUrl() { return settings.optString("comfyui_url", "http://127.0.0.1:8000"); }
    public void setComfyUIUrl(String url) { 
        if (url != null && !url.isEmpty()) {
            String sanitized = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            settings.put("comfyui_url", sanitized); 
            save(); 
        }
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
