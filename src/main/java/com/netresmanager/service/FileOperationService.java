package com.netresmanager.service;

import com.netresmanager.db.DatabaseManager;
import com.netresmanager.model.BatchResult;
import com.netresmanager.model.Project;
import com.netresmanager.util.PathValidator;
import com.netresmanager.win32.Shell32RecycleBin;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles batch file operations: export (move+rename) and recycle (rename+recycle bin).
 * Includes transaction tracking with rollback support for export operations.
 */
public class FileOperationService {

    private static final Logger LOG = Logger.getLogger(FileOperationService.class.getName());
    private final DatabaseManager db;
    private final TagService tagService;

    public FileOperationService() {
        this.db = DatabaseManager.getInstance();
        this.tagService = new TagService();
    }

    /**
     * Exports a batch of files: renames each file with prefix, then moves to export_dir.
     * Records each operation in export_records table.
     * On any failure, rolls back all previous successful operations in this batch.
     */
    public BatchResult exportFiles(List<String> filePaths, Project project) {
        BatchResult result = new BatchResult(generateBatchId(), "export");
        result.totalCount = filePaths.size();

        if (filePaths.isEmpty()) {
            return result;
        }

        // Validate export directory
        Path exportDir = PathValidator.normalize(project.exportDir);
        if (exportDir == null || !PathValidator.isValidDirectory(exportDir)) {
            result.errors.add(new BatchResult.OpError("", "导出目录无效: " + project.exportDir));
            result.failCount = filePaths.size();
            return result;
        }

        List<String> donePaths = new ArrayList<>();       // paths that were successfully moved
        List<String> doneSourcePaths = new ArrayList<>();  // original source paths (for tag cleanup)

        for (String filePath : filePaths) {
            Path source = Paths.get(filePath);
            if (!Files.exists(source)) {
                recordExport(project.id, result.batchId, filePath, "", source.getFileName().toString(),
                        "", "", 0, "failed");
                result.failCount++;
                result.errors.add(new BatchResult.OpError(filePath, "文件不存在"));
                continue;
            }

            String originalName = source.getFileName().toString();
            String newName = (project.exportPrefix != null ? project.exportPrefix : "") + " " + originalName;
            long fileSize = PathValidator.getFileSizeSafe(source);
            String fileType = PathValidator.getFileTypeDisplay(source);

            // Record as pending
            int recordId = recordExport(project.id, result.batchId, filePath,
                    exportDir.resolve(newName).toString(), originalName, newName, fileType, fileSize, "pending");

            try {
                // Step 1: Rename the source file
                Path renamed = source.resolveSibling(newName);
                Files.move(source, renamed, StandardCopyOption.ATOMIC_MOVE);

                // Step 2: Move to export directory
                Path dest = exportDir.resolve(newName);
                Files.move(renamed, dest, StandardCopyOption.REPLACE_EXISTING);

                // Mark as done
                updateExportStatus(recordId, "done");
                donePaths.add(dest.toString());
                doneSourcePaths.add(filePath);
                result.successCount++;

            } catch (IOException e) {
                LOG.log(Level.WARNING, "Export failed for: " + filePath, e);
                updateExportStatus(recordId, "failed");
                result.failCount++;
                result.errors.add(new BatchResult.OpError(filePath, e.getMessage()));

                // Rollback all done operations in this batch
                rollbackExportBatch(result.batchId, donePaths, doneSourcePaths);
                result.rolledBack = true;
                // Clear done lists since they've been rolled back
                result.successCount = 0;
                break;  // Stop processing further files
            }
        }

        // If all succeeded, delete tags for all processed files
        if (!result.rolledBack && result.successCount > 0) {
            try {
                for (String sourcePath : doneSourcePaths) {
                    tagService.deleteTagsForPath(sourcePath, project.id);
                }
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Failed to delete tags during export", e);
            }
        }

        return result;
    }

