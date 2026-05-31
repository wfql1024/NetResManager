package com.netresmanager.model;

/**
 * Statistics aggregation result: one category entry for pie charts and lists.
 */
public class StatEntry {
    public String category;
    public int count;
    public long totalSize;
    public String totalSizeFormatted;   // "132.2 MB"
    public String totalSizeDisplay;      // "138,622,560 (132.2 MB)"

    public StatEntry() {}

    public StatEntry(String category, int count, long totalSize) {
        this.category = category;
        this.count = count;
        this.totalSize = totalSize;
        this.totalSizeFormatted = formatSize(totalSize);
        this.totalSizeDisplay = String.format("%,d", totalSize) + " (" + this.totalSizeFormatted + ")";
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double v = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        while (v >= 1024 && idx < units.length - 1) { v /= 1024; idx++; }
        return String.format("%.1f %s", v, units[idx]);
    }
}
