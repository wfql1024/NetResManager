package com.netresmanager.service;

import com.netresmanager.db.DatabaseManager;
import com.netresmanager.model.StatEntry;
import com.netresmanager.model.StatsSummary;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Aggregation queries for statistics page (pie charts, stats list).
 * Supports hidden/exclude_from_stats filtering and per-project or all-projects queries.
 */
public class StatisticsService {

    private static final Logger LOG = Logger.getLogger(StatisticsService.class.getName());
    private final DatabaseManager db;

    public StatisticsService() {
        this.db = DatabaseManager.getInstance();
    }

    /**
     * Aggregates export records by file type.
     */
    public List<StatEntry> getExportStatsByType(Integer projectId) throws SQLException {
        String sql;
        if (projectId != null) {
            sql = "SELECT file_type AS category, COUNT(*) AS cnt, COALESCE(SUM(file_size), 0) AS total_size " +
                  "FROM export_records WHERE project_id=? AND status='done' AND exclude_from_stats=0 " +
                  "GROUP BY file_type ORDER BY cnt DESC";
        } else {
            sql = "SELECT file_type AS category, COUNT(*) AS cnt, COALESCE(SUM(file_size), 0) AS total_size " +
                  "FROM export_records WHERE status='done' AND exclude_from_stats=0 " +
                  "GROUP BY file_type ORDER BY cnt DESC";
        }
        return queryStats(sql, projectId);
    }

    /**
     * Aggregates export records by tag (joins export_records with tags).
     */
    public List<StatEntry> getExportStatsByTag(Integer projectId) throws SQLException {
        String sql;
        if (projectId != null) {
            sql = "SELECT COALESCE(t.tag_name, '(无标签)') AS category, COUNT(DISTINCT e.id) AS cnt, " +
                  "COALESCE(SUM(e.file_size), 0) AS total_size " +
                  "FROM export_records e LEFT JOIN tags t ON e.source_path = t.file_path AND e.project_id = t.project_id " +
                  "WHERE e.project_id=? AND e.status='done' AND e.exclude_from_stats=0 " +
                  "GROUP BY t.tag_name ORDER BY cnt DESC";
        } else {
            sql = "SELECT COALESCE(t.tag_name, '(无标签)') AS category, COUNT(DISTINCT e.id) AS cnt, " +
                  "COALESCE(SUM(e.file_size), 0) AS total_size " +
                  "FROM export_records e LEFT JOIN tags t ON e.source_path = t.file_path AND e.project_id = t.project_id " +
                  "WHERE e.status='done' AND e.exclude_from_stats=0 " +
                  "GROUP BY t.tag_name ORDER BY cnt DESC";
        }
        return queryStats(sql, projectId);
    }

    /**
     * Aggregates recycle records by file type.
     */
    public List<StatEntry> getRecycleStatsByType(Integer projectId) throws SQLException {
        String sql;
        if (projectId != null) {
            sql = "SELECT file_type AS category, COUNT(*) AS cnt, COALESCE(SUM(file_size), 0) AS total_size " +
                  "FROM recycle_records WHERE project_id=? AND status='done' AND exclude_from_stats=0 " +
                  "GROUP BY file_type ORDER BY cnt DESC";
        } else {
            sql = "SELECT file_type AS category, COUNT(*) AS cnt, COALESCE(SUM(file_size), 0) AS total_size " +
                  "FROM recycle_records WHERE status='done' AND exclude_from_stats=0 " +
                  "GROUP BY file_type ORDER BY cnt DESC";
        }
        return queryStats(sql, projectId);
    }

    /**
     * Aggregates recycle records by tag.
     */
    public List<StatEntry> getRecycleStatsByTag(Integer projectId) throws SQLException {
        String sql;
        if (projectId != null) {
            sql = "SELECT COALESCE(t.tag_name, '(无标签)') AS category, COUNT(DISTINCT r.id) AS cnt, " +
                  "COALESCE(SUM(r.file_size), 0) AS total_size " +
                  "FROM recycle_records r LEFT JOIN tags t ON r.source_path = t.file_path AND r.project_id = t.project_id " +
                  "WHERE r.project_id=? AND r.status='done' AND r.exclude_from_stats=0 " +
                  "GROUP BY t.tag_name ORDER BY cnt DESC";
        } else {
            sql = "SELECT COALESCE(t.tag_name, '(无标签)') AS category, COUNT(DISTINCT r.id) AS cnt, " +
                  "COALESCE(SUM(r.file_size), 0) AS total_size " +
                  "FROM recycle_records r LEFT JOIN tags t ON r.source_path = t.file_path AND r.project_id = t.project_id " +
                  "WHERE r.status='done' AND r.exclude_from_stats=0 " +
                  "GROUP BY t.tag_name ORDER BY cnt DESC";
        }
        return queryStats(sql, projectId);
    }

