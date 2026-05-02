package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ArchiveService;
import de.tki.comfymodels.service.impl.LocalModelScanner;
import de.tki.comfymodels.service.impl.RestBridgeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestBridgeServiceTest {

    private RestBridgeService restBridge;
    private LocalModelScanner scannerStub;
    private ArchiveService archiveStub;
    private final int testPort = 12347;
    private final String testToken = "test-token";

    private static class LocalModelScannerStub extends LocalModelScanner {
        public LocalModelScannerStub() { super(null); }
        @Override
        public java.util.List<ModelInfo> scanLocalModels() {
            return Collections.singletonList(new ModelInfo("checkpoints", "local_test.safetensors", "LOCAL"));
        }
    }

    private static class ArchiveServiceStub extends ArchiveService {
        public AtomicInteger callCount = new AtomicInteger(0);
        public ArchiveServiceStub() { super(null, null); }
        @Override
        public void moveToArchive(String path) throws IOException {
            callCount.incrementAndGet();
        }
    }

    @BeforeEach
    void setUp() {
        restBridge = new RestBridgeService();
        restBridge.setPort(testPort);
        restBridge.setApiToken(testToken);
        
        scannerStub = new LocalModelScannerStub();
        archiveStub = new ArchiveServiceStub();
        
        ReflectionTestUtils.setField(restBridge, "localScanner", scannerStub);
        ReflectionTestUtils.setField(restBridge, "archiveService", archiveStub);
        
        restBridge.startServer();
    }

    @AfterEach
    void tearDown() {
        restBridge.stopServer();
    }

    @Test
    void testGetLocalModels() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/api/models/local"))
                .header("Authorization", "Bearer " + testToken)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("local_test.safetensors"));
    }

    @Test
    void testBatchArchive() throws Exception {
        String jsonPaths = "[\"checkpoints/test1.safetensors\", \"loras/test2.safetensors\"]";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/api/models/archive"))
                .header("Authorization", "Bearer " + testToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPaths))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals(2, ((ArchiveServiceStub)archiveStub).callCount.get());
        assertTrue(response.body().contains("\"archived\": 2"));
    }
}
