package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PngExtractionTest {

    @TempDir
    Path tempDir;

    @Test
    public void testWorkflowExtractionFromSyntheticPng() throws IOException {
        File pngFile = tempDir.resolve("test.png").toFile();
        String expectedWorkflow = "{\"nodes\": [{\"type\": \"CheckpointLoaderSimple\"}]}";
        
        createTestPng(pngFile, expectedWorkflow);

        WorkflowService workflowService = new WorkflowService();
        String extracted = workflowService.extractWorkflow(pngFile);

        assertNotNull(extracted, "Workflow should be extracted from PNG");
        assertEquals(expectedWorkflow, extracted, "Extracted workflow should match the original");
    }

    private void createTestPng(File file, String workflow) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(file))) {
            // PNG Signature
            dos.writeLong(0x89504E470D0A1A0AL);

            // IHDR chunk (minimal)
            dos.writeInt(13);
            byte[] ihdrType = "IHDR".getBytes(StandardCharsets.US_ASCII);
            dos.write(ihdrType);
            dos.writeInt(1); // Width
            dos.writeInt(1); // Height
            dos.writeByte(8); // Bit depth
            dos.writeByte(2); // Color type
            dos.writeByte(0); // Compression
            dos.writeByte(0); // Filter
            dos.writeByte(0); // Interlace
            dos.writeInt(calculateCrc(ihdrType, new byte[]{0,0,0,1, 0,0,0,1, 8, 2, 0, 0, 0}));

            // tEXt chunk with workflow
            byte[] key = "workflow".getBytes(StandardCharsets.UTF_8);
            byte[] value = workflow.getBytes(StandardCharsets.UTF_8);
            byte[] textData = new byte[key.length + 1 + value.length];
            System.arraycopy(key, 0, textData, 0, key.length);
            textData[key.length] = 0;
            System.arraycopy(value, 0, textData, key.length + 1, value.length);

            dos.writeInt(textData.length);
            byte[] textType = "tEXt".getBytes(StandardCharsets.US_ASCII);
            dos.write(textType);
            dos.write(textData);
            dos.writeInt(calculateCrc(textType, textData));

            // IEND chunk
            dos.writeInt(0);
            byte[] iendType = "IEND".getBytes(StandardCharsets.US_ASCII);
            dos.write(iendType);
            dos.writeInt(calculateCrc(iendType, new byte[0]));
        }
    }

    private int calculateCrc(byte[] type, byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(data);
        return (int) crc.getValue();
    }
}
