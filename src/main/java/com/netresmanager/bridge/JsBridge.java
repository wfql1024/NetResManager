package com.netresmanager.bridge;

import com.google.gson.reflect.TypeToken;
import com.netresmanager.model.*;
import com.netresmanager.service.*;
import com.netresmanager.util.JsonUtil;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;
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

    // ==================== Async Folder Size ====================

    /** Sets the script executor for pushing async results back to JS. */
    public void setScriptExecutor(Consumer<String> executor) {
        fileScanService.setOnFolderSizeResult(executor);
    }

    public String jsStartFolderSizeCalculation(String folderPathsJson) {
        try {
            List<String> paths = JsonUtil.fromJson(folderPathsJson,
                    new TypeToken<List<String>>(){}.getType());
            fileScanService.startAsyncFolderSizeCalculation(paths);
            return JsonUtil.successResponse("started");
        } catch (Exception e) {
            return JsonUtil.errorResponse("FOLDER_SIZE_ERROR", e.getMessage());
        }
    }

    public String jsCancelFolderSizeCalculations() {
        fileScanService.cancelFolderSizeCalculations();
        return JsonUtil.successResponse("cancelled");
    }

    // ==================== System Theme ====================

    /**
     * Detects the Windows system theme by reading the registry.
     * Returns "dark" if dark mode is enabled, "light" otherwise.
     */
    // ==================== App State Persistence ====================

    private static final java.util.prefs.Preferences PREFS =
        java.util.prefs.Preferences.userNodeForPackage(JsBridge.class);

    public void jsSaveLastPage(String page) {
        try { PREFS.put("lastPage", page); } catch (Exception e) {}
    }

    public String jsGetLastPage() {
        try { return JsonUtil.successResponse(PREFS.get("lastPage", "manage")); }
        catch (Exception e) { return JsonUtil.successResponse("manage"); }
    }

    // ==================== System Theme ====================

    public String jsGetSystemTheme() {
        try {
            Process p = Runtime.getRuntime().exec(
                new String[]{"reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme"});
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                // Looking for: "    AppsUseLightTheme    REG_DWORD    0x0"
                if (line.contains("AppsUseLightTheme")) {
                    // Value is 0 for dark, 1 for light
                    if (line.contains("0x0") || line.endsWith("0")) {
                        return JsonUtil.successResponse("dark");
                    }
                    return JsonUtil.successResponse("light");
                }
            }
            p.waitFor();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to detect system theme", e);
        }
        return JsonUtil.successResponse("light"); // default
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

    public String jsGetExportStatsByType(Integer projectId, boolean includeRollback) {
        return wrap(() -> statsService.getExportStatsByType(projectId, includeRollback));
    }
    public String jsGetExportStatsByTag(Integer projectId, boolean includeRollback) {
        return wrap(() -> statsService.getExportStatsByTag(projectId, includeRollback));
    }
    public String jsGetRecycleStatsByType(Integer projectId, boolean includeRollback) {
        return wrap(() -> statsService.getRecycleStatsByType(projectId, includeRollback));
    }
    public String jsGetRecycleStatsByTag(Integer projectId, boolean includeRollback) {
        return wrap(() -> statsService.getRecycleStatsByTag(projectId, includeRollback));
    }
    public String jsGetStatsSummary(Integer projectId, boolean includeRollback) {
        return wrap(() -> statsService.getStatsSummary(projectId, includeRollback));
    }

    // ==================== History ====================

    public String jsGetHistory(Integer projectId) {
        return wrap(() -> statsService.getHistory(projectId));
    }

    public String jsGetHiddenRecords(Integer projectId) {
        return wrap(() -> statsService.getHiddenRecords(projectId));
    }

    public String jsGetDeletedRecords(Integer projectId) {
        return wrap(() -> statsService.getDeletedRecords(projectId));
    }

    public String jsSetRecordHidden(String recordId, boolean hidden) {
        return wrap(() -> { statsService.setHidden(recordId, hidden); return "ok"; });
    }

    public String jsSetRecordExcludeFromStats(String recordId, boolean exclude) {
        return wrap(() -> { statsService.setExcludeFromStats(recordId, exclude); return "ok"; });
    }

    public String jsSetRecordDeleted(String recordId, boolean deleted) {
        return wrap(() -> { statsService.setDeleted(recordId, deleted); return "ok"; });
    }

    public String jsRollbackRecord(String recordId) {
        return wrap(() -> {
            String error = statsService.rollbackRecord(recordId);
            if (error != null) throw new RuntimeException(error);
            return "ok";
        });
    }

    // ==================== Export / Import ====================

    public String jsExportAllRecords() {
        return wrap(() -> statsService.getAllRecordsForExport());
    }

    public String jsImportRecords(String json) {
        return wrap(() -> {
            java.util.List<OperationRecord> records = JsonUtil.fromJson(json,
                    new TypeToken<java.util.List<OperationRecord>>(){}.getType());
            statsService.importRecords(records);
            return "导入成功: " + records.size() + " 条记录";
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

    /**
     * Opens a save-file dialog for exporting JSON data.
     * @param defaultName  suggested filename (e.g., "2026-06-07_18-30-00.json")
     * @param content      JSON string to write
     * @return success message or error
     */
    public String jsSaveExportFile(String defaultName, String content) {
        try {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("导出数据");
            chooser.setInitialFileName(defaultName != null ? defaultName : "export.json");
            chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("JSON 文件", "*.json"));
            // Default to desktop
            String desktop = System.getProperty("user.home") + java.io.File.separator + "Desktop";
            java.io.File desktopDir = new java.io.File(desktop);
            if (desktopDir.exists() && desktopDir.isDirectory()) {
                chooser.setInitialDirectory(desktopDir);
            }
            java.io.File file = chooser.showSaveDialog(null);
            if (file != null) {
                java.nio.file.Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
                return JsonUtil.successResponse("已导出到: " + file.getAbsolutePath());
            }
            return JsonUtil.successResponse(null);
        } catch (Exception e) {
            return JsonUtil.errorResponse("SAVE_ERROR", e.getMessage());
        }
    }

    /**
     * Opens a file-open dialog for importing JSON data.
     * @return file content as string, or null if cancelled
     */
    public String jsOpenImportFile() {
        try {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("导入数据");
            chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("JSON 文件", "*.json"));
            java.io.File file = chooser.showOpenDialog(null);
            if (file != null) {
                String content = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                return JsonUtil.successResponse(content);
            }
            return JsonUtil.successResponse(null);
        } catch (Exception e) {
            return JsonUtil.errorResponse("OPEN_ERROR", e.getMessage());
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
