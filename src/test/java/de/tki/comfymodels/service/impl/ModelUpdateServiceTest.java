package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IModelValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ModelUpdateServiceTest {

    private ModelUpdateService updateService;

    @Mock
    private ConfigService configService;
    
    @Mock
    private ModelHashRegistry hashRegistry;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        updateService = new ModelUpdateService(configService, hashRegistry);
    }

    @Test
    void testCheckForUpdateNoFile() {
        ModelInfo current = new ModelInfo("checkpoints", "missing.safetensors", "");
        when(configService.getModelsPath()).thenReturn("C:/models");
        
        Optional<ModelInfo> result = updateService.checkForUpdate(current);
        assertTrue(result.isEmpty());
    }
}
