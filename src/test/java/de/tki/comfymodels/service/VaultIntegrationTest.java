package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.EncryptionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class VaultIntegrationTest {

    @TempDir
    Path tempDir;

    private final EncryptionUtils encryptionUtils = new EncryptionUtils();

    @Test
    public void testVaultSaveAndLoad() throws Exception {
        // Setup ConfigService with temp directory
        ConfigService configService = new ConfigService(encryptionUtils) {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }
        };

        String password = "1234";
        String testKey = "4711";

        // 1. Initial unlock (creates new vault)
        configService.unlock(password);
        
        // 2. Set key and save
        configService.setGeminiApiKey(testKey);
        configService.save();

        // 3. Create NEW instance to simulate restart
        ConfigService newConfigService = new ConfigService(encryptionUtils) {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }
        };

        // 4. Unlock with correct password
        newConfigService.unlock(password);
        
        // 5. Verify key and dark mode
        assertEquals(testKey, newConfigService.getGeminiApiKey(), "Key should be 4711 after loading");
        assertFalse(newConfigService.isDarkMode(), "Default should be false");
        
        newConfigService.setDarkMode(true);
        
        // 5a. Reload again to verify dark mode persistence
        ConfigService restartService = new ConfigService(encryptionUtils) {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }
        };
        restartService.unlock(password);
        assertTrue(restartService.isDarkMode(), "Dark mode should be true after reload");

        // 6. Verify wrong password fails
        ConfigService wrongPassService = new ConfigService(encryptionUtils) {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }
        };
        
        assertThrows(Exception.class, () -> {
            wrongPassService.unlock("wrong_password");
        }, "Unlock should fail with wrong password");
    }
}
