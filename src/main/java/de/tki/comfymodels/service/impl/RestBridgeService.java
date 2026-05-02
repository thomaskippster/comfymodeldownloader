package de.tki.comfymodels.service.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.tki.comfymodels.domain.ModelInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Service
public class RestBridgeService {

    @Autowired
    private LocalModelScanner localScanner;

    @Autowired
    private ArchiveService archiveService;

    @Autowired
    private de.tki.comfymodels.service.IDownloadManager downloadManager;

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
            server.createContext("/api/models/local", new LocalModelsHandler());
            server.createContext("/api/models/archive", new ArchiveHandler());
            server.createContext("/api/status", new StatusHandler());
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

    private boolean handleSecurity(HttpExchange exchange, String method) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return false;
        }

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (expectedApiToken != null && !expectedApiToken.isEmpty()) {
            String expected = "Bearer " + expectedApiToken.trim();
            if (authHeader == null || !authHeader.trim().equals(expected)) {
                System.err.println("REST Bridge: 401 Unauthorized request from " + exchange.getRemoteAddress());
                if (authHeader == null) {
                    System.err.println("  Reason: Missing Authorization header");
                } else {
                    // Log only start/end for security
                    String received = authHeader.trim();
                    System.err.println("  Reason: Token mismatch.");
                    System.err.println("  Expected start: " + expected.substring(0, Math.min(expected.length(), 15)) + "...");
                    System.err.println("  Received start: " + received.substring(0, Math.min(received.length(), 15)) + "...");
                }
                exchange.sendResponseHeaders(401, -1);
                return false;
            }
        }

        if (!method.equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return false;
        }
        return true;
    }

    private void sendJsonResponse(HttpExchange exchange, int code, String json) throws IOException {
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!handleSecurity(exchange, "GET")) return;
            try {
                java.util.Map<Integer, String> status = downloadManager.getQueueStatus();
                JSONObject jo = new JSONObject();
                JSONObject statuses = new JSONObject();
                for (java.util.Map.Entry<Integer, String> entry : status.entrySet()) {
                    statuses.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                jo.put("queue", statuses);
                jo.put("paused", downloadManager.isPaused());
                sendJsonResponse(exchange, 200, jo.toString());
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\": \"" + e.getMessage() + "\"}");
            } finally {
                exchange.close();
            }
        }
    }

    private class LocalModelsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!handleSecurity(exchange, "GET")) return;
            try {
                java.util.List<ModelInfo> localModels = localScanner.scanLocalModels();
                JSONArray ja = new JSONArray();
                for (ModelInfo info : localModels) {
                    JSONObject jo = new JSONObject();
                    jo.put("name", info.getName());
                    jo.put("type", info.getType());
                    jo.put("size", info.getSize());
                    jo.put("save_path", info.getSave_path());
                    ja.put(jo);
                }
                sendJsonResponse(exchange, 200, ja.toString());
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\": \"" + e.getMessage() + "\"}");
            } finally {
                exchange.close();
            }
        }
    }

    private class ArchiveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!handleSecurity(exchange, "POST")) return;
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JSONArray paths = new JSONArray(body);
                int successCount = 0;
                for (int i = 0; i < paths.length(); i++) {
                    try {
                        archiveService.moveToArchive(paths.getString(i));
                        successCount++;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                sendJsonResponse(exchange, 200, "{\"status\": \"success\", \"archived\": " + successCount + "}");
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\": \"" + e.getMessage() + "\"}");
            } finally {
                exchange.close();
            }
        }
    }

    private class ImportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!handleSecurity(exchange, "POST")) return;
                try (InputStream is = exchange.getRequestBody()) {
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    if (workflowConsumer != null) {
                        workflowConsumer.accept(body);
                        sendJsonResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Workflow received\"}");
                    } else {
                        sendJsonResponse(exchange, 503, "{\"status\": \"error\", \"message\": \"App not ready\"}");
                    }
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}");
            } finally {
                exchange.close();
            }
        }
    }
}
