package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.service.IComfyLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ComfyLifecycleService implements IComfyLifecycleService {

    @Autowired
    private ConfigService configService;

    private Process comfyProcess;
    private final AtomicReference<String> status = new AtomicReference<>("Stopped");
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();

    @Override
    public synchronized void start() {
        if (isRunning()) {
            status.set("Already Running");
            return;
        }

        String command = configService.getComfyLaunchCommand();
        String workingDir = configService.getComfyWorkingDir();

        if (command == null || command.trim().isEmpty()) {
            status.set("Error: No launch command configured");
            return;
        }

        try {
            status.set("Starting...");
            
            java.util.List<String> commandList = new java.util.ArrayList<>();
            if (de.tki.comfymodels.util.PlatformUtils.isWindows()) {
                commandList.add("cmd");
                commandList.add("/c");
                // Wrap the entire command in extra quotes for Windows cmd /c quirk
                commandList.add("\"" + command + "\"");
            } else {
                commandList.add("sh");
                commandList.add("-c");
                commandList.add(command);
            }

            System.out.println("🚀 [Lifecycle] Starting command: " + String.join(" ", commandList));
            if (workingDir != null) System.out.println("📁 [Lifecycle] In directory: " + workingDir);

            ProcessBuilder pb = new ProcessBuilder(commandList);
            if (workingDir != null && !workingDir.isEmpty()) {
                pb.directory(new File(workingDir));
            }
            pb.redirectErrorStream(true);
            comfyProcess = pb.start();

            // Quietly consume output to prevent process buffer stalls
            new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(comfyProcess.getInputStream()))) {
                    while (reader.readLine() != null) {
                        // Just consume, don't print
                    }
                } catch (java.io.IOException ignored) {}
            }).start();
            status.set("Running (PID: " + comfyProcess.pid() + ")");
            
            // Background thread to wait for exit
            new Thread(() -> {
                try {
                    int exitCode = comfyProcess.waitFor();
                    status.set("Stopped (Exit Code: " + exitCode + ")");
                    comfyProcess = null;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

        } catch (IOException e) {
            status.set("Error: " + e.getMessage());
        }
    }

    @Override
    public synchronized void stop() {
        status.set("Stopping...");
        
        // 1. Kill internal process if managed
        if (comfyProcess != null) {
            try {
                comfyProcess.descendants().forEach(ProcessHandle::destroyForcibly);
                comfyProcess.destroyForcibly();
                comfyProcess.waitFor();
            } catch (Exception ignored) {}
            comfyProcess = null;
        }

        // 2. Kill by port fallback (for external/orphaned instances)
        if (isHealthy()) {
            killProcessOnPort();
        }
        
        status.set("Stopped");
    }

    private void killProcessOnPort() {
        try {
            String url = configService.getComfyUIUrl();
            int port = 8000;
            try {
                port = Integer.parseInt(url.substring(url.lastIndexOf(":") + 1));
            } catch (Exception ignored) {}

            if (de.tki.comfymodels.util.PlatformUtils.isWindows()) {
                // Find PID on port and kill it
                Process p = Runtime.getRuntime().exec("cmd /c netstat -ano | findstr :" + port);
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                String line = reader.readLine();
                if (line != null) {
                    String[] parts = line.trim().split("\\s+");
                    long pid = Long.parseLong(parts[parts.length - 1]);
                    ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
                }
            } else {
                // Linux/Mac fallback
                Runtime.getRuntime().exec("sh -c fuser -k " + port + "/tcp");
            }
        } catch (Exception e) {
            System.err.println("Failed to kill process on port: " + e.getMessage());
        }
    }

    @Override
    public void restart() {
        stop();
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        
        // Reload extra paths in case they were changed before restart
        configService.loadExtraModelPaths();
        
        start();
        
        // Optional: Wait for health check in background
        new Thread(() -> {
            status.set("Restarting (Waiting for API...)");
            for (int i = 0; i < 30; i++) { // Wait up to 30 seconds
                if (isHealthy()) {
                    status.set("Running & Healthy");
                    return;
                }
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
            status.set("Running (Healthy Check Timeout)");
        }).start();
    }

    @Override
    public String getStatus() {
        String current = status.get();
        if (current.startsWith("Stopped") || current.startsWith("Error")) {
            if (isHealthy()) {
                return "Running (External Instance)";
            }
        }
        return current;
    }

    @Override
    public boolean isRunning() {
        return (comfyProcess != null && comfyProcess.isAlive()) || isHealthy();
    }

    @Override
    public boolean isHealthy() {
        String url = configService.getComfyUIUrl();
        if (url == null || url.isEmpty()) return false;
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/system_stats"))
                    .timeout(Duration.ofMillis(1000))
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() == 200;
            if (!ok) System.err.println("⚠️ [Lifecycle] Health check failed for " + url + " - Status: " + response.statusCode());
            return ok;
        } catch (Exception e) {
            // Silently fail for regular polling, but keep diagnostic info ready if needed
            return false;
        }
    }
}
