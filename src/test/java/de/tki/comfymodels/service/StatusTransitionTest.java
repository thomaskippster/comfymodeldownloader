package de.tki.comfymodels.service;

import com.sun.net.httpserver.HttpServer;
import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.*;
import de.tki.comfymodels.service.impl.PathResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class StatusTransitionTest {

    private DefaultDownloadManager downloadManager;
    private HttpServer server;
    private String serverUrl;
    private Path tempDir;
    private ConfigService configService;
    private final EncryptionUtils encryptionUtils = new EncryptionUtils();

    @BeforeEach
    public void setup() throws Exception {
        tempDir = Files.createTempDirectory("transition_test");
        downloadManager = new DefaultDownloadManager();
        configService = new ConfigService(encryptionUtils, new PathResolver());
        ReflectionTestUtils.setField(downloadManager, "configService", configService);
        ReflectionTestUtils.setField(downloadManager, "pathResolver", new PathResolver());

        // Setup Local Test Server
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/test.safetensors", exchange -> {
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Content-Length", "10000");
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            byte[] data = new byte[10000];
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                for (int i = 0; i < 5; i++) {
                    os.write(data, i * 2000, 2000);
                    os.flush();
                    Thread.sleep(100); 
                }
            } catch (Exception ignored) {}
        });
        
        server.createContext("/auth-fail", exchange -> {
            exchange.sendResponseHeaders(401, -1);
        });

        server.start();
        serverUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    public void tearDown() throws IOException {
        server.stop(0);
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                 .sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(File::delete);
        }
    }

    @Test
    public void testTransition_Idle_To_Downloading_To_Finished() throws InterruptedException {
        List<String> statuses = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        ModelInfo info = new ModelInfo("checkpoints", "test.safetensors", serverUrl + "/test.safetensors");
        downloadManager.startQueue(Collections.singletonList(info), new boolean[]{true}, tempDir.toString(), 
            (idx, s) -> statuses.add(s), latch::countDown);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(statuses.stream().anyMatch(s -> s.contains("Downloading")), "Statuses: " + statuses);
        assertTrue(statuses.contains("✅ Finished"), "Statuses: " + statuses);
    }

    @Test
    public void testTransition_Idle_To_Searching_To_NotFound() throws InterruptedException {
        ModelSearchService searchService = new ModelSearchService();
        ConfigService cfg = new ConfigService(encryptionUtils, new PathResolver());
        GeminiAIService gemini = new GeminiAIService();
        ModelListService list = new ModelListService();
        
        ReflectionTestUtils.setField(searchService, "configService", cfg);
        ReflectionTestUtils.setField(searchService, "geminiService", gemini);
        ReflectionTestUtils.setField(searchService, "modelListService", list);
        ReflectionTestUtils.setField(gemini, "configService", cfg);
        
        List<String> statuses = new CopyOnWriteArrayList<>();
        ModelInfo info = new ModelInfo("checkpoints", "non_existent_model_xyz.safetensors", "MISSING");
        
        searchService.searchOnline(Collections.singletonList(info), new boolean[]{true}, "context", "file.json",
            (idx, s) -> statuses.add(s),
            (idx, found) -> {},
            () -> {}
        );

        // Wait a bit for search to progress (it will fail anyway as no API key is set)
        Thread.sleep(1500);
        assertTrue(statuses.stream().anyMatch(s -> s.contains("Searching") || s.contains("Scouting")), "Statuses: " + statuses);
    }

    @Test
    public void testTransition_Idle_To_SkippedNotSelected() throws InterruptedException {
        List<String> statuses = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        ModelInfo info = new ModelInfo("checkpoints", "skip_test.safetensors", serverUrl + "/test.safetensors");
        downloadManager.startQueue(Collections.singletonList(info), new boolean[]{false}, tempDir.toString(), 
            (idx, s) -> statuses.add(s), latch::countDown);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(statuses.contains("Skipped (Not Selected)"));
    }

    @Test
    public void testTransition_Downloading_To_Paused_To_Resuming() throws InterruptedException {
        List<String> statuses = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        ModelInfo info = new ModelInfo("checkpoints", "pause_test.safetensors", serverUrl + "/test.safetensors");
        downloadManager.startQueue(Collections.singletonList(info), new boolean[]{true}, tempDir.toString(), 
            (idx, s) -> statuses.add(s), latch::countDown);

        Thread.sleep(300);
        downloadManager.togglePause();
        Thread.sleep(500);
        assertTrue(statuses.contains("Paused"), "Statuses: " + statuses);

        downloadManager.togglePause(); // Resume
        Thread.sleep(500);
        assertTrue(statuses.contains("Resuming..."), "Statuses: " + statuses);
        
        downloadManager.stop();
        latch.await(2, TimeUnit.SECONDS);
    }

    @Test
    public void testTransition_Paused_To_SkippedUnchecked() throws InterruptedException {
        List<String> statuses = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        ModelInfo info = new ModelInfo("checkpoints", "uncheck_test.safetensors", serverUrl + "/test.safetensors");
        downloadManager.startQueue(Collections.singletonList(info), new boolean[]{true}, tempDir.toString(), 
            (idx, s) -> statuses.add(s), latch::countDown);

        Thread.sleep(300);
        downloadManager.togglePause();
        Thread.sleep(300);
        
        downloadManager.updateSelection(new boolean[]{false});
        downloadManager.togglePause(); // Resume triggers check

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(statuses.contains("Skipped (Unchecked)"), "Statuses: " + statuses);
    }

    @Test
    public void testTransition_Downloading_To_Stopped() throws InterruptedException {
        List<String> statuses = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        ModelInfo info = new ModelInfo("checkpoints", "stop_test.safetensors", serverUrl + "/test.safetensors");
        downloadManager.startQueue(Collections.singletonList(info), new boolean[]{true}, tempDir.toString(), 
            (idx, s) -> statuses.add(s), latch::countDown);

        Thread.sleep(300);
        downloadManager.stop();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(statuses.contains("Stopped"), "Statuses: " + statuses);
    }

    @Test
    public void testTransition_Resuming_To_AuthRequired() throws InterruptedException {
        List<String> statuses = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        ModelInfo info = new ModelInfo("checkpoints", "auth.safetensors", serverUrl + "/auth-fail");
        
        downloadManager.togglePause();
        downloadManager.startQueue(Collections.singletonList(info), new boolean[]{true}, tempDir.toString(), 
            (idx, s) -> statuses.add(s), latch::countDown);

        Thread.sleep(300);
        downloadManager.togglePause(); // Resume -> will hit 401

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(statuses.contains("❌ Auth Required (Token?)"), "Statuses: " + statuses);
    }

    @Test
    public void testTransition_Downloading_To_Error() throws InterruptedException {
        List<String> statuses = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        ModelInfo info = new ModelInfo("checkpoints", "error.safetensors", "http://localhost:1"); 
        downloadManager.startQueue(Collections.singletonList(info), new boolean[]{true}, tempDir.toString(), 
            (idx, s) -> statuses.add(s), latch::countDown);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(statuses.stream().anyMatch(s -> s.startsWith("Error:")), "Statuses: " + statuses);
    }
}
