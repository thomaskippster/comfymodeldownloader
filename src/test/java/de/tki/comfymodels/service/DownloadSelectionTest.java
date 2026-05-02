package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.DefaultDownloadManager;
import de.tki.comfymodels.service.impl.EncryptionUtils;
import de.tki.comfymodels.service.impl.PathResolver;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DownloadSelectionTest {

    private final EncryptionUtils encryptionUtils = new EncryptionUtils();

    @Test
    public void testOnlySelectedItemsAreProcessed() throws InterruptedException {
        DefaultDownloadManager downloadManager = new DefaultDownloadManager();
        
        // Mocking ConfigService to avoid NPE
        ConfigService configService = new ConfigService(encryptionUtils, new PathResolver());
        ReflectionTestUtils.setField(downloadManager, "configService", configService);
        ReflectionTestUtils.setField(downloadManager, "pathResolver", new PathResolver());
        
        List<ModelInfo> models = new ArrayList<>();
        models.add(new ModelInfo("checkpoints", "model1.safetensors", "http://example.com/1"));
        models.add(new ModelInfo("checkpoints", "model2.safetensors", "http://example.com/2"));
        models.add(new ModelInfo("checkpoints", "model3.safetensors", "http://example.com/3"));

        // Only first and third are selected
        boolean[] selected = {true, false, true};
        
        ConcurrentHashMap<Integer, String> statusMap = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(1);

        downloadManager.startQueue(models, selected, "target/test_downloads", (idx, status) -> {
            statusMap.put(idx, status);
        }, () -> {
            latch.countDown();
        });

        // Wait for completion
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Download queue did not finish in time");

        // Model 2 MUST be skipped
        assertEquals("Skipped (Not Selected)", statusMap.get(1), "Model 2 should have been skipped");
        
        // Model 1 and 3 should have been attempted (likely Error because URL is fake, but NOT Skipped (Not Selected))
        assertTrue(statusMap.containsKey(0));
        assertTrue(!statusMap.get(0).equals("Skipped (Not Selected)"));
        assertTrue(statusMap.containsKey(2));
        assertTrue(!statusMap.get(2).equals("Skipped (Not Selected)"));
    }

    @Test
    public void testDynamicUncheckWhilePaused() throws InterruptedException {
        DefaultDownloadManager downloadManager = new DefaultDownloadManager();
        ConfigService configService = new ConfigService(encryptionUtils, new PathResolver());
        ReflectionTestUtils.setField(downloadManager, "configService", configService);
        ReflectionTestUtils.setField(downloadManager, "pathResolver", new PathResolver());
        
        List<ModelInfo> models = new ArrayList<>();
        // Use a dummy model
        models.add(new ModelInfo("checkpoints", "model1.safetensors", "http://invalid-url-for-test.com/file"));

        boolean[] selected = {true};
        ConcurrentHashMap<Integer, String> statusMap = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(1);

        // Start in PAUSED state
        downloadManager.togglePause();
        
        downloadManager.startQueue(models, selected, "target/test_downloads", (idx, status) -> {
            statusMap.put(idx, status);
        }, () -> {
            latch.countDown();
        });

        // Give it a moment to reach the pause loop in startQueue or downloadWithResume
        Thread.sleep(300);
        
        // At this point, it should be either "Idle" (not yet started) or "Paused"
        // But the important thing is: We uncheck it NOW while it's waiting
        downloadManager.updateSelection(new boolean[]{false});
        
        // Resume the manager
        downloadManager.togglePause();

        // Wait for completion (the thread should now see it's unchecked and finish)
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Download queue did not finish in time");

        // The status MUST indicate it was skipped or errored out (as long as it didn't finish)
        String finalStatus = statusMap.get(0);
        assertTrue(finalStatus != null && (finalStatus.contains("Skipped") || finalStatus.contains("ConnectException")), 
            "Expected 'Skipped' or 'ConnectException' status but got: " + finalStatus);
    }
}
