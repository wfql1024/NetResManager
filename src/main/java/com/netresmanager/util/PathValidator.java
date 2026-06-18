package com.netresmanager.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for path validation and normalization.
 */
public final class PathValidator {

    private PathValidator() {}

    /**
     * Normalizes a path string: resolves "..", ".", trims, handles Windows long paths.
     * Returns the normalized absolute path or null if invalid.
     */
    public static Path normalize(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) return null;
        try {
            // Handle Windows long path prefix
            String cleaned = pathStr.trim();
            Path path;
            if (cleaned.startsWith("\\\\?\\")) {
                path = Paths.get(cleaned);
            } else {
                path = Paths.get(cleaned);
            }
            Path normalized = path.toAbsolutePath().normalize();
            return normalized;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * Checks whether the given path is an existing, readable directory.
     */
    public static boolean isValidDirectory(Path path) {
        return path != null && Files.isDirectory(path) && Files.isReadable(path);
    }

    /**
     * Checks whether the given path string represents a valid, existing directory.
     */
    public static boolean isValidDirectory(String pathStr) {
        Path p = normalize(pathStr);
        return isValidDirectory(p);
    }

    /**
     * Checks whether the given path is an existing, readable regular file.
     */
    public static boolean isValidFile(Path path) {
        return path != null && Files.isRegularFile(path) && Files.isReadable(path);
    }

    /**
     * Validates a list of directory path strings.
     * @return list of invalid paths (empty = all valid)
     */
    public static List<String> validateDirectoryPaths(List<String> paths) {
        List<String> invalid = new ArrayList<>();
        for (String p : paths) {
            if (!isValidDirectory(p)) {
                invalid.add(p);
            }
        }
        return invalid;
    }

    /**
     * Checks whether a child path is safely within a base directory.
     * Prevents path traversal attacks (e.g., ../../system32).
     */
    public static boolean isSafeWithinBase(Path child, Path base) {
        if (child == null || base == null) return false;
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path normalizedChild = child.toAbsolutePath().normalize();
        return normalizedChild.startsWith(normalizedBase);
    }

    /**
     * Returns the file extension (lowercase, without dot) or "folder" for directories.
     */
    public static String getFileType(Path path) {
        if (Files.isDirectory(path)) return "folder";
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "(无扩展名)";
        return name.substring(dot + 1).toLowerCase();
    }

    /**
     * Returns a friendly Chinese description for common file types.
     */
    public static String getFileTypeDisplay(Path path) {
        if (Files.isDirectory(path)) return "文件夹";
        String ext = getFileType(path);
        return switch (ext.toLowerCase()) {
            case "pdf" -> "PDF 文档";
            case "doc", "docx" -> "Word 文档";
            case "xls", "xlsx" -> "Excel 表格";
            case "ppt", "pptx" -> "PowerPoint 演示";
            case "jpg", "jpeg", "png", "gif", "bmp", "webp" -> "图片";
            case "mp3", "wav", "flac", "aac", "ogg" -> "音频";
            case "mp4", "avi", "mkv", "mov", "wmv", "flv" -> "视频";
            case "zip", "rar", "7z", "tar", "gz" -> "压缩文件";
            case "txt", "log", "md", "csv" -> "文本文件";
            case "java", "py", "js", "ts", "c", "cpp", "h" -> "源代码";
            case "exe", "dll", "so" -> "可执行文件";
            case "html", "htm", "css", "xml", "json" -> "网页/数据文件";
            case "(无扩展名)" -> "(无扩展名)";
            default -> ext.toUpperCase() + " 文件";
        };
    }

    /**
     * Attempts to get file size. For directories, recursively calculates total size.
     * Returns 0 on error.
     */
    public static long getFileSizeSafe(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                return Files.size(path);
            }
            if (Files.isDirectory(path)) {
                return getFolderSizeRecursive(path);
            }
        } catch (IOException ignored) {}
        return 0;
    }

    /**
     * Recursively calculates the total size of a directory.
     * Uses java.io.File for performance.
     */
    public static long getFolderSizeRecursive(Path dir) {
        long total = 0;
        try {
            java.io.File[] files = dir.toFile().listFiles();
            if (files == null) return 0;
            for (java.io.File f : files) {
                try {
                    if (f.isDirectory()) {
                        total += getFolderSizeRecursive(f.toPath());
                    } else if (f.isFile()) {
                        total += f.length();
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return total;
    }
}
