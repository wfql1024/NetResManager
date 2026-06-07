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

    /** SQL fragment to exclude rollback-success records and failed records from normal stats */
    private static final String EXCLUDE_ROLLBACK =
        " AND dest_path != '' AND (rollback_failure_reason != 'success' OR rollback_failure_reason IS NULL OR rollback_failure_reason = '')";

    /** SQL fragment to include ONLY rollback-success records */
    private static final String ROLLBACK_ONLY =
        " AND dest_path != '' AND rollback_failure_reason = 'success'";

    public List<StatEntry> getExportStatsByType(Integer projectId, boolean includeRollback) throws SQLException {
        return mergeStats(
            getStatsByType("export", projectId, false),
            includeRollback ? getStatsByType("export", projectId, true) : null);
    }

    public List<StatEntry> getExportStatsByTag(Integer projectId, boolean includeRollback) throws SQLException {
        return mergeStats(
            getStatsByTag("export", projectId, false),
            includeRollback ? getStatsByTag("export", projectId, true) : null);
    }

    public List<StatEntry> getRecycleStatsByType(Integer projectId, boolean includeRollback) throws SQLException {
        return mergeStats(
            getStatsByType("recycle", projectId, false),
            includeRollback ? getStatsByType("recycle", projectId, true) : null);
    }

    public List<StatEntry> getRecycleStatsByTag(Integer projectId, boolean includeRollback) throws SQLException {
        return mergeStats(
            getStatsByTag("recycle", projectId, false),
            includeRollback ? getStatsByTag("recycle", projectId, true) : null);
    }

    public StatsSummary getStatsSummary(Integer projectId, boolean includeRollback) throws SQLException {
        StatsSummary s = new StatsSummary();
        String baseWhere = " WHERE dest_path != '' AND exclude_from_stats=0 AND deleted=0";
        String projFilter = (projectId != null ? " AND project_id=" + projectId : "");

        // Normal records
        String normalWhere = baseWhere + EXCLUDE_ROLLBACK + projFilter;
        querySummary(s, normalWhere);

        // Rollback records (if requested)
        if (includeRollback) {
            String rbWhere = baseWhere + ROLLBACK_ONLY + projFilter;
            StatsSummary rb = new StatsSummary();
            querySummary(rb, rbWhere);
            s.totalExported += rb.totalExported;
            s.totalRecycled += rb.totalRecycled;
            if (rb.uniqueFileTypes > s.uniqueFileTypes) s.uniqueFileTypes = rb.uniqueFileTypes;
        }

        s.totalProcessed = s.totalExported + s.totalRecycled;
        s.uniqueTags = countDistinctTagsFromJson(projectId, includeRollback);
        return s;
    }

    private void querySummary(StatsSummary s, String where) throws SQLException {
        String sql = "SELECT operation_type, COUNT(*), COUNT(DISTINCT file_type) FROM operation_records"
                   + where + " GROUP BY operation_type";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String t = rs.getString(1);
                int c = rs.getInt(2);
                int types = rs.getInt(3);
                if ("export".equals(t)) s.totalExported = c;
                else if ("recycle".equals(t)) s.totalRecycled = c;
                if (types > s.uniqueFileTypes) s.uniqueFileTypes = types;
            }
        }
    }

    /** Count distinct tag names from operation_records.tags_json */
    private int countDistinctTagsFromJson(Integer projectId, boolean includeRollback) throws SQLException {
        String baseWhere = " WHERE dest_path != '' AND exclude_from_stats=0 AND deleted=0";
        if (projectId != null) baseWhere += " AND project_id=" + projectId;

        java.util.Set<String> tagSet = new java.util.HashSet<>();
        // Normal records
        collectTags(tagSet, "SELECT tags_json FROM operation_records" + baseWhere + EXCLUDE_ROLLBACK);
        // Rollback records
        if (includeRollback) {
            collectTags(tagSet, "SELECT tags_json FROM operation_records" + baseWhere + ROLLBACK_ONLY);
        }
        return tagSet.size();
    }

    private void collectTags(java.util.Set<String> tagSet, String sql) throws SQLException {
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String json = rs.getString("tags_json");
                if (json != null && !json.isEmpty() && !"[]".equals(json)) {
                    try {
                        java.util.List<String> tags = JsonUtil.fromJson(json,
                            new com.google.gson.reflect.TypeToken<java.util.List<String>>(){}.getType());
                        if (tags != null) tagSet.addAll(tags);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    /**
     * Merges normal and rollback stats. Rollback entries get "(已撤销)" prefix.
     */
    private List<StatEntry> mergeStats(List<StatEntry> normal, List<StatEntry> rollback) {
        if (rollback == null || rollback.isEmpty()) return normal;
        for (StatEntry e : rollback) {
            e.category = "(已撤销)" + e.category;
        }
        normal.addAll(rollback);
        normal.sort((a, b) -> Integer.compare(b.count, a.count));
        return normal;
    }

    private List<StatEntry> getStatsByType(String opType, Integer projectId, boolean rollbackOnly) throws SQLException {
        String where = " WHERE operation_type=? AND dest_path != '' AND exclude_from_stats=0 AND deleted=0"
                     + (rollbackOnly ? ROLLBACK_ONLY : EXCLUDE_ROLLBACK);
        if (projectId != null) where += " AND project_id=?";
        String sql = "SELECT file_type AS category, COUNT(*) AS cnt, COALESCE(SUM(file_size), 0) AS total_size " +
                     "FROM operation_records" + where + " GROUP BY file_type ORDER BY cnt DESC";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, opType);
            if (projectId != null) ps.setInt(2, projectId);
            return mapStats(ps);
        }
    }

    private List<StatEntry> getStatsByTag(String opType, Integer projectId, boolean rollbackOnly) throws SQLException {
        String where = " WHERE operation_type=? AND dest_path != '' AND exclude_from_stats=0 AND deleted=0"
                     + (rollbackOnly ? ROLLBACK_ONLY : EXCLUDE_ROLLBACK);
        if (projectId != null) where += " AND project_id=?";

        java.util.Map<String, StatEntry> tagMap = new java.util.LinkedHashMap<>();
        long noTagCount = 0;
        long noTagSize = 0;

        String sql = "SELECT tags_json, file_size FROM operation_records" + where;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, opType);
            if (projectId != null) ps.setInt(2, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString("tags_json");
                    long size = rs.getLong("file_size");
                    java.util.List<String> tags = null;
                    if (json != null && !json.isEmpty() && !"[]".equals(json)) {
                        try {
                            tags = JsonUtil.fromJson(json,
                                new com.google.gson.reflect.TypeToken<java.util.List<String>>(){}.getType());
                        } catch (Exception e) { /* parse error, treat as no tags */ }
                    }
                    if (tags == null || tags.isEmpty()) {
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
        if (noTagCount > 0) {
            result.add(new StatEntry("(无标签)", (int) noTagCount, noTagSize));
        }
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

    // ==================== Hidden / Deleted Records ====================

    /**
     * Queries hidden records (hidden=1, deleted=0).
     */
    public List<OperationRecord> getHiddenRecords(Integer projectId) throws SQLException {
        String sql = "SELECT * FROM operation_records WHERE hidden=1 AND deleted=0";
        if (projectId != null) sql += " AND project_id=?";
        sql += " ORDER BY record_id DESC";
        return queryRecords(sql, projectId);
    }

    /**
     * Queries soft-deleted records (deleted=1).
     */
    public List<OperationRecord> getDeletedRecords(Integer projectId) throws SQLException {
        String sql = "SELECT * FROM operation_records WHERE deleted=1";
        if (projectId != null) sql += " AND project_id=?";
        sql += " ORDER BY record_id DESC";
        return queryRecords(sql, projectId);
    }

    private List<OperationRecord> queryRecords(String sql, Integer projectId) throws SQLException {
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

    // ==================== Export / Import ====================

    /**
     * Returns all non-deleted operation records (for JSON export/backup).
     */
    public List<OperationRecord> getAllRecordsForExport() throws SQLException {
        String sql = "SELECT * FROM operation_records WHERE deleted=0 ORDER BY record_id DESC";
        return queryRecords(sql, null);
    }

    /**
     * Imports records from JSON. For each record: if the id already exists, update it;
     * otherwise insert a new row. The caller's JSON supplies all field values.
     */
    public void importRecords(List<OperationRecord> records) throws SQLException {
        String upsertSql = "INSERT OR REPLACE INTO operation_records " +
            "(record_id, project_id, operation_type, source_path, dest_path, " +
            "file_type, file_size, tags_json, " +
            "success_time, hidden, exclude_from_stats, deleted, rollback_failure_reason) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            for (OperationRecord r : records) {
                ps.setString(1, r.recordId);
                ps.setInt(2, r.projectId);
                ps.setString(3, r.operationType);
                ps.setString(4, r.sourcePath);
                ps.setString(5, r.destPath != null ? r.destPath : "");
                ps.setString(6, r.fileType != null ? r.fileType : "");
                ps.setLong(7, r.fileSize);
                ps.setString(8, JsonUtil.toJson(r.tags != null ? r.tags : List.of()));
                ps.setString(9, r.successTime != null ? r.successTime : "");
                ps.setInt(10, r.hidden ? 1 : 0);
                ps.setInt(11, r.excludeFromStats ? 1 : 0);
                ps.setInt(12, r.deleted ? 1 : 0);
                ps.setString(13, r.rollbackFailureReason != null ? r.rollbackFailureReason : "");
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ==================== History ====================

    /**
     * Queries operation history for a project, grouped by batch (record_id prefix).
     * Excludes hidden and deleted records.
     */
    public List<OperationRecord> getHistory(Integer projectId) throws SQLException {
        String sql = "SELECT * FROM operation_records WHERE hidden=0 AND deleted=0";
        if (projectId != null) sql += " AND project_id=?";
        sql += " ORDER BY record_id DESC";

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
    public void setHidden(String recordId, boolean hidden) throws SQLException {
        String sql = "UPDATE operation_records SET hidden=? WHERE record_id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hidden ? 1 : 0);
            ps.setString(2, recordId);
            ps.executeUpdate();
        }
    }

    /** Set exclude_from_stats flag */
    public void setExcludeFromStats(String recordId, boolean exclude) throws SQLException {
        String sql = "UPDATE operation_records SET exclude_from_stats=? WHERE record_id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, exclude ? 1 : 0);
            ps.setString(2, recordId);
            ps.executeUpdate();
        }
    }

    /** Soft-delete a record */
    public void setDeleted(String recordId, boolean deleted) throws SQLException {
        String sql = "UPDATE operation_records SET deleted=? WHERE record_id=?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, deleted ? 1 : 0);
            ps.setString(2, recordId);
            ps.executeUpdate();
        }
    }

    /** Attempt rollback of a single record */
    public String rollbackRecord(String recordId) {
        return new FileOperationService().rollbackRecord(recordId);
    }

    private OperationRecord mapRecord(ResultSet rs) throws SQLException {
        OperationRecord r = new OperationRecord();
        r.recordId = rs.getString("record_id");
        r.projectId = rs.getInt("project_id");
        r.operationType = rs.getString("operation_type");
        r.sourcePath = rs.getString("source_path");
        r.destPath = rs.getString("dest_path");
        r.fileType = rs.getString("file_type");
        r.fileSize = rs.getLong("file_size");
        String tagsJson = rs.getString("tags_json");
        r.tags = JsonUtil.fromJson(tagsJson, new TypeToken<List<String>>(){}.getType());
        r.successTime = rs.getString("success_time");
        r.hidden = rs.getInt("hidden") != 0;
        r.excludeFromStats = rs.getInt("exclude_from_stats") != 0;
        r.deleted = rs.getInt("deleted") != 0;
        r.rollbackFailureReason = rs.getString("rollback_failure_reason");
        r.computeDerived();
        return r;
    }

}
