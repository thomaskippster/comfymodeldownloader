package de.tki.comfymodels;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.ArchiveService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MissingSizeReproductionTest {

    @TempDir
    Path tempDir;

    @Test
    public void reproduceSizeMismatchIssue() throws IOException {
        // Mock ConfigService
        ConfigService configService = mock(ConfigService.class);
        when(configService.getModelsPath()).thenReturn(tempDir.toString());
        
        // Mock ArchiveService
        ArchiveService archiveService = mock(ArchiveService.class);
        when(archiveService.normalizeFolder(anyString())).thenAnswer(i -> i.getArguments()[0]);

        // Scenario: Model metadata says 19.6GB, but local file is 850MB
        long metadataByteSize = (long) (19.6 * 1024 * 1024 * 1024);
        long actualByteSize = 849608296L;
        
        String filename = "Qwen-Image-Edit-2509-Lightning-4steps-V1.0-bf16.safetensors";
        String savePath = "loras/qwen-image-edit-lightning";
        
        ModelInfo info = new ModelInfo("loras", filename, "http://someurl");
        info.setByteSize(metadataByteSize);
        info.setSize("19.6GB");
        info.setSave_path(savePath);

        // Create the local file with "wrong" (actual) size
        Path targetDir = tempDir.resolve(savePath);
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve(filename);
        byte[] dummyData = new byte[1024]; // Just a small file
        Files.write(targetFile, dummyData);
        // Force set size if possible? No, we just use a small file to simulate "not matching"
        
        // Logic from Main.analyzeJsonContent()
        Path local = targetFile;
        boolean exists = Files.exists(local) && Files.isRegularFile(local);
        
        if (exists && info.getByteSize() > 0) {
            long localSize = Files.size(local);
            if (localSize != info.getByteSize()) {
                exists = false;
            }
        }

        assertFalse(exists, "App should think it does not exist because size mismatch");
    }
}
