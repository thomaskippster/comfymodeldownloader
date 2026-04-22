package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class VaultIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    public void testVaultSaveAndLoad() throws Exception {
        // Setup ConfigService with temp directory
        ConfigService configService = new ConfigService() {
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
        ConfigService newConfigService = new ConfigService() {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }
        };

        // 4. Unlock with correct password
        newConfigService.unlock(password);
        
        // 5. Verify key
        assertEquals(testKey, newConfigService.getGeminiApiKey(), "Key should be 4711 after loading");

        // 6. Verify wrong password fails
        ConfigService wrongPassService = new ConfigService() {
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