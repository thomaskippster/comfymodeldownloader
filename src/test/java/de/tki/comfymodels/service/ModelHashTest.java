package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ModelValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ModelHashTest {

    @TempDir
    Path tempDir;

    @Test
    public void testCalculateSHA256() throws Exception {
        ModelValidator validator = new ModelValidator();
        Path testFile = tempDir.resolve("test_model.bin");
        byte[] data = "ComfyUI Model Data for Hashing Test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(testFile, data);

        String hash = validator.calculateHash(testFile.toFile());
        
        assertNotNull(hash);
        assertEquals(64, hash.length()); 
        
        // echo -n "ComfyUI Model Data for Hashing Test" | sha256sum
        String expectedHash = "5022ad6b25eac3358b18aa8ef8ee79d06833c43bed545bed1a5d45057d3054e2";
        assertEquals(expectedHash, hash);
    }

    @Test
    public void testEmptyFileHash() throws Exception {
        ModelValidator validator = new ModelValidator();
        Path emptyFile = tempDir.resolve("empty.bin");
        Files.createFile(emptyFile);

        String hash = validator.calculateHash(emptyFile.toFile());
        
        // SHA-256 for empty string
        String expectedEmptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        assertEquals(expectedEmptyHash, hash);
    }
}
