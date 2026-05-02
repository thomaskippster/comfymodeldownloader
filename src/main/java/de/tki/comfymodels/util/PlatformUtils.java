package de.tki.comfymodels.util;

import java.io.IOException;

public class PlatformUtils {

    private static String OS_NAME = System.getProperty("os.name").toLowerCase();

    public static void setOsNameForTesting(String osName) {
        OS_NAME = osName.toLowerCase();
    }

    public static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    public static boolean isMac() {
        return OS_NAME.contains("mac");
    }

    public static boolean isLinux() {
        return OS_NAME.contains("nix") || OS_NAME.contains("nux") || OS_NAME.contains("aix");
    }

    public static void shutdownSystem() {
        String[] command = getShutdownCommand();
        if (command == null) {
            System.err.println("Shutdown not supported on this OS: " + OS_NAME);
            return;
        }

        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            System.err.println("Error executing shutdown command: " + e.getMessage());
        }
    }

    public static String[] getShutdownCommand() {
        if (isWindows()) {
            return new String[]{"shutdown", "/s", "/t", "60"};
        } else if (isMac()) {
            return new String[]{"osascript", "-e", "tell app \"System Events\" to shut down"};
        } else if (isLinux()) {
            return new String[]{"shutdown", "-h", "+1"};
        }
        return null;
    }

    public static boolean isSystemTraySupported() {
        try {
            return java.awt.SystemTray.isSupported();
        } catch (Exception e) {
            return false;
        }
    }
}
