package com.invoker4k.sc2fla.utils;

import com.invoker4k.sc2fla.Utils;

import java.io.File;

public class PlatformUtils {
    private static String osName;
    private static boolean isWindows;
    private static boolean isLinux;
    private static boolean isMac;
    private static boolean hasWine = false;

    public static void detectPlatform() {
        osName = System.getProperty("os.name").toLowerCase();
        isWindows = osName.contains("win");
        isLinux = osName.contains("nix") || osName.contains("nux") || osName.contains("aix");
        isMac = osName.contains("mac");

        if (isLinux) {
            hasWine = checkWineInstalled();
        }

        Utils.info("Platform: " + osName + (isLinux ? " (Wine: " + (hasWine ? "available" : "not found") + ")" : ""));
    }

    public static boolean isWindows() { return isWindows; }
    public static boolean isLinux() { return isLinux; }
    public static boolean isMac() { return isMac; }
    public static boolean hasWine() { return hasWine; }

    private static boolean checkWineInstalled() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"which", "wine"});
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static String findExecutable(String name) {
        String envPath = System.getenv(name.toUpperCase() + "_PATH");
        if (envPath != null) {
            File tool = new File(envPath);
            if (tool.exists() && tool.isFile()) {
                return tool.getAbsolutePath();
            }
        }

        try {
            String jarDir = new File(PlatformUtils.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParent();
            File tool = new File(jarDir, name);
            if (tool.exists()) {
                return tool.getAbsolutePath();
            }
        } catch (Exception ignored) {}

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                File tool = new File(dir, name);
                if (tool.exists()) {
                    return tool.getAbsolutePath();
                }
            }
        }

        return null;
    }

    public static String getSctxConverterCommand() {
        String exeName = "SctxConverter.exe";

        if (isWindows()) {
            String path = findExecutable(exeName);
            if (path != null) return path;
            return null;
        }

        if (isLinux()) {
            if (!hasWine()) {
                return null;
            }
            String path = findExecutable(exeName);
            if (path != null) {
                return "wine " + path;
            }
            return null;
        }

        return null;
    }

    public static String getPvrTexToolCommand() {
        String osName = System.getProperty("os.name").toLowerCase();
        String exeName = osName.contains("win") ? "PVRTexToolCLI.exe" : "PVRTexToolCLI";

        if (isWindows() || isLinux() || isMac()) {
            return findExecutable(exeName);
        }

        return null;
    }
}