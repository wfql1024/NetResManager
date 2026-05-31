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
        return wrap(() -> { projectService.deleteProject(id); return "ok"; });
    }

    // ==================== File Scanning ====================

    public String jsScanProject(int projectId, String dir) {
        return wrap(() -> {
            Project project = projectService.getProject(projectId);
            if (project == null) throw new IllegalArgumentException("项目不存在: " + projectId);
            return fileScanService.scanProject(project, dir != null && !dir.isEmpty() ? dir : null);
        });
    }

    public String jsRefreshScan(int projectId, String dir) {
        return wrap(() -> {
            Project project = projectService.getProject(projectId);
            if (project == null) throw new IllegalArgumentException("项目不存在: " + projectId);
            return fileScanService.refreshScan(project, dir != null && !dir.isEmpty() ? dir : null);
        });
    }

    // ==================== File Operations ====================

    public String jsExportFiles(String filePathsJson, int projectId) {
        return wrap(() -> {
            List<String> paths = JsonUtil.fromJson(filePathsJson,
                    new TypeToken<List<String>>(){}.getType());
            Project project = projectService.getProject(projectId);
            if (project == null) throw new IllegalArgumentException("项目不存在: " + projectId);
            return fileOpService.exportFiles(paths, project);
        });
    }

    public String jsRecycleFiles(String filePathsJson, int projectId) {
        return wrap(() -> {
            List<String> paths = JsonUtil.fromJson(filePathsJson,
                    new TypeToken<List<String>>(){}.getType());
            Project project = projectService.getProject(projectId);
            if (project == null) throw new IllegalArgumentException("项目不存在: " + projectId);
            return fileOpService.recycleFiles(paths, project);
        });
    }

    // ==================== Tags ====================

    public String jsAddTag(String filePath, String tagName, int projectId) {
        return wrap(() -> tagService.addTag(filePath, tagName, projectId));
    }

    public String jsRemoveTag(String filePath, String tagName, int projectId) {
        return wrap(() -> { tagService.removeTag(filePath, tagName, projectId); return "ok"; });
    }

    public String jsGetTagsForFile(String filePath, int projectId) {
        return wrap(() -> tagService.getTagsForFile(filePath, projectId));
    }

    public String jsGetAllTags(int projectId) {
        return wrap(() -> tagService.getAllTags(projectId));
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

    // ==================== History ====================

    public String jsGetHistory(Integer projectId) {
        return wrap(() -> statsService.getHistory(projectId));
    }

    public String jsSetRecordHidden(int recordId, boolean hidden) {
        return wrap(() -> { statsService.setHidden(recordId, hidden); return "ok"; });
    }

    public String jsSetRecordExcludeFromStats(int recordId, boolean exclude) {
        return wrap(() -> { statsService.setExcludeFromStats(recordId, exclude); return "ok"; });
    }

    public String jsSetRecordDeleted(int recordId, boolean deleted) {
        return wrap(() -> { statsService.setDeleted(recordId, deleted); return "ok"; });
    }

    public String jsRollbackRecord(int recordId) {
        return wrap(() -> {
            String error = statsService.rollbackRecord(recordId);
            if (error != null) return "{\"error\":\"" + error + "\"}";
            return "{\"success\":true}";
        });
    }

    // ==================== Utility ====================

    public String jsPickDirectory() {
        try {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("选择目录");
            File dir = chooser.showDialog(null);
            return dir != null ? JsonUtil.successResponse(dir.getAbsolutePath())
                              : JsonUtil.successResponse(null);
        } catch (Exception e) {
            return JsonUtil.errorResponse("PICKER_ERROR", e.getMessage());
        }
    }

    public void jsOpenFileExplorer(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) return;
            if (!file.isDirectory()) file = file.getParentFile();
            if (file != null) java.awt.Desktop.getDesktop().open(file);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to open explorer: " + path, e);
        }
    }

    public void jsShowMessage(String title, String msg, String type) {
        try {
            javafx.scene.control.Alert.AlertType alertType = switch (type != null ? type : "info") {
                case "warn" -> javafx.scene.control.Alert.AlertType.WARNING;
                case "error" -> javafx.scene.control.Alert.AlertType.ERROR;
                default -> javafx.scene.control.Alert.AlertType.INFORMATION;
            };
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to show message dialog", e);
        }
    }

    // ==================== Internal ====================

    private String wrap(ThrowingSupplier<?> supplier) {
        try {
            return JsonUtil.successResponse(supplier.get());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Bridge call failed", e);
            return JsonUtil.errorResponse(e.getClass().getSimpleName(), e.getMessage());
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> { T get() throws Exception; }
}
