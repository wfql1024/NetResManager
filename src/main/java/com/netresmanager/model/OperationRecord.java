package com.netresmanager.model;

import java.nio.file.Path;
import java.util.List;

/**
 * Unified operation record (V4 schema).
 *
 * "可推导则不必要" — fields derivable from other columns are NOT stored in DB.
 *   record_id = timestamp-uuid-seq (PK, contains batch+timestamp info)
 *   source_path → derive originalName, originalLocation
 *   dest_path   → derive newName, newLocation; empty → operation failed
 */
public class OperationRecord {
    // === DB columns ===
    public String recordId;              // PK: timestamp-uuid-seq
    public int projectId;
    public String operationType;         // "export" | "recycle"
    public String sourcePath;            // original full path
    public String destPath;              // target path; empty = failed
    public String fileType;
    public long fileSize;
    public List<String> tags;            // parsed from tags_json
    public String successTime;
    public boolean hidden;
    public boolean excludeFromStats;
    public boolean deleted;
    public String rollbackFailureReason; // "" = not attempted, "success" = ok, other = failure reason

    // === Derived (set at query time via computeDerived(), not stored in DB) ===
    public String originalName;
    public String newName;
    public String originalLocation;
    public String newLocation;
    public String operationTime;
    public String fileSizeFormatted;

    public OperationRecord() {}

    /** Populate derived fields from the DB columns. Call after loading from DB. */
    public void computeDerived() {
        // Names and locations from paths
        if (sourcePath != null && !sourcePath.isEmpty()) {
            Path sp = Path.of(sourcePath);
            originalName = sp.getFileName() != null ? sp.getFileName().toString() : "";
            Path parent = sp.getParent();
            originalLocation = parent != null ? parent.toString() : "";
        } else {
            originalName = "";
            originalLocation = "";
        }
        if (destPath != null && !destPath.isEmpty()) {
            Path dp = Path.of(destPath);
            newName = dp.getFileName() != null ? dp.getFileName().toString() : "";
            Path parent = dp.getParent();
            newLocation = parent != null ? parent.toString() : "";
        } else {
            newName = "";
            newLocation = "";
        }
        // Operation time from record_id timestamp
        if (recordId != null && !recordId.isEmpty()) {
            int dashIdx = recordId.indexOf('-');
            if (dashIdx > 0) {
                try {
                    long ts = Long.parseLong(recordId.substring(0, dashIdx));
                    java.time.LocalDateTime dt = java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(ts),
                        java.time.ZoneId.systemDefault());
                    operationTime = dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } catch (NumberFormatException e) {
                    operationTime = "";
                }
            }
        }
        if (operationTime == null || operationTime.isEmpty()) {
            operationTime = successTime != null ? successTime : "";
        }
        // Formatted file size
        fileSizeFormatted = formatSize(fileSize);
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        double v = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        while (v >= 1024 && idx < units.length - 1) { v /= 1024; idx++; }
        return String.format("%.1f %s", v, units[idx]);
    }

    /** Whether the original operation succeeded (dest_path is non-empty). */
    public boolean isSuccess() {
        return destPath != null && !destPath.isEmpty();
    }

    /** Whether this record can be rolled back. */
    public boolean isRollbackable() {
        return isSuccess()
            && (rollbackFailureReason == null || rollbackFailureReason.isEmpty());
    }
}
