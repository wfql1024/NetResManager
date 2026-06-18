package com.netresmanager.service;

import com.netresmanager.config.AppConfig;
import com.netresmanager.model.FileEntry;
import com.netresmanager.model.FileEntryGroup;
import com.netresmanager.model.Project;
import com.netresmanager.util.PathValidator;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Scans project directories recursively and returns FileEntry lists.
 * Includes an in-memory cache with configurable TTL.
 */
public class FileScanService {

    private static final Logger LOG = Logger.getLogger(FileScanService.class.getName());
    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, CachedScan> cache = new HashMap<>();

    private static class CachedScan {
        final long timestamp;
        final List<FileEntryGroup> result;

        CachedScan(long timestamp, List<FileEntryGroup> result) {
            this.timestamp = timestamp;
            this.result = result;
        }
    }

    /**
     * Scans all paths in a project, optionally filtered to a subdirectory.
     * Returns files grouped by their source base path.
     */
    public List<FileEntryGroup> scanProject(Project project, String currentDir) {
        String cacheKey = project.id + "|" + (currentDir != null ? currentDir : "");
        CachedScan cached = cache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < AppConfig.SCAN_CACHE_TTL_MS) {
            return cached.result;
        }

        List<FileEntryGroup> result = doScan(project, currentDir);
        cache.put(cacheKey, new CachedScan(System.currentTimeMillis(), result));
        return result;
    }

    /**
     * Forces a fresh scan, bypassing the cache.
     */
    public List<FileEntryGroup> refreshScan(Project project, String currentDir) {
        String cacheKey = project.id + "|" + (currentDir != null ? currentDir : "");
        cache.remove(cacheKey);
        List<FileEntryGroup> result = doScan(project, currentDir);
        cache.put(cacheKey, new CachedScan(System.currentTimeMillis(), result));
        return result;
    }

    private List<FileEntryGroup> doScan(Project project, String currentDir) {
        List<FileEntryGroup> groups = new ArrayList<>();

        if (project.paths == null || project.paths.isEmpty()) {
            return groups;
        }

        for (String basePathStr : project.paths) {
            Path basePath = PathValidator.normalize(basePathStr);
            if (basePath == null || !PathValidator.isValidDirectory(basePath)) {
                // Skip invalid paths (don't add empty groups)
                continue;
            }

            // When browsing a subdirectory, skip project paths that don't contain it
            if (currentDir != null && !currentDir.isEmpty()) {
                Path cd = PathValidator.normalize(currentDir);
                if (cd == null || !PathValidator.isSafeWithinBase(cd, basePath)) {
                    continue; // this base path doesn't contain currentDir, skip it
                }
            }

            Path scanRoot;
            if (currentDir != null && !currentDir.isEmpty()) {
                Path cd = PathValidator.normalize(currentDir);
                scanRoot = cd;
            } else {
                scanRoot = basePath;
            }

            List<FileEntry> files = scanDirectory(scanRoot, basePath, 0);
            FileEntryGroup group = new FileEntryGroup();
            group.sourcePath = basePath.toString();
            group.files = files;
            groups.add(group);
        }

        return groups;
    }

    /**
     * Recursively scans a directory, returning FileEntry objects.
     * Directories listed first, then files, both sorted alphabetically.
     */
    private List<FileEntry> scanDirectory(Path dir, Path basePath, int depth) {
        List<FileEntry> entries = new ArrayList<>();

        if (depth > AppConfig.MAX_SCAN_DEPTH) {
            return entries;
        }

        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> children = stream.toList();

            // Separate dirs and files
            List<Path> dirs = new ArrayList<>();
            List<Path> files = new ArrayList<>();

            for (Path child : children) {
                try {
                    if (Files.isDirectory(child)) {
                        dirs.add(child);
                    } else if (Files.isRegularFile(child)) {
                        files.add(child);
                    }
                } catch (Exception e) {
                    // Skip files we can't access
                }
            }

            // Sort alphabetically (case-insensitive)
            Comparator<Path> pathComp = Comparator.comparing(
                    p -> p.getFileName().toString().toLowerCase());
            dirs.sort(pathComp);
            files.sort(pathComp);

            // Add directories first
            for (Path d : dirs) {
                try {
                    BasicFileAttributes attr = Files.readAttributes(d, BasicFileAttributes.class);
                    FileEntry entry = new FileEntry(
                            d.getFileName().toString(),
                            d.toString(),
                            PathValidator.getFileTypeDisplay(d),
                            0,
                            formatTime(attr.lastModifiedTime().toInstant()),
                            true,
                            basePath.toString()
                    );
                    entries.add(entry);
                } catch (IOException e) {
                    // Skip on error
                }
            }

            // Then files
            for (Path f : files) {
                try {
                    BasicFileAttributes attr = Files.readAttributes(f, BasicFileAttributes.class);
                    FileEntry entry = new FileEntry(
                            f.getFileName().toString(),
                            f.toString(),
                            PathValidator.getFileTypeDisplay(f),
                            attr.size(),
                            formatTime(attr.lastModifiedTime().toInstant()),
                            false,
                            basePath.toString()
                    );
                    entries.add(entry);
                } catch (IOException e) {
                    // Skip on error
                }
            }

            // Limit total entries
            if (entries.size() > AppConfig.MAX_SCAN_ENTRIES) {
                entries = entries.subList(0, AppConfig.MAX_SCAN_ENTRIES);
            }

        } catch (AccessDeniedException e) {
            LOG.log(Level.WARNING, "Access denied: " + dir);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error scanning directory: " + dir, e);
        }

        return entries;
    }

    /**
     * Gets details for a single file.
     */
    public FileEntry getFileDetails(String absolutePath) {
        Path path = Path.of(absolutePath);
        try {
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            return new FileEntry(
                    path.getFileName().toString(),
                    path.toString(),
                    PathValidator.getFileTypeDisplay(path),
                    attr.isRegularFile() ? attr.size() : 0,
                    formatTime(attr.lastModifiedTime().toInstant()),
                    attr.isDirectory(),
                    null
            );
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Clears all cached scans.
     */
    public void clearCache() {
        cache.clear();
    }

    // ==================== Async folder size calculation ====================

    private final ExecutorService folderSizeExecutor = Executors.newFixedThreadPool(4);
    private final Map<String, Future<?>> pendingCalculations = new ConcurrentHashMap<>();
    private Consumer<String> onFolderSizeResult;

    /** Sets the callback for pushing folder size results back to JS. */
    public void setOnFolderSizeResult(Consumer<String> callback) {
        this.onFolderSizeResult = callback;
    }

    /**
     * Starts async calculation of folder sizes for the given paths.
     * Results are pushed back via the onFolderSizeResult callback.
     */
    public void startAsyncFolderSizeCalculation(List<String> folderPaths) {
        if (folderPaths == null || folderPaths.isEmpty()) return;
        for (String pathStr : folderPaths) {
            // Skip if already pending
            if (pendingCalculations.containsKey(pathStr)) continue;
            Path path = Path.of(pathStr);
            Future<?> future = folderSizeExecutor.submit(() -> {
                long size = calculateFolderSizeRecursive(path);
                String formatted = formatSize(size);
                if (onFolderSizeResult != null) {
                    // Escape path for JS string literal: backslash and single quote
                    String escaped = pathStr.replace("\\", "\\\\").replace("'", "\\'");
                    onFolderSizeResult.accept(
                        "NRM.onFolderSize('" + escaped + "'," + size + ",'" + formatted + "')");
                }
            });
            pendingCalculations.put(pathStr, future);
        }
    }

    /** Cancels all pending folder size calculations. */
    public void cancelFolderSizeCalculations() {
        for (Future<?> f : pendingCalculations.values()) {
            f.cancel(true);
        }
        pendingCalculations.clear();
    }

    /**
     * Recursively calculates the total size of a directory.
     * Uses java.io.File for performance; checks Thread.interrupted() for cancellation.
     */
    public long calculateFolderSizeRecursive(Path dir) {
        return calcDirSize(dir, 0);
    }

    private long calcDirSize(Path dir, int depth) {
        if (depth > AppConfig.MAX_SCAN_DEPTH) return 0;
        long total = 0;
        try {
            File[] files = dir.toFile().listFiles();
            if (files == null) return 0;
            for (File f : files) {
                if (Thread.interrupted()) return total;
                try {
                    if (f.isDirectory()) {
                        total += calcDirSize(f.toPath(), depth + 1);
                    } else if (f.isFile()) {
                        total += f.length();
                    }
                } catch (Exception ignored) {
                    // skip inaccessible files
                }
            }
        } catch (Exception ignored) {
            // skip inaccessible directories
        }
        return total;
    }

    private String formatTime(Instant instant) {
        LocalDateTime dt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return dt.format(DT_FORMAT);
    }

    /**
     * Formats a byte count as human-readable string.
     */
    private static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        double v = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        while (v >= 1024 && idx < units.length - 1) { v /= 1024; idx++; }
        return String.format("%.1f %s", v, units[idx]);
    }
}
