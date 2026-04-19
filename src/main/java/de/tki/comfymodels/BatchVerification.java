package de.tki.comfymodels;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ComfyModelAnalyzer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.zip.InflaterInputStream;

public class BatchVerification {
    public static void main(String[] args) throws Exception {
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        Path root = Paths.get("C:/pinokio/api/comfy.git/workflows/ComfyUI_examples");
        
        System.out.println("=== BATCH VERIFICATION START ===");
        
        Files.walk(root)
            .filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".png"))
            .limit(30) // Stichprobe von 30 verschiedenen Workflows
            .forEach(path -> {
                try {
                    System.out.println("\nFILE: " + path.getFileName());
                    String content;
                    if (path.toString().endsWith(".png")) {
                        content = extractWorkflowFromPng(path.toFile());
                    } else {
                        content = Files.readString(path);
                    }

                    if (content == null) {
                        System.out.println("  [!] No workflow found in PNG");
                        return;
                    }

                    List<ModelInfo> results = analyzer.analyze(content, path.getFileName().toString());
                    if (results.isEmpty()) {
                        System.out.println("  [?] No models detected");
                    } else {
                        for (ModelInfo info : results) {
                            System.out.printf("  [%s] %s | URL: %s | Source: %s\n", 
                                info.getType(), info.getName(), 
                                info.getUrl().equals("MISSING") ? "Missing" : "FOUND",
                                info.getPopularity());
                        }
                    }
                } catch (Exception e) {
                    System.out.println("  [ERROR] " + e.getMessage());
                }
            });
        
        System.out.println("\n=== BATCH VERIFICATION END ===");
    }

    private static String extractWorkflowFromPng(File file) {
        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            if (dis.readLong() != 0x89504E470D0A1A0AL) return null;
            while (true) {
                int length = dis.readInt();
                byte[] typeBytes = new byte[4];
                dis.readFully(typeBytes);
                String type = new String(typeBytes, StandardCharsets.US_ASCII);
                if ("tEXt".equals(type) || "iTXt".equals(type) || "zTXt".equals(type)) {
                    byte[] data = new byte[length];
                    dis.readFully(data);
                    int nullPos = 0; while (nullPos < data.length && data[nullPos] != 0) nullPos++;
                    String key = new String(data, 0, nullPos, StandardCharsets.UTF_8);
                    String value = null;
                    if ("tEXt".equals(type)) value = new String(data, nullPos + 1, data.length - (nullPos + 1), StandardCharsets.UTF_8);
                    else if ("zTXt".equals(type)) {
                        try (InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(data, nullPos + 2, data.length - (nullPos + 2)))) { value = new String(iis.readAllBytes(), StandardCharsets.UTF_8); }
                    } else if ("iTXt".equals(type)) {
                        int currentPos = nullPos + 3; int nullCount = 0;
                        while (currentPos < data.length && nullCount < 2) { if (data[currentPos] == 0) nullCount++; currentPos++; }
                        if (currentPos < data.length) {
                            if (data[nullPos + 1] != 0) {
                                try (InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(data, currentPos, data.length - currentPos))) { value = new String(iis.readAllBytes(), StandardCharsets.UTF_8); }
                            } else value = new String(data, currentPos, data.length - currentPos, StandardCharsets.UTF_8);
                        }
                    }
                    if ("workflow".equalsIgnoreCase(key) || "prompt".equalsIgnoreCase(key)) return value;
                    dis.readInt();
                } else if ("IEND".equals(type)) break; else dis.skipBytes(length + 4);
            }
        } catch (Exception e) {}
        return null;
    }
}
