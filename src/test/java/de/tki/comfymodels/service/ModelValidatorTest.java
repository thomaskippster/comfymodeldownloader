package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ModelValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ModelValidatorTest {

    private ModelValidator validator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        validator = new ModelValidator();
    }

    @Test
    void testNonExistentFile() {
        File file = new File(tempDir.toFile(), "ghost.safetensors");
        IModelValidator.ValidationResult result = validator.validateFile(file);
        assertFalse(result.ok);
        assertTrue(result.message.contains("exist"));
    }

    @Test
    void testEmptyFile() throws IOException {
        File file = new File(tempDir.toFile(), "empty.safetensors");
        file.createNewFile();
        IModelValidator.ValidationResult result = validator.validateFile(file);
        assertFalse(result.ok);
        assertTrue(result.message.contains("Empty"));
    }

    @Test
    void testLfsStub() throws IOException {
        File file = new File(tempDir.toFile(), "stub.safetensors");
        String lfsContent = "version https://git-lfs.github.com/spec/v1\noid sha256:12345\nsize 1000";
        java.nio.file.Files.writeString(file.toPath(), lfsContent);
        
        IModelValidator.ValidationResult result = validator.validateFile(file);
        assertFalse(result.ok);
        assertTrue(result.message.contains("LFS Stub"));
    }

    @Test
    void testValidSafetensors() throws IOException {
        File file = new File(tempDir.toFile(), "valid.safetensors");
        String headerJson = "{\"__metadata__\":{\"format\":\"pt\"}}";
        byte[] headerBytes = headerJson.getBytes(StandardCharsets.UTF_8);
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            ByteBuffer buf = ByteBuffer.allocate(8);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.putLong(headerBytes.length);
            fos.write(buf.array());
            fos.write(headerBytes);
            fos.write(new byte[100]); // Fake data
        }

        IModelValidator.ValidationResult result = validator.validateFile(file);
        assertTrue(result.ok, "Should be valid: " + result.message);
    }

    @Test
    void testCorruptSafetensorsHeaderLength() throws IOException {
        File file = new File(tempDir.toFile(), "corrupt_len.safetensors");
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            ByteBuffer buf = ByteBuffer.allocate(8);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.putLong(1000000); // Invalid length (much larger than file)
            fos.write(buf.array());
            fos.write(new byte[10]);
        }

        IModelValidator.ValidationResult result = validator.validateFile(file);
        assertFalse(result.ok);
        assertTrue(result.message.contains("Invalid header length"));
    }

    @Test
    void testTruncatedSafetensors() throws IOException {
        File file = new File(tempDir.toFile(), "truncated.safetensors");
        // Header says we have 1000 bytes of data, but we only write 500
        String headerJson = "{\"tensor1\":{\"dtype\":\"F16\",\"shape\":[500],\"data_offsets\":[0,1000]}}";
        byte[] headerBytes = headerJson.getBytes(StandardCharsets.UTF_8);
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            ByteBuffer buf = ByteBuffer.allocate(8);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.putLong(headerBytes.length);
            fos.write(buf.array());
            fos.write(headerBytes);
            fos.write(new byte[500]); // Only half the data
        }

        IModelValidator.ValidationResult result = validator.validateFile(file);
        // Currently this passes, but it should fail
        assertFalse(result.ok, "Should detect truncation: " + result.message);
        assertTrue(result.message.contains("truncated") || result.message.contains("size"), "Message should mention size or truncation: " + result.message);
    }

    @Test
    void testSafetensorsAlignmentError() throws IOException {
        File file = new File(tempDir.toFile(), "alignment.safetensors");
        // Tensor says it's F16 (2 bytes), but length is 999
        String headerJson = "{\"tensor1\":{\"dtype\":\"F16\",\"shape\":[500],\"data_offsets\":[0,999]}}";
        byte[] headerBytes = headerJson.getBytes(StandardCharsets.UTF_8);
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            ByteBuffer buf = ByteBuffer.allocate(8);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.putLong(headerBytes.length);
            fos.write(buf.array());
            fos.write(headerBytes);
            fos.write(new byte[1000]); // File has enough data, but header definition is misaligned
        }

        IModelValidator.ValidationResult result = validator.validateFile(file);
        assertFalse(result.ok, "Should detect alignment error");
        assertTrue(result.message.contains("multiple of element size"), "Message should mention element size: " + result.message);
    }
}
