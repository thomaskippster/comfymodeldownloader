package de.tki.comfymodels.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class NodeSyncServiceTest {

    private NodeSyncService nodeSyncService;

    @Mock
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        nodeSyncService = new NodeSyncService(configService);
    }

    @Test
    void testFindMissingNodesEmpty() {
        String workflow = "{}";
        Set<String> missing = nodeSyncService.findMissingNodes(workflow);
        assertTrue(missing.isEmpty());
    }

    @Test
    void testFindMissingNodesSample() {
        String workflow = "{\"1\": {\"class_type\": \"KSampler\"}, \"2\": {\"class_type\": \"CustomMissingNode\"}}";
        // KSampler is a built-in node, so it shouldn't be "missing" in terms of custom nodes 
        // if we have a list of built-ins.
        Set<String> missing = nodeSyncService.findMissingNodes(workflow);
        assertTrue(missing.contains("CustomMissingNode"));
        assertFalse(missing.contains("KSampler"));
    }
}
