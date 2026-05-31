package com.netresmanager.config;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Application configuration constants and utility methods.
 */
public final class AppConfig {

    private AppConfig() {
        // utility class
    }

    public static final String APP_NAME = "NetResManager";
    public static final String APP_VERSION = "1.0.0";

    /** Maximum depth for recursive directory scanning to prevent symlink loops */
    public static final int MAX_SCAN_DEPTH = 50;

    /** Scan cache TTL in milliseconds */
    public static final long SCAN_CACHE_TTL_MS = 30_000;

    /** Maximum number of entries to return in a single scan (safety limit) */
    public static final int MAX_SCAN_ENTRIES = 100_000;

    /** Minimum window width */
    public static final int WINDOW_MIN_WIDTH = 900;

    /** Minimum window height */
    public static final int WINDOW_MIN_HEIGHT = 600;

    /** Default window width */
    public static final int WINDOW_DEFAULT_WIDTH = 1100;

    /** Default window height */
    public static final int WINDOW_DEFAULT_HEIGHT = 700;

    /**
     * Returns the application data directory, creating it if necessary.
     * The directory is: {user.home}/.netresmanager/
     */
    public static Path getAppDataDir() {
        String userHome = System.getProperty("user.home");
        Path dir = Paths.get(userHome, ".netresmanager");
        File file = dir.toFile();
        if (!file.exists()) {
            file.mkdirs();
        }
        return dir;
    }

    /**
     * Returns the full path to the SQLite database file.
     * The database file is: {user.home}/.netresmanager/netresmanager.db
     */
    public static String getDbPath() {
        return getAppDataDir().resolve("netresmanager.db").toString();
    }

    /**
     * Checks whether the current operating system is Windows.
     */
    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }
}
