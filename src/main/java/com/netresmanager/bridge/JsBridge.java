package com.netresmanager.bridge;

import com.google.gson.reflect.TypeToken;
import com.netresmanager.model.*;
import com.netresmanager.service.*;
import com.netresmanager.util.JsonUtil;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Java-JS bridge exposed as window.javaObject in the WebView.
 * All public methods starting with "js" are callable from JavaScript.
 *
 * Each method returns a JSON envelope string:
 *   {"success": true, "data": ..., "error": null} on success
 *   {"success": false, "data": null, "error": {"code": "...", "message": "..."}} on failure
 */
public class JsBridge {

    private static final Logger LOG = Logger.getLogger(JsBridge.class.getName());

    private final ProjectService projectService;
    private final FileScanService fileScanService;
    private final FileOperationService fileOpService;
    private final TagService tagService;
    private final StatisticsService statsService;

    public JsBridge() {
        this.projectService = new ProjectService();
        this.fileScanService = new FileScanService();
        this.fileOpService = new FileOperationService();
        this.tagService = new TagService();
        this.statsService = new StatisticsService();
    }

    // ==================== Project CRUD ====================

    public String jsGetAllProjects() {
        return wrap(() -> projectService.getAllProjects());
    }

    public String jsGetProject(int id) {
        return wrap(() -> projectService.getProject(id));
    }

    public String jsCreateProject(String name, String pathsJson,
                                  String exportDir, String exportPrefix,
                                  String recyclePrefix) {
        return wrap(() -> {
            List<String> paths = JsonUtil.fromJson(pathsJson,
                    new TypeToken<List<String>>(){}.getType());
            return projectService.createProject(name,
                    paths.toArray(new String[0]),
                    exportDir, exportPrefix, recyclePrefix);
        });
    }

    public String jsUpdateProject(int id, String name, String pathsJson,
                                  String exportDir, String exportPrefix,
                                  String recyclePrefix) {
        return wrap(() -> {
            List<String> paths = JsonUtil.fromJson(pathsJson,
                    new TypeToken<List<String>>(){}.getType());
            return projectService.updateProject(id, name,
                    paths.toArray(new String[0]),
                    exportDir, exportPrefix, recyclePrefix);
        });
    }

    public String jsDeleteProject(int id) {
        return wrap(() -> {
            projectService.deleteProject(id);
            return "ok";
        });
    }

    // ==================== File Scanning ====================

    public String jsScanProject(int projectId, String dir) {
        return wrap(() -> {
            Project project = projectService.getProject(projectId);
            if (project == null) {
                throw new IllegalArgumentException("项目不存在: " + projectId);
            }
            return fileScanService.scanProject(project, dir != null && !dir.isEmpty() ? dir : null);
        });
    }

    public String jsRefreshScan(int projectId, String dir) {
        return wrap(() -> {
            Project project = projectService.getProject(projectId);
            if (project == null) {
                throw new IllegalArgumentException("项目不存在: " + projectId);
            }
            return fileScanService.refreshScan(project, dir != null && !dir.isEmpty() ? dir : null);
        });
    }

    // ==================== File Operations ====================

    public String jsExportFiles(String filePathsJson, int projectId) {
        return wrap(() -> {
            List<String> paths = JsonUtil.fromJson(filePathsJson,
                    new TypeToken<List<String>>(){}.getType());
            Project project = projectService.getProject(projectId);
            if (project == null) {
                throw new IllegalArgumentException("项目不存在: " + projectId);
            }
            return fileOpService.exportFiles(paths, project);
        });
    }

    public String jsRecycleFiles(String filePathsJson, int projectId) {
        return wrap(() -> {
            List<String> paths = JsonUtil.fromJson(filePathsJson,
                    new TypeToken<List<String>>(){}.getType());
            Project project = projectService.getProject(projectId);
            if (project == null) {
                throw new IllegalArgumentException("项目不存在: " + projectId);
            }
            return fileOpService.recycleFiles(paths, project);
        });
    }

