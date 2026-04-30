package de.tki.comfymodels.service.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Service
public class RestBridgeService {

    private HttpServer server;
    private Consumer<String> workflowConsumer;
    private int port = 12345;
    private String expectedApiToken;

    public void setPort(int port) {
        this.port = port;
    }

    public void setApiToken(String token) {
        this.expectedApiToken = token;
    }

    public void setWorkflowConsumer(Consumer<String> consumer) {
        this.workflowConsumer = consumer;
    }

    public void startServer() {
        if (server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/import", new ImportHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("REST Bridge started on port " + port);
        } catch (IOException e) {
            System.err.println("Failed to start REST Bridge: " + e.getMessage());
        }
    }

    @PreDestroy
    public void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private class ImportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Add CORS headers to EVERY response
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS, GET");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

                // Handle preflight OPTIONS request
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                // SECURITY: Validate API Token
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (expectedApiToken != null && !expectedApiToken.isEmpty()) {
                    if (authHeader == null || !authHeader.equals("Bearer " + expectedApiToken)) {
                        System.err.println("REST Bridge: Unauthorized request (Invalid or Missing Token)");
                        exchange.sendResponseHeaders(401, -1);
                        return;
                    }
                }

                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                try (InputStream is = exchange.getRequestBody()) {
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    
                    if (workflowConsumer != null) {
                        workflowConsumer.accept(body);
                        
                        String response = "{\"status\": \"success\", \"message\": \"Workflow received\"}";
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(responseBytes);
                        }
                    } else {
                        String response = "{\"status\": \"error\", \"message\": \"App not ready\"}";
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(503, responseBytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(responseBytes);
                        }
                    }
                }
            } catch (Exception e) {
                String response = "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}";
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                try {
                    exchange.sendResponseHeaders(500, responseBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                    }
                } catch (IOException ignored) {}
            } finally {
                exchange.close();
            }
        }
    }
}
