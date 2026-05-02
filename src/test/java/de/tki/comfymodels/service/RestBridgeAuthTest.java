package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.RestBridgeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestBridgeAuthTest {

    private RestBridgeService restBridge;
    private final String TEST_TOKEN = "test-token-123";

    @BeforeEach
    public void setup() {
        restBridge = new RestBridgeService();
        restBridge.setPort(12346);
        restBridge.setApiToken(TEST_TOKEN);
        restBridge.startServer();
    }

    @AfterEach
    public void teardown() {
        restBridge.stopServer();
    }

    @Test
    public void testUnauthorizedRequest() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        
        // Request with WRONG token
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:12346/import"))
                .header("Authorization", "Bearer wrong-token")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(401, response.statusCode(), "Should return 401 for invalid token");
    }

    @Test
    public void testAuthorizedRequest() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        
        // Set a mock consumer to avoid NPE
        restBridge.setWorkflowConsumer(json -> {});

        // Request with CORRECT token
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:12346/import"))
                .header("Authorization", "Bearer " + TEST_TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode(), "Should return 200 for valid token");
    }

    @Test
    public void testAuthorizedRequestWithWhitespace() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        restBridge.setWorkflowConsumer(json -> {});

        // Request with CORRECT token but extra WHITESPACE
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:12346/import"))
                .header("Authorization", "  Bearer " + TEST_TOKEN + "  ")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode(), "Should return 200 even with whitespace in header");
    }
    
    @Test
    public void testMissingTokenRequest() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        
        // Request with NO token
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:12346/import"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(401, response.statusCode(), "Should return 401 when no token is provided");
    }
}
