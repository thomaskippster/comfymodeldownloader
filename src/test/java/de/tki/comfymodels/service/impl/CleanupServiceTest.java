package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.ICleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class CleanupServiceTest {

    private CleanupService cleanupService;

    @Mock
    private ConfigService configService;

    @Mock
    private ArchiveService archiveService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cleanupService = new CleanupService(configService, archiveService);
    }

    @Test
    void testFindUnusedModels() throws IOException {
        when(configService.getModelsPath()).thenReturn(tempDir.toString());

        Path oldModel = tempDir.resolve("old.safetensors");
        Files.createFile(oldModel);
        // Set last access time to 6 months ago
        FileTime sixMonthsAgo = FileTime.from(Instant.now().minus(180, ChronoUnit.DAYS));
        Files.setAttribute(oldModel, "lastAccessTime", sixMonthsAgo);

        Path newModel = tempDir.resolve("new.safetensors");
        Files.createFile(newModel);
        // Set last access time to now
        Files.setAttribute(newModel, "lastAccessTime", FileTime.from(Instant.now()));

        List<ModelInfo> unused = cleanupService.findUnusedModels(3);
        
        assertEquals(1, unused.size());
        assertEquals("old.safetensors", unused.get(0).getName());
    }
}