    /**
     * Recycles a batch of files: renames each file with prefix, then sends to Windows Recycle Bin.
     * Records each operation in recycle_records table.
     * Recycle bin operations are NOT rollback-able.
     */
    public BatchResult recycleFiles(List<String> filePaths, Project project) {
        BatchResult result = new BatchResult(generateBatchId(), "recycle");
        result.totalCount = filePaths.size();

        if (filePaths.isEmpty()) {
            return result;
        }

        List<String> doneSourcePaths = new ArrayList<>();

        for (String filePath : filePaths) {
            Path source = Paths.get(filePath);
            if (!Files.exists(source)) {
                recordRecycle(project.id, result.batchId, filePath, source.getFileName().toString(),
                        "", "", 0, "failed");
                result.failCount++;
                result.errors.add(new BatchResult.OpError(filePath, "文件不存在"));
                continue;
            }

            String originalName = source.getFileName().toString();
            String newName = (project.recyclePrefix != null ? project.recyclePrefix : "") + " " + originalName;
            long fileSize = PathValidator.getFileSizeSafe(source);
            String fileType = PathValidator.getFileTypeDisplay(source);

            int recordId = recordRecycle(project.id, result.batchId, filePath, originalName,
                    source.resolveSibling(newName).toString(), fileType, fileSize, "pending");

            try {
                // Step 1: Rename the file
                Path renamed = source.resolveSibling(newName);
                Files.move(source, renamed, StandardCopyOption.ATOMIC_MOVE);

                // Step 2: Send to recycle bin
                boolean recycled = Shell32RecycleBin.sendToRecycleBin(renamed.toString());
                if (recycled) {
                    updateRecycleStatus(recordId, "done");
                    doneSourcePaths.add(filePath);
                    result.successCount++;
                } else {
                    // Rename back on recycle failure
                    Files.move(renamed, source);
                    updateRecycleStatus(recordId, "failed");
                    result.failCount++;
                    result.errors.add(new BatchResult.OpError(filePath, "移入回收站失败"));
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Recycle failed for: " + filePath, e);
                updateRecycleStatus(recordId, "failed");
                result.failCount++;
                result.errors.add(new BatchResult.OpError(filePath, e.getMessage()));
            }
        }

        // Delete tags for successfully recycled files
        if (result.successCount > 0) {
            try {
                for (String sourcePath : doneSourcePaths) {
                    tagService.deleteTagsForPath(sourcePath, project.id);
                }
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Failed to delete tags during recycle", e);
            }
        }

        return result;
    }

    /**
     * Rolls back an export batch by moving files back from export directory to original locations.
     */
    private void rollbackExportBatch(String batchId, List<String> destPaths, List<String> sourcePaths) {
        for (int i = 0; i < Math.min(destPaths.size(), sourcePaths.size()); i++) {
            try {
                Path dest = Paths.get(destPaths.get(i));
                Path originalSource = Paths.get(sourcePaths.get(i));

                if (Files.exists(dest)) {
                    // Move back to original location
                    Files.move(dest, originalSource.resolveSibling(dest.getFileName()),
                            StandardCopyOption.REPLACE_EXISTING);
                    // Rename back to original name
                    Path renamed = originalSource.resolveSibling(dest.getFileName());
                    Files.move(renamed, originalSource);
                }
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Rollback failed for: " + sourcePaths.get(i), e);
            }
        }
        // Update all done records to rolled_back
        String sql = "UPDATE export_records SET status='rolled_back' WHERE batch_id=? AND status='done'";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, batchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to update rollback status", e);
        }
    }

    /**
     * Manual rollback triggered by user from the UI.
     */
    public BatchResult rollbackBatch(String batchId, String operationType) {
        BatchResult result = new BatchResult(batchId, operationType);
        if ("export".equals(operationType)) {
            // Query all done records for this batch
            String sql = "SELECT source_path, dest_path FROM export_records WHERE batch_id=? AND status='done'";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, batchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String sourcePath = rs.getString("source_path");
                        String destPath = rs.getString("dest_path");
                        try {
                            Path dest = Paths.get(destPath);
                            Path src = Paths.get(sourcePath);
                            if (Files.exists(dest)) {
                                Files.move(dest, src.resolveSibling(dest.getFileName()));
                                Files.move(src.resolveSibling(dest.getFileName()), src);
                            }
                            result.successCount++;
                        } catch (IOException e) {
                            result.failCount++;
                            result.errors.add(new BatchResult.OpError(sourcePath, e.getMessage()));
                        }
                    }
                }
                // Mark all as rolled_back
                String updateSql = "UPDATE export_records SET status='rolled_back' WHERE batch_id=? AND status='done'";
                try (PreparedStatement ups = conn.prepareStatement(updateSql)) {
                    ups.setString(1, batchId);
                    ups.executeUpdate();
                }
            } catch (SQLException e) {
                LOG.log(Level.SEVERE, "Rollback query failed", e);
                result.errors.add(new BatchResult.OpError("", e.getMessage()));
            }
        }
        return result;
    }

    // ===== DB helpers =====

    private int recordExport(int projectId, String batchId, String sourcePath, String destPath,
                              String originalName, String newName, String fileType, long fileSize, String status) {
        String sql = "INSERT INTO export_records (project_id, batch_id, source_path, dest_path, original_name, " +
                     "new_name, file_type, file_size, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, projectId);
            ps.setString(2, batchId);
            ps.setString(3, sourcePath);
            ps.setString(4, destPath);
            ps.setString(5, originalName);
            ps.setString(6, newName);
            ps.setString(7, fileType);
            ps.setLong(8, fileSize);
            ps.setString(9, status);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to record export", e);
        }
        return -1;
    }

    private void updateExportStatus(int recordId, String status) {
        String sql = "UPDATE export_records SET status=? WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, recordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to update export status", e);
        }
    }

    private int recordRecycle(int projectId, String batchId, String sourcePath, String originalName,
                               String renamedPath, String fileType, long fileSize, String status) {
        String sql = "INSERT INTO recycle_records (project_id, batch_id, source_path, original_name, " +
                     "renamed_path, file_type, file_size, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, projectId);
            ps.setString(2, batchId);
            ps.setString(3, sourcePath);
            ps.setString(4, originalName);
            ps.setString(5, renamedPath);
            ps.setString(6, fileType);
            ps.setLong(7, fileSize);
            ps.setString(8, status);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to record recycle", e);
        }
        return -1;
    }

    private void updateRecycleStatus(int recordId, String status) {
        String sql = "UPDATE recycle_records SET status=? WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, recordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to update recycle status", e);
        }
    }

    private String generateBatchId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
