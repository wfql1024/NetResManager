package com.netresmanager.service;

import com.google.gson.reflect.TypeToken;
import com.netresmanager.db.DatabaseManager;
import com.netresmanager.model.Project;
import com.netresmanager.util.JsonUtil;
import com.netresmanager.util.PathValidator;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CRUD operations for Project entities.
 */
public class ProjectService {

    private static final Logger LOG = Logger.getLogger(ProjectService.class.getName());
    private final DatabaseManager db;

    public ProjectService() {
        this.db = DatabaseManager.getInstance();
    }

    /**
     * Creates a new project after validating paths.
     */
    public Project createProject(String name, String[] paths, String exportDir,
                                 String exportPrefix, String recyclePrefix) throws Exception {
        // Validate
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("项目名称不能为空");
        }
        List<String> pathList = paths != null ? List.of(paths) : List.of();
        List<String> invalid = PathValidator.validateDirectoryPaths(pathList);
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("以下路径无效或不存在: " + String.join(", ", invalid));
        }

        String pathsJson = JsonUtil.toJson(pathList);
        String sql = "INSERT INTO projects (name, paths, export_dir, export_prefix, recycle_prefix) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.setString(2, pathsJson);
            ps.setString(3, exportDir != null ? exportDir : "");
            ps.setString(4, exportPrefix != null ? exportPrefix : "");
            ps.setString(5, recyclePrefix != null ? recyclePrefix : "");
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return getProject(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to create project: " + name, e);
            throw e;
        }
        return null;
    }

    /**
     * Updates an existing project.
     */
    public Project updateProject(int id, String name, String[] paths, String exportDir,
                                 String exportPrefix, String recyclePrefix) throws Exception {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("项目名称不能为空");
        }
        List<String> pathList = paths != null ? List.of(paths) : List.of();
        String pathsJson = JsonUtil.toJson(pathList);
        String sql = "UPDATE projects SET name=?, paths=?, export_dir=?, export_prefix=?, recycle_prefix=?, " +
                     "updated_at=datetime('now','localtime') WHERE id=?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            ps.setString(2, pathsJson);
            ps.setString(3, exportDir != null ? exportDir : "");
            ps.setString(4, exportPrefix != null ? exportPrefix : "");
            ps.setString(5, recyclePrefix != null ? recyclePrefix : "");
            ps.setInt(6, id);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new IllegalArgumentException("项目不存在: id=" + id);
            }
            return getProject(id);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to update project: " + id, e);
            throw e;
        }
    }

    /**
     * Deletes a project and all associated records (CASCADE).
     */
    public void deleteProject(int id) throws SQLException {
        String sql = "DELETE FROM projects WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new IllegalArgumentException("项目不存在: id=" + id);
            }
            LOG.info("Project deleted: " + id);
        }
    }

    /**
     * Gets a single project by ID.
     */
    public Project getProject(int id) throws SQLException {
        String sql = "SELECT * FROM projects WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rowToProject(rs);
                }
            }
        }
        return null;
    }

    /**
     * Gets all projects.
     */
    public List<Project> getAllProjects() throws SQLException {
        List<Project> projects = new ArrayList<>();
        String sql = "SELECT * FROM projects ORDER BY name";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                projects.add(rowToProject(rs));
            }
        }
        return projects;
    }

    /**
     * Validates a list of directory path strings.
     * @return list of invalid paths (empty = all valid)
     */
    public List<String> validatePaths(List<String> paths) {
        return PathValidator.validateDirectoryPaths(paths);
    }

    private Project rowToProject(ResultSet rs) throws SQLException {
        Project p = new Project();
        p.id = rs.getInt("id");
        p.name = rs.getString("name");
        String pathsJson = rs.getString("paths");
        p.paths = JsonUtil.fromJson(pathsJson,
                new TypeToken<List<String>>(){}.getType());
        p.exportDir = rs.getString("export_dir");
        p.exportPrefix = rs.getString("export_prefix");
        p.recyclePrefix = rs.getString("recycle_prefix");
        p.createdAt = rs.getString("created_at");
        p.updatedAt = rs.getString("updated_at");
        return p;
    }
}
