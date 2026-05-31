package com.netresmanager.model;

/**
 * Statistics aggregation result: one category entry for pie charts and lists.
 */
public class StatEntry {
    public String category;  // file extension, "folder", or tag name
    public int count;
    public long totalSize;
    public String totalSizeFormatted;

    public StatEntry() {}

    public StatEntry(String category, int count, long totalSize) {
        this.category = category;
        this.count = count;
        this.totalSize = totalSize;
        this.totalSizeFormatted = formatSize(totalSize);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char prefix = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), prefix);
    }
}
