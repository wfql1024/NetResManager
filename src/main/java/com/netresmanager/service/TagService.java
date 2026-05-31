package com.netresmanager.service;

import com.netresmanager.db.DatabaseManager;
import com.netresmanager.model.TagPair;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages tags (path-tag pairs) for files.
 */
public class TagService {

    private static final Logger LOG = Logger.getLogger(TagService.class.getName());
    private final DatabaseManager db;

    public TagService() {
        this.db = DatabaseManager.getInstance();
    }

    /**
     * Adds a tag to a file path. Duplicates are silently ignored (UNIQUE constraint).
     */
    public TagPair addTag(String filePath, String tagName, int projectId) throws SQLException {
        if (filePath == null || tagName == null || tagName.isBlank()) {
            throw new IllegalArgumentException("文件路径和标签名不能为空");
        }
        String sql = "INSERT OR IGNORE INTO tags (file_path, tag_name, project_id) VALUES (?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filePath);
            ps.setString(2, tagName.trim());
            ps.setInt(3, projectId);
            ps.executeUpdate();

            // Fetch the inserted/existing tag
            String query = "SELECT * FROM tags WHERE file_path=? AND tag_name=? AND project_id=?";
            try (PreparedStatement qs = conn.prepareStatement(query)) {
                qs.setString(1, filePath);
                qs.setString(2, tagName.trim());
                qs.setInt(3, projectId);
                try (ResultSet rs = qs.executeQuery()) {
                    if (rs.next()) {
                        return rowToTag(rs);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Removes a specific tag from a file path.
     */
    public void removeTag(String filePath, String tagName, int projectId) throws SQLException {
        String sql = "DELETE FROM tags WHERE file_path=? AND tag_name=? AND project_id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filePath);
            ps.setString(2, tagName);
            ps.setInt(3, projectId);
            ps.executeUpdate();
        }
    }

    /**
     * Gets all tags for a specific file path.
     */
    public List<TagPair> getTagsForFile(String filePath, int projectId) throws SQLException {
        List<TagPair> tags = new ArrayList<>();
        String sql = "SELECT * FROM tags WHERE file_path=? AND project_id=? ORDER BY tag_name";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filePath);
            ps.setInt(2, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tags.add(rowToTag(rs));
                }
            }
        }
        return tags;
    }

    /**
     * Gets all tags for a project.
     */
    public List<TagPair> getAllTags(int projectId) throws SQLException {
        List<TagPair> tags = new ArrayList<>();
        String sql = "SELECT * FROM tags WHERE project_id=? ORDER BY tag_name, file_path";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tags.add(rowToTag(rs));
                }
            }
        }
        return tags;
    }

    /**
     * Gets all distinct tag names for a project.
     */
    public Set<String> getAllDistinctTagNames(int projectId) throws SQLException {
        Set<String> names = new HashSet<>();
        String sql = "SELECT DISTINCT tag_name FROM tags WHERE project_id=? ORDER BY tag_name";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("tag_name"));
                }
            }
        }
        return names;
    }

    /**
     * Gets files that have a specific tag in a project.
     */
    public List<String> getFilesByTag(String tagName, int projectId) throws SQLException {
        List<String> files = new ArrayList<>();
        String sql = "SELECT DISTINCT file_path FROM tags WHERE tag_name=? AND project_id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tagName);
            ps.setInt(2, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    files.add(rs.getString("file_path"));
                }
            }
        }
        return files;
    }

    /**
     * Deletes all tags for a given file path AND any sub-paths (for folder cascade).
     * Called when a file/folder is successfully exported or recycled.
     */
    public void deleteTagsForPath(String filePath, int projectId) throws SQLException {
        // Delete the exact path and any paths that start with filePath + separator
        // (i.e., children of a folder)
        String sql = "DELETE FROM tags WHERE project_id=? AND (file_path=? OR file_path LIKE ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            ps.setString(2, filePath);
            ps.setString(3, filePath + File.separator + "%");
            int deleted = ps.executeUpdate();
            LOG.info("Deleted " + deleted + " tags for path: " + filePath);
        }
    }

    private TagPair rowToTag(ResultSet rs) throws SQLException {
        TagPair t = new TagPair();
        t.id = rs.getInt("id");
        t.filePath = rs.getString("file_path");
        t.tagName = rs.getString("tag_name");
        t.projectId = rs.getInt("project_id");
        t.createdAt = rs.getString("created_at");
        return t;
    }
}
