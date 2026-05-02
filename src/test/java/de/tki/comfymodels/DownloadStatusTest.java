package de.tki.comfymodels.service;

import com.sun.net.httpserver.HttpServer;
import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.DefaultDownloadManager;
import de.tki.comfymodels.service.impl.EncryptionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DownloadStatusTest {

    private DefaultDownloadManager downloadManager;
    private HttpServer server;
    private String serverUrl;
    private Path tempDir;

    @BeforeEach
    public void setup() throws Exception {
        tempDir = Files.createTempDirectory("downloader_test");
        downloadManager = new DefaultDownloadManager();
        
        // Use real ConfigService and EncryptionUtils
        EncryptionUtils encryptionUtils = new EncryptionUtils();
        ConfigService configService = new ConfigService(encryptionUtils, new de.tki.comfymodels.service.impl.PathResolver());
        
        Field field = DefaultDownloadManager.class.getDeclaredField("configService");
        field.setAccessible(true);
        field.set(downloadManager, configService);

        Field pathField = DefaultDownloadManager.class.getDeclaredField("pathResolver");
        pathField.setAccessible(true);
        pathField.set(downloadManager, new de.tki.comfymodels.service.impl.PathResolver());

        // Setup Local Test Server
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/test.safetensors", exchange -> {
            byte[] data = new byte[1024 * 1024]; // 1MB Test Data
            exchange.getResponseHeaders().add("Content-Length", String.valueOf(data.length));
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                // Send data slowly so we can test pause/stop
                for (int i = 0; i < 10; i++) {
                    os.write(data, i * (data.length / 10), data.length / 10);
                    os.flush();
                    Thread.sleep(100); 
                }
            } catch (InterruptedException ignored) {}
        });
        server.start();
        serverUrl = "http://localhost:" + server.getAddress().getPort() + "/test.safetensors";
    }

    @AfterEach
    public void tearDown() throws IOException {
        server.stop(0);
        // Clean temp files
        Files.walk(tempDir).map(Path::toFile).forEach(File::delete);
    }

    @Test
    public void testFullStatusLifecycle() throws InterruptedException {
        List<String> capturedStatus = new CopyOnWriteArrayList<>();
        CountDownLatch finishedLatch = new CountDownLatch(1);
        
        ModelInfo info = new ModelInfo("checkpoints", "test_model.safetensors", serverUrl);
        List<ModelInfo> list = Collections.singletonList(info);
        boolean[] selected = {true};

        downloadManager.startQueue(list, selected, tempDir.toString(), 
            (idx, status) -> {
                capturedStatus.add(status);
                System.out.println("Status Update: " + status);
            }, 
            finishedLatch::countDown
        );

        // 1. Check for Start/Download
        Thread.sleep(300);
        assertTrue(capturedStatus.stream().anyMatch(s -> s.contains("Downloading")), "Should show Downloading status");

        // 2. Teste PAUSE
        System.out.println("--- Triggering PAUSE ---");
        downloadManager.togglePause();
        Thread.sleep(500);
        assertTrue(capturedStatus.contains("Paused"), "Should show Paused status");

        // 3. Teste RESUME
        System.out.println("--- Triggering RESUME ---");
        downloadManager.togglePause();
        Thread.sleep(500);
        assertTrue(capturedStatus.contains("Resuming..."), "Should show Resuming status");

        // 4. Teste STOP
        System.out.println("--- Triggering STOP ---");
        downloadManager.stop();
        
        finishedLatch.await(5, TimeUnit.SECONDS);
        // Accept both "Stopped" or "Error: closed" (which is the result of closing the stream during stop)
        assertTrue(capturedStatus.stream().anyMatch(s -> s.contains("Stopped") || s.contains("Error: closed")), 
            "Should show Stopped or closed status after stop(), but got: " + capturedStatus);
    }
}
