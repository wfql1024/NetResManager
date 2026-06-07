-- Migration V3 → V4
-- Principles:
--   1. record_id (timestamp-uuid-seq) replaces id + batch_id as PK
--   2. source_path/dest_path replace original_name/new_name (derivable from paths)
--   3. operation_time derived from record_id timestamp
--   4. file_size is raw; formatted size computed at display time
--   5. status removed; failed operations indicated by empty dest_path

CREATE TABLE operation_records_new (
    record_id       TEXT PRIMARY KEY,
    project_id      INTEGER NOT NULL,
    operation_type  TEXT NOT NULL,
    source_path     TEXT NOT NULL,
    dest_path       TEXT NOT NULL DEFAULT '',
    file_type       TEXT NOT NULL DEFAULT '',
    file_size       INTEGER NOT NULL DEFAULT 0,
    tags_json       TEXT NOT NULL DEFAULT '[]',
    success_time    TEXT NOT NULL DEFAULT '',
    hidden          INTEGER NOT NULL DEFAULT 0,
    exclude_from_stats INTEGER NOT NULL DEFAULT 0,
    deleted         INTEGER NOT NULL DEFAULT 0,
    rollback_failure_reason TEXT NOT NULL DEFAULT '',
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- Migrate data: record_id = batch_id || '-' || id
INSERT INTO operation_records_new
    (record_id, project_id, operation_type, source_path, dest_path,
     file_type, file_size, tags_json, success_time,
     hidden, exclude_from_stats, deleted, rollback_failure_reason)
SELECT
    batch_id || '-' || id,
    project_id,
    operation_type,
    source_path,
    CASE WHEN status = 'failed' THEN '' ELSE dest_path END,
    file_type,
    file_size,
    tags_json,
    success_time,
    hidden,
    exclude_from_stats,
    deleted,
    rollback_failure_reason
FROM operation_records;

-- Replace old table
DROP TABLE operation_records;
ALTER TABLE operation_records_new RENAME TO operation_records;

-- Recreate indexes
CREATE INDEX IF NOT EXISTS idx_op_project ON operation_records(project_id);
CREATE INDEX IF NOT EXISTS idx_op_type   ON operation_records(operation_type);
CREATE INDEX IF NOT EXISTS idx_op_hidden ON operation_records(hidden);
CREATE INDEX IF NOT EXISTS idx_op_deleted ON operation_records(deleted);
