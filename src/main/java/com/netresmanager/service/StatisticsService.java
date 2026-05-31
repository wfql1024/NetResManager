package com.netresmanager.service;

import com.google.gson.reflect.TypeToken;
import com.netresmanager.db.DatabaseManager;
import com.netresmanager.model.OperationRecord;
import com.netresmanager.model.StatEntry;
import com.netresmanager.model.StatsSummary;
import com.netresmanager.util.JsonUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Statistics and history queries against unified operation_records.
 */
public class StatisticsService {

    private static final Logger LOG = Logger.getLogger(StatisticsService.class.getName());
    private final DatabaseManager db;

    public StatisticsService() {
        this.db = DatabaseManager.getInstance();
    }

    // ==================== Statistics ====================

    public List<StatEntry> getExportStatsByType(Integer projectId) throws SQLException {
        return getStatsByType("export", projectId);
    }

    public List<StatEntry> getExportStatsByTag(Integer projectId) throws SQLException {
        return getStatsByTag("export", projectId);
    }

    public List<StatEntry> getRecycleStatsByType(Integer projectId) throws SQLException {
        return getStatsByType("recycle", projectId);
    }

    public List<StatEntry> getRecycleStatsByTag(Integer projectId) throws SQLException {
        return getStatsByTag("recycle", projectId);
    }

    public StatsSummary getStatsSummary(Integer projectId) throws SQLException {
        StatsSummary s = new StatsSummary();
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {

            String baseWhere = "WHERE status='done' AND exclude_from_stats=0 AND deleted=0";
            if (projectId != null) baseWhere += " AND project_id=" + projectId;

            // Total processed = export + recycle done records
            ResultSet rs = stmt.executeQuery(
                "SELECT operation_type, COUNT(*) FROM operation_records " + baseWhere +
                " GROUP BY operation_type");
            while (rs.next()) {
                String t = rs.getString(1);
                int c = rs.getInt(2);
                if ("export".equals(t)) s.totalExported = c;
                else if ("recycle".equals(t)) s.totalRecycled = c;
            }
            s.totalProcessed = s.totalExported + s.totalRecycled;

            rs = stmt.executeQuery("SELECT COUNT(DISTINCT file_type) FROM operation_records " + baseWhere);
            s.uniqueFileTypes = rs.getInt(1);

            // Count unique tags from tags_json field
            s.uniqueTags = countDistinctTagsFromJson(conn, projectId);
        }
        return s;
    }

