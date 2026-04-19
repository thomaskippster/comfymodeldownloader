package de.tki.comfymodels;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class PngTest {
    public static void main(String[] args) {
        File file = new File("flux_canny_model_example.png");
        String workflow = extractWorkflowFromPng(file);
        if (workflow != null) {
            System.out.println("SUCCESS! Extracted workflow length: " + workflow.length());
            System.out.println("Snippet: " + workflow.substring(0, Math.min(200, workflow.length())));
        } else {
            System.out.println("FAILED! No workflow found.");
        }
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
                    String content = new String(data, StandardCharsets.UTF_8);
                    if (content.contains("workflow") || content.contains("prompt")) {
                        for (int i = 0; i < data.length; i++) {
                            if (data[i] == 0) {
                                return new String(data, i + 1, data.length - i - 1, StandardCharsets.UTF_8).trim();
                            }
                        }
                    }
                    dis.readInt(); // CRC
                } else if ("IEND".equals(type)) {
                    break;
                } else {
                    dis.skipBytes(length + 4);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
