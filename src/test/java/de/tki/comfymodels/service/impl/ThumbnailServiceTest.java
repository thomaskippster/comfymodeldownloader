package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.ModelInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ThumbnailServiceTest {

    private ThumbnailService thumbnailService;

    @Mock
    private ConfigService configService;

    @Mock
    private ModelHashRegistry hashRegistry;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        thumbnailService = new ThumbnailService(configService, hashRegistry);
    }

    @Test
    void testDownloadThumbnailNoHash() throws InterruptedException {
        ModelInfo model = new ModelInfo("checkpoints", "test.safetensors", "");
        when(configService.getModelsPath()).thenReturn(tempDir.toString());
        
        AtomicBoolean finished = new AtomicBoolean(false);
        thumbnailService.downloadThumbnail(model, result -> {
            assertFalse(result);
            finished.set(true);
        });
        
        // Wait a bit for async
        Thread.sleep(200);
        assertTrue(finished.get());
    }
}
