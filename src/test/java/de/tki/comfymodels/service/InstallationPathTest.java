package de.tki.comfymodels.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InstallationPathTest {

    @TempDir
    Path tempDir;

    @Test
    void testStandardLayout() throws IOException {
        Path customNodes = tempDir.resolve("custom_nodes");
        Files.createDirectory(customNodes);
        
        File inputPath = tempDir.toFile();
        File customNodesDir = findCustomNodes(inputPath);
        
        assertNotNull(customNodesDir);
        assertEquals(customNodes.toAbsolutePath().toString(), customNodesDir.getAbsolutePath());
    }

    @Test
    void testPortableLayout() throws IOException {
        Path comfyDir = tempDir.resolve("ComfyUI");
        Files.createDirectory(comfyDir);
        Path customNodes = comfyDir.resolve("custom_nodes");
        Files.createDirectory(customNodes);
        
        File inputPath = tempDir.toFile();
        File customNodesDir = findCustomNodes(inputPath);
        
        assertNotNull(customNodesDir);
        assertEquals(customNodes.toAbsolutePath().toString(), customNodesDir.getAbsolutePath());
    }

    @Test
    void testDesktopAppLayout() throws IOException {
        Path resources = tempDir.resolve("resources");
        Files.createDirectory(resources);
        Path comfyDir = resources.resolve("ComfyUI");
        Files.createDirectory(comfyDir);
        Path customNodes = comfyDir.resolve("custom_nodes");
        Files.createDirectory(customNodes);
        
        File inputPath = tempDir.toFile();
        File customNodesDir = findCustomNodes(inputPath);
        
        assertNotNull(customNodesDir);
        assertEquals(customNodes.toAbsolutePath().toString(), customNodesDir.getAbsolutePath());
    }

    @Test
    void testDirectSelection() throws IOException {
        Path customNodes = tempDir.resolve("custom_nodes");
        Files.createDirectory(customNodes);
        
        File inputPath = customNodes.toFile();
        File customNodesDir = findCustomNodes(inputPath);
        
        assertNotNull(customNodesDir);
        assertEquals(customNodes.toAbsolutePath().toString(), customNodesDir.getAbsolutePath());
    }

    /**
     * Logic extracted from Main.java for testing
     */
    private File findCustomNodes(File inputPath) {
        File c1 = new File(inputPath, "custom_nodes");
        File c2 = new File(inputPath, "ComfyUI" + File.separator + "custom_nodes");
        File c3 = new File(inputPath, "resources" + File.separator + "ComfyUI" + File.separator + "custom_nodes");
        File c4 = inputPath;

        if (c1.exists() && c1.isDirectory()) return c1;
        if (c2.exists() && c2.isDirectory()) return c2;
        if (c3.exists() && c3.isDirectory()) return c3;
        if (c4.getName().equalsIgnoreCase("custom_nodes") && c4.isDirectory()) return c4;
        
        return null;
    }
}