    /**
     * Gets a summary of statistics for a project or all projects.
     */
    public StatsSummary getStatsSummary(Integer projectId) throws SQLException {
        StatsSummary s = new StatsSummary();

        // Total projects
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM projects");
            s.totalProjects = rs.getInt(1);

            // Total exported
            String exSql = projectId != null
                    ? "SELECT COUNT(*) FROM export_records WHERE project_id=? AND status='done' AND exclude_from_stats=0"
                    : "SELECT COUNT(*) FROM export_records WHERE status='done' AND exclude_from_stats=0";
            try (PreparedStatement ps = conn.prepareStatement(exSql)) {
                if (projectId != null) ps.setInt(1, projectId);
                rs = ps.executeQuery();
                s.totalExported = rs.getInt(1);
            }

            // Total recycled
            String reSql = projectId != null
                    ? "SELECT COUNT(*) FROM recycle_records WHERE project_id=? AND status='done' AND exclude_from_stats=0"
                    : "SELECT COUNT(*) FROM recycle_records WHERE status='done' AND exclude_from_stats=0";
            try (PreparedStatement ps = conn.prepareStatement(reSql)) {
                if (projectId != null) ps.setInt(1, projectId);
                rs = ps.executeQuery();
                s.totalRecycled = rs.getInt(1);
            }

            // Unique file types
            String typeSql = projectId != null
                    ? "SELECT COUNT(DISTINCT file_type) FROM (SELECT file_type FROM export_records WHERE project_id=? AND status='done' AND exclude_from_stats=0 UNION ALL SELECT file_type FROM recycle_records WHERE project_id=? AND status='done' AND exclude_from_stats=0)"
                    : "SELECT COUNT(DISTINCT file_type) FROM (SELECT file_type FROM export_records WHERE status='done' AND exclude_from_stats=0 UNION ALL SELECT file_type FROM recycle_records WHERE status='done' AND exclude_from_stats=0)";
            try (PreparedStatement ps = conn.prepareStatement(typeSql)) {
                if (projectId != null) { ps.setInt(1, projectId); ps.setInt(2, projectId); }
                rs = ps.executeQuery();
                s.uniqueFileTypes = rs.getInt(1);
            }

            // Unique tags
            String tagSql = projectId != null
                    ? "SELECT COUNT(DISTINCT tag_name) FROM tags WHERE project_id=?"
                    : "SELECT COUNT(DISTINCT tag_name) FROM tags";
            try (PreparedStatement ps = conn.prepareStatement(tagSql)) {
                if (projectId != null) ps.setInt(1, projectId);
                rs = ps.executeQuery();
                s.uniqueTags = rs.getInt(1);
            }
        }
        return s;
    }

    /**
     * Sets a record's visibility status.
     */
    public void setRecordHidden(String table, int recordId, boolean hidden) throws SQLException {
        String sql = "UPDATE " + table + " SET hidden=? WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hidden ? 1 : 0);
            ps.setInt(2, recordId);
            ps.executeUpdate();
        }
    }

    /**
     * Sets a record's exclude_from_stats status.
     */
    public void setRecordExcludeFromStats(String table, int recordId, boolean exclude) throws SQLException {
        String sql = "UPDATE " + table + " SET exclude_from_stats=? WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, exclude ? 1 : 0);
            ps.setInt(2, recordId);
            ps.executeUpdate();
        }
    }

    private List<StatEntry> queryStats(String sql, Integer projectId) throws SQLException {
        List<StatEntry> entries = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (projectId != null) {
                ps.setInt(1, projectId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String category = rs.getString("category");
                    int cnt = rs.getInt("cnt");
                    long totalSize = rs.getLong("total_size");
                    entries.add(new StatEntry(
                            category != null ? category : "(无标签)",
                            cnt,
                            totalSize));
                }
            }
        }
        return entries;
    }
}
