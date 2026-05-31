-- Migration V1 → V2
-- Creates the unified operation_records table and migrates data from old tables.
-- Old export_records and recycle_records are kept for safety (not dropped).

-- Create the new unified table
CREATE TABLE IF NOT EXISTS operation_records (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id      INTEGER NOT NULL,
    batch_id        TEXT    NOT NULL,
    operation_type  TEXT    NOT NULL,
    source_path     TEXT    NOT NULL,
    dest_path       TEXT    NOT NULL DEFAULT '',
    original_name   TEXT    NOT NULL,
    new_name        TEXT    NOT NULL DEFAULT '',
    file_type       TEXT    NOT NULL DEFAULT '',
    file_size       INTEGER NOT NULL DEFAULT 0,
    tags_json       TEXT    NOT NULL DEFAULT '[]',
    operation_time  TEXT    NOT NULL,
    success_time    TEXT    NOT NULL DEFAULT '',
    status          TEXT    NOT NULL DEFAULT 'done',
    hidden          INTEGER NOT NULL DEFAULT 0,
    exclude_from_stats INTEGER NOT NULL DEFAULT 0,
    deleted         INTEGER NOT NULL DEFAULT 0,
    rollback_failure_reason TEXT NOT NULL DEFAULT '',
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- Migrate data from export_records (if table exists)
INSERT INTO operation_records
    (project_id, batch_id, operation_type, source_path, dest_path,
     original_name, new_name, file_type, file_size,
     operation_time, success_time, status, hidden, exclude_from_stats)
SELECT project_id, batch_id, 'export', source_path, dest_path,
       original_name, new_name, file_type, file_size,
       operated_at, operated_at, status, hidden, exclude_from_stats
FROM export_records
WHERE (SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='export_records') > 0;

-- Migrate data from recycle_records (if table exists)
INSERT INTO operation_records
    (project_id, batch_id, operation_type, source_path, dest_path,
     original_name, new_name, file_type, file_size,
     operation_time, success_time, status, hidden, exclude_from_stats)
SELECT project_id, batch_id, 'recycle', source_path, renamed_path,
       original_name, '', file_type, file_size,
       operated_at, operated_at, status, hidden, exclude_from_stats
FROM recycle_records
WHERE (SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='recycle_records') > 0;

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_op_project ON operation_records(project_id);
CREATE INDEX IF NOT EXISTS idx_op_batch   ON operation_records(batch_id);
CREATE INDEX IF NOT EXISTS idx_op_time    ON operation_records(operation_time);
CREATE INDEX IF NOT EXISTS idx_op_type    ON operation_records(operation_type);
