package de.tki.comfymodels.service.impl;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class ComfyUIBridgeClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private String comfyUrl = "http://127.0.0.1:8188";

    public void setComfyUrl(String url) {
        if (url != null && !url.isEmpty()) {
            this.comfyUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
    }

    public void notifyModelDownloaded() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(comfyUrl + "/kippster/model-downloaded"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();

            System.out.println("Notifying ComfyUI at: " + request.uri());
            
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            System.out.println("ComfyUI notified successfully: " + response.body());
                        } else {
                            System.err.println("Failed to notify ComfyUI. Status: " + response.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        System.err.println("Could not reach ComfyUI for refresh: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            System.err.println("Error creating notification request: " + e.getMessage());
        }
    }
}
