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

    public FileOperationService() {
        this.db = DatabaseManager.getInstance();
        this.tagService = new TagService();
    }

    /**
     * Exports a batch of files. Each file gets the same operation_time (batch timestamp).
     * On failure, rolls back all successful operations in this batch.
     */
    public BatchResult exportFiles(List<String> filePaths, Project project) {
        BatchResult result = new BatchResult(generateBatchId(), "export");
        result.totalCount = filePaths.size();
        if (filePaths.isEmpty()) return result;

        String batchTime = LocalDateTime.now().format(DT_FMT);
        Path exportDir = PathValidator.normalize(project.exportDir);
        if (exportDir == null || !PathValidator.isValidDirectory(exportDir)) {
            result.errors.add(new BatchResult.OpError("", "导出目录无效: " + project.exportDir));
            result.failCount = filePaths.size();
            return result;
        }

        List<String> donePaths = new ArrayList<>();
        List<String> doneSourcePaths = new ArrayList<>();

        for (String filePath : filePaths) {
            Path source = Paths.get(filePath);
            if (!Files.exists(source)) {
                insertRecord(project.id, result.batchId, "export", filePath, "",
                        source.getFileName().toString(), "", batchTime, "failed");
                result.failCount++;
                result.errors.add(new BatchResult.OpError(filePath, "文件不存在"));
                continue;
            }

            String originalName = source.getFileName().toString();
            String newName = (project.exportPrefix != null ? project.exportPrefix : "") + " " + originalName;
            long fileSize = PathValidator.getFileSizeSafe(source);
            String fileType = PathValidator.getFileTypeDisplay(source);
            String tagsJson = getTagsJson(filePath, project.id);
            String destPathStr = exportDir.resolve(newName).toString();

            int recordId = insertRecordFull(project.id, result.batchId, "export",
                    filePath, destPathStr, originalName, newName, batchTime,
                    tagsJson, fileType, fileSize, "pending");

            try {
                Path renamed = source.resolveSibling(newName);
                Files.move(source, renamed, StandardCopyOption.ATOMIC_MOVE);
                Path dest = exportDir.resolve(newName);
                Files.move(renamed, dest, StandardCopyOption.REPLACE_EXISTING);

                String successTime = LocalDateTime.now().format(DT_FMT);
                updateRecordSuccess(recordId, successTime);
                donePaths.add(dest.toString());
                doneSourcePaths.add(filePath);
                result.successCount++;

            } catch (IOException e) {
                LOG.log(Level.WARNING, "Export failed: " + filePath, e);
                updateRecordFailed(recordId);
                result.failCount++;
                result.errors.add(new BatchResult.OpError(filePath, e.getMessage()));

                rollbackExportBatch(result.batchId, donePaths, doneSourcePaths);
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
        BatchResult result = new BatchResult(generateBatchId(), "recycle");
        result.totalCount = filePaths.size();
        if (filePaths.isEmpty()) return result;

        String batchTime = LocalDateTime.now().format(DT_FMT);
        List<String> doneSourcePaths = new ArrayList<>();

        for (String filePath : filePaths) {
            Path source = Paths.get(filePath);
            if (!Files.exists(source)) {
                insertRecord(project.id, result.batchId, "recycle", filePath, "",
                        source.getFileName().toString(), "", batchTime, "failed");
                result.failCount++;
                result.errors.add(new BatchResult.OpError(filePath, "文件不存在"));
                continue;
            }

            String originalName = source.getFileName().toString();
            String newName = (project.recyclePrefix != null ? project.recyclePrefix : "") + " " + originalName;
            long fileSize = PathValidator.getFileSizeSafe(source);
            String fileType = PathValidator.getFileTypeDisplay(source);
            String tagsJson = getTagsJson(filePath, project.id);
            String renamedPathStr = source.resolveSibling(newName).toString();

            int recordId = insertRecordFull(project.id, result.batchId, "recycle",
                    filePath, renamedPathStr, originalName, newName, batchTime,
                    tagsJson, fileType, fileSize, "pending");

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
                    // Rename back
                    Files.move(renamed, source);
                    updateRecordFailed(recordId);
                    result.failCount++;
                    result.errors.add(new BatchResult.OpError(filePath, "移入回收站失败"));
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Recycle failed: " + filePath, e);
                updateRecordFailed(recordId);
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
     * On failure, sets rollback_failure_reason so user can see it's greyed out.
     */
    public String rollbackRecord(int recordId) {
        String sql = "SELECT * FROM operation_records WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "记录不存在";

                String type = rs.getString("operation_type");
                String destPath = rs.getString("dest_path");
                String sourcePath = rs.getString("source_path");
                String rollbackReason = rs.getString("rollback_failure_reason");

                if (rollbackReason != null && !rollbackReason.isEmpty()) {
                    return "该记录已无法撤回: " + rollbackReason;
                }

                if ("export".equals(type)) {
                    try {
                        Path dest = Paths.get(destPath);
                        Path src = Paths.get(sourcePath);
                        if (Files.exists(dest)) {
                            Files.move(dest, src.resolveSibling(dest.getFileName()),
                                    StandardCopyOption.REPLACE_EXISTING);
                            Files.move(src.resolveSibling(dest.getFileName()), src);
                        }
                        markRolledBack(recordId);
                        return null; // success
                    } catch (IOException e) {
                        String reason = e.getMessage();
                        setRollbackFailure(recordId, reason);
                        return reason;
                    }
                } else {
                    // Recycle rollback: try to restore from recycle bin
                    // This is unreliable; mark as failed
                    setRollbackFailure(recordId, "回收站操作不支持撤回");
                    return "回收站操作不支持撤回";
                }
            }
        } catch (SQLException e) {
            return "数据库错误: " + e.getMessage();
        }
    }

    // ===== DB helpers =====

    private int insertRecordFull(int projectId, String batchId, String opType,
                              String sourcePath, String destPath,
                              String originalName, String newName,
                              String opTime, String tagsJson,
                              String fileType, long fileSize, String status) {
        String sql = "INSERT INTO operation_records (project_id, batch_id, operation_type, " +
                     "source_path, dest_path, original_name, new_name, operation_time, " +
                     "tags_json, file_type, file_size, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, projectId);
            ps.setString(2, batchId);
            ps.setString(3, opType);
            ps.setString(4, sourcePath);
            ps.setString(5, destPath);
            ps.setString(6, originalName);
            ps.setString(7, newName);
            ps.setString(8, opTime);
            ps.setString(9, tagsJson);
            ps.setString(10, fileType);
            ps.setLong(11, fileSize);
            ps.setString(12, status);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to insert operation record", e);
        }
        return -1;
    }

    /** Simple insert (file not found / fast fail) */
    private int insertRecord(int projectId, String batchId, String opType,
                              String sourcePath, String destPath,
                              String originalName, String newName,
                              String opTime, String status) {
        return insertRecordFull(projectId, batchId, opType, sourcePath, destPath,
                originalName, newName, opTime, "[]", "", 0, status);
    }

    private void updateRecordSuccess(int recordId, String successTime) {
        String sql = "UPDATE operation_records SET status='done', success_time=? WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, successTime);
            ps.setInt(2, recordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to update record success", e);
        }
    }

    private void updateRecordFailed(int recordId) {
        String sql = "UPDATE operation_records SET status='failed' WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, recordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to update record failed", e);
        }
    }

    private void markRolledBack(int recordId) {
        String sql = "UPDATE operation_records SET status='rolled_back' WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, recordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to mark rolled back", e);
        }
    }

    private void setRollbackFailure(int recordId, String reason) {
        String sql = "UPDATE operation_records SET rollback_failure_reason=?, status='rolled_back' WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setInt(2, recordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to set rollback failure", e);
        }
    }

    private void rollbackExportBatch(String batchId, List<String> destPaths, List<String> sourcePaths) {
        for (int i = 0; i < Math.min(destPaths.size(), sourcePaths.size()); i++) {
            try {
                Path dest = Paths.get(destPaths.get(i));
                Path src = Paths.get(sourcePaths.get(i));
                if (Files.exists(dest)) {
                    Files.move(dest, src.resolveSibling(dest.getFileName()),
                            StandardCopyOption.REPLACE_EXISTING);
                    Files.move(src.resolveSibling(dest.getFileName()), src);
                }
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Rollback failed: " + sourcePaths.get(i), e);
            }
        }
        // Mark batch as rolled_back
        String sql = "UPDATE operation_records SET status='rolled_back' WHERE batch_id=? AND status='done'";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, batchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to update rollback status", e);
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

    private String generateBatchId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
