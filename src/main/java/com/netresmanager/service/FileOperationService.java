package com.netresmanager.service;

import com.google.gson.reflect.TypeToken;
import com.netresmanager.db.DatabaseManager;
import com.netresmanager.model.BatchResult;
import com.netresmanager.model.Project;
import com.netresmanager.util.JsonUtil;
import com.netresmanager.util.PathValidator;
import com.netresmanager.win32.Shell32RecycleBin;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles batch file operations using unified operation_records table.
 */
public class FileOperationService {

    private static final Logger LOG = Logger.getLogger(FileOperationService.class.getName());
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DatabaseManager db;
    private final TagService tagService;
    private int batchSeq = 0;           // per-batch sequence counter
    private String currentBatchPrefix;  // timestamp-uuid prefix for current batch

    public FileOperationService() {
        this.db = DatabaseManager.getInstance();
        this.tagService = new TagService();
    }

    /** Generates a unique record_id: timestamp-uuid-seq */
    private String generateRecordId() {
        if (currentBatchPrefix == null) {
            currentBatchPrefix = System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
            batchSeq = 0;
        }
        return currentBatchPrefix + "-" + batchSeq++;
    }

    /** Resets batch state for a new batch operation */
    private void resetBatch() {
        currentBatchPrefix = null;
        batchSeq = 0;
    }

    /**
     * Exports a batch of files. Each file gets the same operation_time (batch timestamp).
     * On failure, rolls back all successful operations in this batch.
     */
    public BatchResult exportFiles(List<String> filePaths, Project project) {
        resetBatch();
        BatchResult result = new BatchResult("", "export");
        result.totalCount = filePaths.size();
        if (filePaths.isEmpty()) return result;

        Path exportDir = PathValidator.normalize(project.exportDir);
        if (exportDir == null || !PathValidator.isValidDirectory(exportDir)) {
            result.errors.add(new BatchResult.OpError("", "导出目录无效: " + project.exportDir));
            result.failCount = filePaths.size();
            return result;
        }

        List<String> doneRecordIds = new ArrayList<>();
        List<String> doneSourcePaths = new ArrayList<>();

        for (String filePath : filePaths) {
            Path source = Paths.get(filePath);
            if (!Files.exists(source)) {
                String recordId = generateRecordId();
                insertRecord(recordId, project.id, "export", filePath, "");
                result.failCount++;
                result.errors.add(new BatchResult.OpError(filePath, "文件不存在"));
                continue;
            }

            String newName = (project.exportPrefix != null ? project.exportPrefix : "") + " " + source.getFileName().toString();
            long fileSize = PathValidator.getFileSizeSafe(source);
            String fileType = PathValidator.getFileTypeDisplay(source);
            String tagsJson = getTagsJson(filePath, project.id);
            String destPathStr = exportDir.resolve(newName).toString();
            String recordId = generateRecordId();

            insertRecordFull(recordId, project.id, "export", filePath, destPathStr,
                    tagsJson, fileType, fileSize);

            try {
                Path renamed = source.resolveSibling(newName);
                Files.move(source, renamed, StandardCopyOption.ATOMIC_MOVE);
                Path dest = exportDir.resolve(newName);
                Files.move(renamed, dest, StandardCopyOption.REPLACE_EXISTING);

                String successTime = LocalDateTime.now().format(DT_FMT);
                updateRecordSuccess(recordId, successTime);
                doneRecordIds.add(recordId);
                doneSourcePaths.add(filePath);
                result.successCount++;

            } catch (IOException e) {
                LOG.log(Level.WARNING, "Export failed: " + filePath, e);
                // Leave dest_path empty to indicate failure
                result.failCount++;
                result.errors.add(new BatchResult.OpError(filePath, e.getMessage()));

                rollbackExportBatch(doneRecordIds, doneSourcePaths);
                result.rolledBack = true;
                result.successCount = 0;
                break;
            }
        }

        if (!result.rolledBack && result.successCount > 0) {
            deleteTagsForPaths(doneSourcePaths, project.id);
        }

        return result;
    }

