package com.netresmanager.model;

/**
 * Represents a single file or directory entry in the file list.
 */
public class FileEntry {
    public String name;
    public String path;
    public String type;
    public long size;
    public String sizeFormatted;
    public String modified;
    public boolean isDirectory;
    public String sourcePath;  // the project path this file belongs to

    public FileEntry() {}

    public FileEntry(String name, String path, String type, long size,
                     String modified, boolean isDirectory, String sourcePath) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.size = size;
        this.sizeFormatted = formatSize(size);
        this.modified = modified;
        this.isDirectory = isDirectory;
        this.sourcePath = sourcePath;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char prefix = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), prefix);
    }

    @Override
    public String toString() {
        return name + (isDirectory ? "/" : "") + " (" + sizeFormatted + ")";
    }
}