    /** Count distinct tag names from operation_records.tags_json */
    private int countDistinctTagsFromJson(Connection conn, Integer projectId) throws SQLException {
        String sql = "SELECT tags_json FROM operation_records " +
                     "WHERE status='done' AND exclude_from_stats=0 AND deleted=0 AND tags_json != '[]'";
        if (projectId != null) sql += " AND project_id=" + projectId;

        java.util.Set<String> tagSet = new java.util.HashSet<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String json = rs.getString("tags_json");
                if (json != null && !json.isEmpty() && !"[]".equals(json)) {
                    try {
                        String[] tags = JsonUtil.fromJson(json, String[].class);
                        if (tags != null) for (String t : tags) tagSet.add(t);
                    } catch (Exception ignored) {}
                }
            }
        }
        return tagSet.size();
    }

    private List<StatEntry> getStatsByType(String opType, Integer projectId) throws SQLException {
        String sql = "SELECT file_type AS category, COUNT(*) AS cnt, COALESCE(SUM(file_size), 0) AS total_size " +
                     "FROM operation_records " +
                     "WHERE operation_type=? AND status='done' AND exclude_from_stats=0 AND deleted=0";
        if (projectId != null) sql += " AND project_id=?";
        sql += " GROUP BY file_type ORDER BY cnt DESC";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, opType);
            if (projectId != null) ps.setInt(2, projectId);
            return mapStats(ps);
        }
    }

    private List<StatEntry> getStatsByTag(String opType, Integer projectId) throws SQLException {
        // Query all records (including those without tags)
        String sql = "SELECT tags_json, file_size FROM operation_records " +
                     "WHERE operation_type=? AND status='done' AND exclude_from_stats=0 AND deleted=0";
        if (projectId != null) sql += " AND project_id=?";

        java.util.Map<String, StatEntry> tagMap = new java.util.LinkedHashMap<>();
        long noTagCount = 0;
        long noTagSize = 0;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, opType);
            if (projectId != null) ps.setInt(2, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString("tags_json");
                    long size = rs.getLong("file_size");
                    String[] tags = null;
                    if (json != null && !json.isEmpty() && !"[]".equals(json)) {
                        try {
                            tags = JsonUtil.fromJson(json, String[].class);
                        } catch (Exception e) { /* parse error, treat as no tags */ }
                    }
                    if (tags == null || tags.length == 0) {
                        noTagCount++;
                        noTagSize += size;
                    } else {
                        for (String tag : tags) {
                            StatEntry entry = tagMap.get(tag);
                            if (entry == null) {
                                entry = new StatEntry(tag, 0, 0);
                                tagMap.put(tag, entry);
                            }
                            entry.count++;
                            entry.totalSize += size;
                        }
                    }
                }
            }
        }

        List<StatEntry> result = new java.util.ArrayList<>(tagMap.values());
        // Always add (无标签) if any untagged records exist
        if (noTagCount > 0) {
            result.add(new StatEntry("(无标签)", (int) noTagCount, noTagSize));
        }
        // Recompute formatted fields after aggregation
        for (StatEntry e : result) {
            e.totalSizeFormatted = formatSize(e.totalSize);
            e.totalSizeDisplay = String.format("%,d", e.totalSize) + " (" + e.totalSizeFormatted + ")";
        }
        result.sort((a, b) -> Integer.compare(b.count, a.count));
        return result;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double v = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        while (v >= 1024 && idx < units.length - 1) { v /= 1024; idx++; }
        return String.format("%.1f %s", v, units[idx]);
    }

    private List<StatEntry> mapStats(PreparedStatement ps) throws SQLException {
        List<StatEntry> entries = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                entries.add(new StatEntry(
                        rs.getString("category") != null ? rs.getString("category") : "(无标签)",
                        rs.getInt("cnt"),
                        rs.getLong("total_size")));
            }
        }
        return entries;
    }

    // ==================== History ====================

    /**
     * Queries operation history for a project, grouped by operation_time (batch).
     * Excludes hidden and deleted records.
     */
    public List<OperationRecord> getHistory(Integer projectId) throws SQLException {
        String sql = "SELECT * FROM operation_records WHERE hidden=0 AND deleted=0";
        if (projectId != null) sql += " AND project_id=?";
        sql += " ORDER BY operation_time DESC, id ASC";

        List<OperationRecord> records = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (projectId != null) ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRecord(rs));
                }
            }
        }
        return records;
    }

    /** Set hidden flag */
    public void setHidden(int recordId, boolean hidden) throws SQLException {
        String sql = "UPDATE operation_records SET hidden=? WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hidden ? 1 : 0);
            ps.setInt(2, recordId);
            ps.executeUpdate();
        }
    }

    /** Set exclude_from_stats flag */
    public void setExcludeFromStats(int recordId, boolean exclude) throws SQLException {
        String sql = "UPDATE operation_records SET exclude_from_stats=? WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, exclude ? 1 : 0);
            ps.setInt(2, recordId);
            ps.executeUpdate();
        }
    }

    /** Soft-delete a record */
    public void setDeleted(int recordId, boolean deleted) throws SQLException {
        String sql = "UPDATE operation_records SET deleted=? WHERE id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, deleted ? 1 : 0);
            ps.setInt(2, recordId);
            ps.executeUpdate();
        }
    }

    /** Attempt rollback of a single record */
    public String rollbackRecord(int recordId) {
        // Delegated to FileOperationService which has the actual rollback logic
        return new FileOperationService().rollbackRecord(recordId);
    }

    private OperationRecord mapRecord(ResultSet rs) throws SQLException {
        OperationRecord r = new OperationRecord();
        r.id = rs.getInt("id");
        r.projectId = rs.getInt("project_id");
        r.batchId = rs.getString("batch_id");
        r.operationType = rs.getString("operation_type");
        r.sourcePath = rs.getString("source_path");
        r.destPath = rs.getString("dest_path");
        r.originalName = rs.getString("original_name");
        r.newName = rs.getString("new_name");
        r.fileType = rs.getString("file_type");
        r.fileSize = rs.getLong("file_size");
        r.fileSizeFormatted = formatSize(r.fileSize);
        String tagsJson = rs.getString("tags_json");
        r.tags = JsonUtil.fromJson(tagsJson, new TypeToken<List<String>>(){}.getType());
        r.operationTime = rs.getString("operation_time");
        r.successTime = rs.getString("success_time");
        r.status = rs.getString("status");
        r.hidden = rs.getInt("hidden") != 0;
        r.excludeFromStats = rs.getInt("exclude_from_stats") != 0;
        r.deleted = rs.getInt("deleted") != 0;
        r.rollbackFailureReason = rs.getString("rollback_failure_reason");
        return r;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char prefix = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), prefix);
    }
}