    /**
     * Recycles a batch of files.
     */
    public BatchResult recycleFiles(List<String> filePaths, Project project) {
        resetBatch();
        BatchResult result = new BatchResult("", "recycle");
        result.totalCount = filePaths.size();
        if (filePaths.isEmpty()) return result;

        List<String> doneSourcePaths = new ArrayList<>();

        for (String filePath : filePaths) {
            Path source = Paths.get(filePath);
            if (!Files.exists(source)) {
                String recordId = generateRecordId();
                insertRecord(recordId, project.id, "recycle", filePath, "");
                result.failCount++;
                result.errors.add(new BatchResult.OpError(filePath, "文件不存在"));
                continue;
            }

            String newName = (project.recyclePrefix != null ? project.recyclePrefix : "") + " " + source.getFileName().toString();
            long fileSize = PathValidator.getFileSizeSafe(source);
            String fileType = PathValidator.getFileTypeDisplay(source);
            String tagsJson = getTagsJson(filePath, project.id);
            String renamedPathStr = source.resolveSibling(newName).toString();
            String recordId = generateRecordId();

            insertRecordFull(recordId, project.id, "recycle", filePath, renamedPathStr,
                    tagsJson, fileType, fileSize);

            try {
                Path renamed = source.resolveSibling(newName);
                Files.move(source, renamed, StandardCopyOption.ATOMIC_MOVE);

                boolean recycled = Shell32RecycleBin.sendToRecycleBin(renamed.toString());
                if (recycled) {
                    String successTime = LocalDateTime.now().format(DT_FMT);
                    updateRecordSuccess(recordId, successTime);
                    doneSourcePaths.add(filePath);
                    result.successCount++;
                } else {
                    // Rename back and clear dest_path to indicate failure
                    Files.move(renamed, source);
                    clearDestPath(recordId);
                    result.failCount++;
                    result.errors.add(new BatchResult.OpError(filePath, "移入回收站失败"));
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Recycle failed: " + filePath, e);
                clearDestPath(recordId);
                result.failCount++;
                result.errors.add(new BatchResult.OpError(filePath, e.getMessage()));
            }
        }

        if (result.successCount > 0) {
            deleteTagsForPaths(doneSourcePaths, project.id);
        }

        return result;
    }

    /**
     * Manual rollback for a single operation record.
     * Each record is independent — one failure does not affect others.
     *
     * Verification logic:
     *   1. Calculate expected file location after rollback
     *   2. Perform the rollback operation
     *   3. Verify the file exists at the expected location
     *
     * On success: sets rollback_failure_reason = 'success'
     * On failure: sets rollback_failure_reason = failure reason text
     */
    public String rollbackRecord(String recordId) {
        String sql = "SELECT * FROM operation_records WHERE record_id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "记录不存在";

                String type = rs.getString("operation_type");
                String destPathStr = rs.getString("dest_path");
                String sourcePathStr = rs.getString("source_path");
                String existingRollback = rs.getString("rollback_failure_reason");

                // Failed operation: dest_path is empty
                if (destPathStr == null || destPathStr.isEmpty()) {
                    return "原始操作未成功，无法撤销";
                }

                // Already rolled back successfully
                if ("success".equals(existingRollback)) {
                    return null; // already done, not an error
                }

                // Already attempted and failed — allow retry
                // (fall through to attempt again)

                if ("export".equals(type)) {
                    return rollbackExport(recordId, sourcePathStr, destPathStr);
                } else {
                    return rollbackRecycle(recordId, sourcePathStr, destPathStr);
                }
            }
        } catch (SQLException e) {
            return "数据库错误: " + e.getMessage();
        }
    }

    /**
     * Rollback an export operation: move file from export dir back to original location.
     */
    private String rollbackExport(String recordId, String sourcePathStr, String destPathStr) {
        Path sourcePath = Paths.get(sourcePathStr);
        Path destPath = Paths.get(destPathStr);

        // Step 1: Check if the exported file exists
        if (!Files.exists(destPath)) {
            String reason = "导出目录中已找不到该文件";
            setRollbackFailed(recordId, reason);
            return reason;
        }

        // Step 2: Check for conflicts at target location
        if (Files.exists(sourcePath)) {
            String reason = "目标位置已有同名文件";
            setRollbackFailed(recordId, reason);
            return reason;
        }

        // Step 3: Perform the move
        try {
            // Ensure parent directory exists
            Path parent = sourcePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.move(destPath, sourcePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            String reason = "移动失败: " + e.getMessage();
            setRollbackFailed(recordId, reason);
            return reason;
        }

        // Step 4: Verify the file exists at the original location
        if (Files.exists(sourcePath)) {
            setRollbackSuccess(recordId);
            return null; // success
        } else {
            // Try to move back
            try {
                if (Files.exists(sourcePath)) {
                    Files.move(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignored) {}
            String reason = "移动后文件未出现在预期位置";
            setRollbackFailed(recordId, reason);
            return reason;
        }
    }

    /**
     * Rollback a recycle operation: restore file from recycle bin if possible.
     * Windows recycle bin restore is complex; most attempts will fail with
     * a clear message asking the user to restore manually.
     */
    private String rollbackRecycle(String recordId, String sourcePathStr, String destPathStr) {
        Path sourcePath = Paths.get(sourcePathStr);
        Path destPath = Paths.get(destPathStr);

        // Case 1: The renamed file still exists (recycle bin send may have failed
        // but the operation was marked as done, or permissions prevented deletion)
        if (Files.exists(destPath)) {
            // Check for conflict
            if (Files.exists(sourcePath)) {
                String reason = "目标位置已有同名文件";
                setRollbackFailed(recordId, reason);
                return reason;
            }
            // Rename back to original name
            try {
                Files.move(destPath, sourcePath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                String reason = "重命名失败: " + e.getMessage();
                setRollbackFailed(recordId, reason);
                return reason;
            }
            // Verify
            if (Files.exists(sourcePath)) {
                setRollbackSuccess(recordId);
                return null;
            } else {
                String reason = "重命名后文件未出现在预期位置";
                setRollbackFailed(recordId, reason);
                return reason;
            }
        }

        // Case 2: Source file already exists (someone manually restored it)
        if (Files.exists(sourcePath)) {
            setRollbackSuccess(recordId);
            return null; // already restored, treat as success
        }

        // Case 3: File is in recycle bin — can't restore programmatically
        String reason = "文件已在回收站中，请手动从回收站还原";
        setRollbackFailed(recordId, reason);
        return reason;
    }

    // ===== Rollback status helpers =====

    private void setRollbackSuccess(String recordId) {
        String sql = "UPDATE operation_records SET rollback_failure_reason='success' WHERE record_id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, recordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to set rollback success", e);
        }
    }

    private void setRollbackFailed(String recordId, String reason) {
        String sql = "UPDATE operation_records SET rollback_failure_reason=? WHERE record_id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reason != null ? reason : "未知原因");
            ps.setString(2, recordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to set rollback failure", e);
        }
    }

    // ===== DB helpers =====

    private void insertRecordFull(String recordId, int projectId, String opType,
                                  String sourcePath, String destPath,
                                  String tagsJson, String fileType, long fileSize) {
        String sql = "INSERT INTO operation_records (record_id, project_id, operation_type, " +
                     "source_path, dest_path, tags_json, file_type, file_size) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, recordId);
            ps.setInt(2, projectId);
            ps.setString(3, opType);
            ps.setString(4, sourcePath);
            ps.setString(5, destPath);
            ps.setString(6, tagsJson);
            ps.setString(7, fileType);
            ps.setLong(8, fileSize);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to insert operation record", e);
        }
    }

    /** Simple insert for failed operations (dest_path empty) */
    private void insertRecord(String recordId, int projectId, String opType,
                              String sourcePath, String destPath) {
        insertRecordFull(recordId, projectId, opType, sourcePath, destPath, "[]", "", 0);
    }

    private void updateRecordSuccess(String recordId, String successTime) {
        String sql = "UPDATE operation_records SET success_time=? WHERE record_id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, successTime);
            ps.setString(2, recordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to update record success", e);
        }
    }

    private void clearDestPath(String recordId) {
        String sql = "UPDATE operation_records SET dest_path='' WHERE record_id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, recordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to clear dest_path", e);
        }
    }

    private void rollbackExportBatch(List<String> recordIds, List<String> sourcePaths) {
        for (int i = 0; i < Math.min(recordIds.size(), sourcePaths.size()); i++) {
            try {
                String sql = "SELECT dest_path FROM operation_records WHERE record_id=?";
                try (Connection conn = db.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, recordIds.get(i));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String dest = rs.getString("dest_path");
                            if (dest != null && !dest.isEmpty()) {
                                Path destPath = Paths.get(dest);
                                Path srcPath = Paths.get(sourcePaths.get(i));
                                if (Files.exists(destPath)) {
                                    Files.move(destPath, srcPath, StandardCopyOption.REPLACE_EXISTING);
                                }
                            }
                        }
                    }
                }
                setRollbackSuccess(recordIds.get(i));
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Batch rollback failed: " + sourcePaths.get(i), e);
            }
        }
    }

    private String getTagsJson(String filePath, int projectId) {
        try {
            var tags = tagService.getTagsForFile(filePath, projectId);
            List<String> names = new ArrayList<>();
            for (var t : tags) names.add(t.tagName);
            return JsonUtil.toJson(names);
        } catch (SQLException e) {
            return "[]";
        }
    }

    private void deleteTagsForPaths(List<String> paths, int projectId) {
        try {
            for (String p : paths) {
                tagService.deleteTagsForPath(p, projectId);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to delete tags", e);
        }
    }

}
