package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ModelSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExistingModelSizeTest {

    @TempDir
    Path tempDir;

    @Test
    public void testLocalSizeFormatting() throws IOException {
        ModelSearchService searchService = new ModelSearchService();
        
        File dummyFile = tempDir.resolve("test.safetensors").toFile();
        byte[] content = new byte[1024 * 1024 * 5]; // 5MB
        Files.write(dummyFile.toPath(), content);
        
        long size = dummyFile.length();
        String formatted = searchService.formatSize(size);
        
        assertEquals("5.00 MB", formatted);
    }
}