    public String jsRollbackBatch(String batchId, String opType) {
        return wrap(() -> fileOpService.rollbackBatch(batchId, opType));
    }

    // ==================== Tags ====================

    public String jsAddTag(String filePath, String tagName, int projectId) {
        return wrap(() -> tagService.addTag(filePath, tagName, projectId));
    }

    public String jsRemoveTag(String filePath, String tagName, int projectId) {
        return wrap(() -> {
            tagService.removeTag(filePath, tagName, projectId);
            return "ok";
        });
    }

    public String jsGetTagsForFile(String filePath, int projectId) {
        return wrap(() -> tagService.getTagsForFile(filePath, projectId));
    }

    public String jsGetAllTags(int projectId) {
        return wrap(() -> tagService.getAllTags(projectId));
    }

    public String jsGetAllDistinctTagNames(int projectId) {
        return wrap(() -> tagService.getAllDistinctTagNames(projectId));
    }

    // ==================== Statistics ====================

    public String jsGetExportStatsByType(Integer projectId) {
        return wrap(() -> statsService.getExportStatsByType(projectId));
    }

    public String jsGetExportStatsByTag(Integer projectId) {
        return wrap(() -> statsService.getExportStatsByTag(projectId));
    }

    public String jsGetRecycleStatsByType(Integer projectId) {
        return wrap(() -> statsService.getRecycleStatsByType(projectId));
    }

    public String jsGetRecycleStatsByTag(Integer projectId) {
        return wrap(() -> statsService.getRecycleStatsByTag(projectId));
    }

    public String jsGetStatsSummary(Integer projectId) {
        return wrap(() -> statsService.getStatsSummary(projectId));
    }

    public String jsSetRecordHidden(String table, int recordId, boolean hidden) {
        return wrap(() -> {
            statsService.setRecordHidden(table, recordId, hidden);
            return "ok";
        });
    }

    public String jsSetRecordExcludeFromStats(String table, int recordId, boolean exclude) {
        return wrap(() -> {
            statsService.setRecordExcludeFromStats(table, recordId, exclude);
            return "ok";
        });
    }

    // ==================== Utility ====================

    /**
     * Opens a directory chooser dialog and returns the selected path.
     * Since this must run on the JavaFX thread, it uses Platform.runLater via a latch.
     */
    public String jsPickDirectory() {
        try {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("选择目录");
            File dir = chooser.showDialog(null);
            if (dir != null) {
                return JsonUtil.successResponse(dir.getAbsolutePath());
            }
            return JsonUtil.successResponse(null);
        } catch (Exception e) {
            return JsonUtil.errorResponse("PICKER_ERROR", e.getMessage());
        }
    }

    /**
     * Opens the file explorer at the given path (or the parent dir of a file).
     */
    public void jsOpenFileExplorer(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                return;
            }
            if (!file.isDirectory()) {
                file = file.getParentFile();
            }
            if (file != null) {
                java.awt.Desktop.getDesktop().open(file);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to open explorer: " + path, e);
        }
    }

    /**
     * Shows a native dialog message (for critical errors that need user attention).
     */
    public void jsShowMessage(String title, String msg, String type) {
        try {
            javafx.scene.control.Alert.AlertType alertType;
            switch (type != null ? type : "info") {
                case "warn" -> alertType = javafx.scene.control.Alert.AlertType.WARNING;
                case "error" -> alertType = javafx.scene.control.Alert.AlertType.ERROR;
                default -> alertType = javafx.scene.control.Alert.AlertType.INFORMATION;
            }
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to show message dialog", e);
        }
    }

    // ==================== Internal Helpers ====================

    /**
     * Wraps a callable operation, returning a JSON success/error envelope.
     */
    private String wrap(ThrowingSupplier<?> supplier) {
        try {
            Object result = supplier.get();
            return JsonUtil.successResponse(result);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Bridge call failed", e);
            return JsonUtil.errorResponse(e.getClass().getSimpleName(), e.getMessage());
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
