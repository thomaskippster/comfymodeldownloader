package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.service.IWorkflowService;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.InflaterInputStream;

@Service
public class WorkflowService implements IWorkflowService {

    @Override
    public String extractWorkflow(File file) throws IOException {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png")) {
            return extractWorkflowFromPng(file);
        } else {
            return Files.readString(file.toPath());
        }
    }

    private String extractWorkflowFromPng(File file) {
        String workflow = null, prompt = null;
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
                        if (nullPos + 2 < data.length) {
                            try (InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(data, nullPos + 2, data.length - (nullPos + 2)))) { value = new String(iis.readAllBytes(), StandardCharsets.UTF_8); }
                        }
                    } else if ("iTXt".equals(type)) {
                        int currentPos = nullPos + 3; int nullCount = 0;
                        while (currentPos < data.length && nullCount < 2) { if (data[currentPos] == 0) nullCount++; currentPos++; }
                        if (currentPos < data.length) {
                            if (data[nullPos + 1] != 0) {
                                try (InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(data, currentPos, data.length - currentPos))) { value = new String(iis.readAllBytes(), StandardCharsets.UTF_8); }
                            } else value = new String(data, currentPos, data.length - currentPos, StandardCharsets.UTF_8);
                        }
                    }
                    if (value != null && !value.isEmpty()) {
                        if (key.equalsIgnoreCase("workflow")) workflow = value;
                        else if (key.equalsIgnoreCase("prompt")) prompt = value;
                        else if (key.toLowerCase().contains("comfy") && workflow == null) workflow = value;
                    }
                    dis.readInt();
                } else if ("IEND".equals(type)) break; else dis.skipBytes(length + 4);
            }
        } catch (Exception e) {
            // Log error or rethrow as needed
        }
        if (workflow == null && prompt != null && prompt.trim().startsWith("{")) {
            try {
                JSONObject jo = new JSONObject(prompt);
                if (jo.has("workflow")) return jo.get("workflow").toString();
            } catch (Exception ignored) {}
        }
        return workflow != null ? workflow : prompt;
    }
}
