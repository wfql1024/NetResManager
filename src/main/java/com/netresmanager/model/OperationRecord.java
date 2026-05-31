package com.netresmanager.model;

import java.util.List;

/**
 * Unified operation record for both export and recycle operations.
 */
public class OperationRecord {
    public int id;
    public int projectId;
    public String batchId;
    public String operationType;        // "export" or "recycle"
    public String sourcePath;           // original full path
    public String destPath;             // destination/renamed path
    public String originalName;
    public String newName;
    public String fileType;
    public long fileSize;
    public String fileSizeFormatted;
    public List<String> tags;           // parsed from tags_json
    public String operationTime;        // batch timestamp (when button pressed)
    public String successTime;          // when operation completed
    public String status;               // done | failed | rolled_back
    public boolean hidden;
    public boolean excludeFromStats;
    public boolean deleted;
    public String rollbackFailureReason; // non-empty = can't rollback

    public OperationRecord() {}

    /** Whether this record can be rolled back */
    public boolean isRollbackable() {
        return "done".equals(status)
            && (rollbackFailureReason == null || rollbackFailureReason.isEmpty());
    }

    /** Whether this record should appear in history (not hidden, not deleted) */
    public boolean isVisible() {
        return !hidden && !deleted;
    }

    /** Whether this record should be counted in statistics */
    public boolean isCountable() {
        return !excludeFromStats && !deleted && "done".equals(status);
    }
}
