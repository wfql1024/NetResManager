package com.netresmanager.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of a batch file operation (export or recycle).
 */
public class BatchResult {
    public String batchId;
    public String operationType;
    public int successCount;
    public int failCount;
    public int totalCount;
    public boolean rolledBack;
    public List<OpError> errors;

    public BatchResult() {}

    public BatchResult(String batchId, String operationType) {
        this.batchId = batchId;
        this.operationType = operationType;
        this.errors = new ArrayList<>();
    }

    public static class OpError {
        public String path;
        public String message;

        public OpError() {}

        public OpError(String path, String message) {
            this.path = path;
            this.message = message;
        }
    }
}
