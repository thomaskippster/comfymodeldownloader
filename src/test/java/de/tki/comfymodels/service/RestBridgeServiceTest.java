package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.RestBridgeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestBridgeServiceTest {

    private RestBridgeService restBridge;
    private final int testPort = 12346;
    private final String testToken = "test-token";

    @BeforeEach
    void setUp() {
        restBridge = new RestBridgeService();
        restBridge.setPort(testPort);
        restBridge.setApiToken(testToken);
        restBridge.startServer();
    }

    @AfterEach
    void tearDown() {
        restBridge.stopServer();
    }

    @Test
    void testWorkflowImport() throws Exception {
        AtomicReference<String> receivedWorkflow = new AtomicReference<>();
        restBridge.setWorkflowConsumer(receivedWorkflow::set);

        String testJson = "{\"nodes\": []}";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/import"))
                .header("Authorization", "Bearer " + testToken)
                .POST(HttpRequest.BodyPublishers.ofString(testJson))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals(testJson, receivedWorkflow.get());
    }
}
