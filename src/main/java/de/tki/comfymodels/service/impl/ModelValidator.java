package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.service.IModelValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Service
public class ModelValidator implements IModelValidator {

    @Override
    public ValidationResult validateFile(File file) {
        String path = file.getAbsolutePath();
        
        if (!file.exists()) return new ValidationResult(false, "File does not exist", path);
        if (file.length() == 0) return new ValidationResult(false, "Empty file (0 bytes)", path);
        
        // Check for LFS Stubs (common when downloading from HF without proper LFS setup)
        if (file.length() < 1024) {
            try {
                String content = Files.readString(file.toPath());
                if (content.contains("version https://git-lfs.github.com/spec/v1")) {
                    return new ValidationResult(false, "Hugging Face LFS Stub (Not the actual model)", path);
                }
            } catch (Exception ignored) {}
        }

        String name = file.getName().toLowerCase();
        if (name.endsWith(".safetensors") || name.endsWith(".sft")) {
            return validateSafetensors(file);
        }

        // For other files, we just check if they are readable and have a sane size
        if (file.length() < 1024 * 1024 && !name.endsWith(".yaml") && !name.endsWith(".json")) {
            return new ValidationResult(false, "Suspiciously small for a model file", path);
        }

        return new ValidationResult(true, "OK", path);
    }

    @Override
    public String calculateHash(File file) {
        if (!file.exists() || !file.isFile()) return null;
        try (java.io.InputStream is = new java.io.BufferedInputStream(new java.io.FileInputStream(file), 1024 * 1024)) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private ValidationResult validateSafetensors(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 8) return new ValidationResult(false, "File too small to have a header", file.getAbsolutePath());
            
            byte[] headerLenBytes = new byte[8];
            raf.readFully(headerLenBytes);
            
            // Safetensors header length is a 64-bit little-endian unsigned integer
            ByteBuffer buffer = ByteBuffer.wrap(headerLenBytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            long headerLen = buffer.getLong();

            if (headerLen <= 0 || headerLen > raf.length() - 8) {
                return new ValidationResult(false, "Invalid header length: " + headerLen, file.getAbsolutePath());
            }

            // Optional: Validate that the header is valid JSON
            if (headerLen < 100 * 1024 * 1024) { // Don't try to read massive headers into memory
                byte[] headerBytes = new byte[(int) headerLen];
                raf.readFully(headerBytes);
                String headerJson = new String(headerBytes, StandardCharsets.UTF_8);
                try {
                    JSONObject json = new JSONObject(headerJson);
                    long maxOffset = 0;
                    
                    for (String key : json.keySet()) {
                        if (key.equals("__metadata__")) continue;
                        
                        JSONObject tensorInfo = json.optJSONObject(key);
                        if (tensorInfo != null && tensorInfo.has("data_offsets")) {
                            JSONArray offsets = tensorInfo.getJSONArray("data_offsets");
                            if (offsets.length() == 2) {
                                long start = offsets.getLong(0);
                                long end = offsets.getLong(1);
                                maxOffset = Math.max(maxOffset, end);
                                
                                // Check for alignment/element size issues
                                String dtype = tensorInfo.optString("dtype", "");
                                if (!dtype.isEmpty()) {
                                    int elementSize = getElementSize(dtype);
                                    if (elementSize > 1) {
                                        long length = end - start;
                                        if (length % elementSize != 0) {
                                            return new ValidationResult(false, 
                                                String.format("Tensor '%s' length (%d) is not a multiple of element size (%d) for dtype %s", 
                                                key, length, elementSize, dtype), file.getAbsolutePath());
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    long expectedSize = 8 + headerLen + maxOffset;
                    if (file.length() < expectedSize) {
                        return new ValidationResult(false, 
                            String.format("File is truncated. Expected at least %d bytes but found %d", expectedSize, file.length()), 
                            file.getAbsolutePath());
                    }
                    
                } catch (Exception e) {
                    return new ValidationResult(false, "Header is not valid JSON or has invalid structure: " + e.getMessage(), file.getAbsolutePath());
                }
            }

            return new ValidationResult(true, "OK", file.getAbsolutePath());
        } catch (Exception e) {
            return new ValidationResult(false, "Error reading file: " + e.getMessage(), file.getAbsolutePath());
        }
    }

    private int getElementSize(String dtype) {
        switch (dtype.toUpperCase()) {
            case "F64":
            case "I64":
            case "U64":
                return 8;
            case "F32":
            case "I32":
            case "U32":
                return 4;
            case "F16":
            case "BF16":
            case "I16":
            case "U16":
                return 2;
            case "I8":
            case "U8":
            case "BOOL":
                return 1;
            default:
                return 1; // Unknown, assume 1
        }
    }
}
