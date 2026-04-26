package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.ModelHashRegistry;
import de.tki.comfymodels.service.impl.ModelValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ModelHashRegistryTest {

    @TempDir
    Path tempDir;

    private ConfigService configService;
    private IModelValidator validator;
    private ModelHashRegistry registry;
    private String lastCalculatedHash = "hash123";
    private int calculationCount = 0;

    @BeforeEach
    public void setup() throws Exception {
        configService = new ConfigService(null) {
            @Override
            public File getFileInAppData(String filename) {
                return tempDir.resolve(filename).toFile();
            }
        };

        validator = new IModelValidator() {
            @Override
            public ValidationResult validateFile(File file) { return new ValidationResult(true, "OK", file.getAbsolutePath()); }
            @Override
            public String calculateHash(File file) {
                calculationCount++;
                return lastCalculatedHash;
            }
        };

        registry = new ModelHashRegistry();
        
        // Use reflection to set private fields since we aren't using @InjectMocks
        setField(registry, "configService", configService);
        setField(registry, "validator", validator);
        registry.load();
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    @Test
    public void testGetOrCalculateHash() throws Exception {
        Path file = tempDir.resolve("model.safetensors");
        Files.writeString(file, "content");
        File modelFile = file.toFile();
        
        lastCalculatedHash = "hash123";
        calculationCount = 0;

        // First call: should calculate
        String hash1 = registry.getOrCalculateHash(modelFile);
        assertEquals("hash123", hash1);
        assertEquals(1, calculationCount);

        // Second call: should use cache because size and timestamp are same
        String hash2 = registry.getOrCalculateHash(modelFile);
        assertEquals("hash123", hash2);
        assertEquals(1, calculationCount); 

        // Change content -> size stays same, but timestamp changes (usually)
        // To be sure for the test, we wait a bit or just assume size change
        Files.writeString(file, "content has changed now");
        File updatedFile = file.toFile();
        
        lastCalculatedHash = "new_hash";
        String hash3 = registry.getOrCalculateHash(updatedFile);
        assertEquals("new_hash", hash3);
        assertEquals(2, calculationCount); // Re-calculation triggered!
    }

    @Test
    public void testFindPathByHash() throws Exception {
        Path file = tempDir.resolve("duplicate.safetensors");
        Files.writeString(file, "duplicate content");
        File modelFile = file.toFile();
        
        lastCalculatedHash = "duplicate_hash";
        registry.getOrCalculateHash(modelFile);
        
        Optional<String> foundPath = registry.findPathByHash("duplicate_hash");
        assertTrue(foundPath.isPresent());
        assertEquals(modelFile.getAbsolutePath(), foundPath.get());
    }

    @Test
    public void testUnregister() throws Exception {
        Path file = tempDir.resolve("to_be_deleted.safetensors");
        Files.writeString(file, "soon gone");
        File modelFile = file.toFile();
        
        lastCalculatedHash = "temp_hash";
        registry.getOrCalculateHash(modelFile);
        assertTrue(registry.findPathByHash("temp_hash").isPresent());
        
        registry.unregister(modelFile);
        assertFalse(registry.findPathByHash("temp_hash").isPresent());
    }
}
