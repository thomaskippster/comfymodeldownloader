package de.tki.comfymodels.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PlatformUtilsTest {

    @AfterEach
    void tearDown() {
        // Reset to real OS
        PlatformUtils.setOsNameForTesting(System.getProperty("os.name"));
    }

    @Test
    void testWindowsDetection() {
        PlatformUtils.setOsNameForTesting("Windows 11");
        assertTrue(PlatformUtils.isWindows());
        assertFalse(PlatformUtils.isMac());
        assertFalse(PlatformUtils.isLinux());
    }

    @Test
    void testMacDetection() {
        PlatformUtils.setOsNameForTesting("Mac OS X");
        assertTrue(PlatformUtils.isMac());
        assertFalse(PlatformUtils.isWindows());
        assertFalse(PlatformUtils.isLinux());
    }

    @Test
    void testLinuxDetection() {
        PlatformUtils.setOsNameForTesting("Linux");
        assertTrue(PlatformUtils.isLinux());
        assertFalse(PlatformUtils.isWindows());
        assertFalse(PlatformUtils.isMac());
    }

    @Test
    void testAixDetection() {
        PlatformUtils.setOsNameForTesting("AIX");
        assertTrue(PlatformUtils.isLinux());
        assertFalse(PlatformUtils.isWindows());
        assertFalse(PlatformUtils.isMac());
    }

    @Test
    void testWindowsShutdownCommand() {
        PlatformUtils.setOsNameForTesting("Windows 11");
        String[] cmd = PlatformUtils.getShutdownCommand();
        assertNotNull(cmd);
        assertEquals("shutdown", cmd[0]);
        assertEquals("/s", cmd[1]);
    }

    @Test
    void testMacShutdownCommand() {
        PlatformUtils.setOsNameForTesting("Mac OS X");
        String[] cmd = PlatformUtils.getShutdownCommand();
        assertNotNull(cmd);
        assertEquals("osascript", cmd[0]);
    }

    @Test
    void testLinuxShutdownCommand() {
        PlatformUtils.setOsNameForTesting("Linux");
        String[] cmd = PlatformUtils.getShutdownCommand();
        assertNotNull(cmd);
        assertEquals("shutdown", cmd[0]);
        assertEquals("-h", cmd[1]);
    }
}
