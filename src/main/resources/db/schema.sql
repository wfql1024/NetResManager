-- NetResManager Database Schema
-- Version 2

PRAGMA journal_mode=WAL;
PRAGMA foreign_keys=ON;

-- Projects table
CREATE TABLE IF NOT EXISTS projects (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT    NOT NULL UNIQUE,
    paths           TEXT    NOT NULL DEFAULT '[]',
    export_dir      TEXT    NOT NULL DEFAULT '',
    export_prefix   TEXT    NOT NULL DEFAULT '',
    recycle_prefix  TEXT    NOT NULL DEFAULT '',
    created_at      TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at      TEXT    NOT NULL DEFAULT (datetime('now','localtime'))
);

-- Tags table: path-tag pairs
CREATE TABLE IF NOT EXISTS tags (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    file_path   TEXT    NOT NULL,
    tag_name    TEXT    NOT NULL,
    project_id  INTEGER NOT NULL,
    created_at  TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    UNIQUE(file_path, tag_name, project_id)
);

-- Unified operation records (export + recycle)
CREATE TABLE IF NOT EXISTS operation_records (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id      INTEGER NOT NULL,
    batch_id        TEXT    NOT NULL,
    operation_type  TEXT    NOT NULL,              -- 'export' or 'recycle'
    source_path     TEXT    NOT NULL,              -- original full path
    dest_path       TEXT    NOT NULL DEFAULT '',   -- destination/renamed path
    original_name   TEXT    NOT NULL,
    new_name        TEXT    NOT NULL DEFAULT '',
    file_type       TEXT    NOT NULL DEFAULT '',
    file_size       INTEGER NOT NULL DEFAULT 0,
    tags_json       TEXT    NOT NULL DEFAULT '[]', -- JSON array of tag names at operation time
    operation_time  TEXT    NOT NULL,              -- batch timestamp (when button pressed)
    success_time    TEXT    NOT NULL DEFAULT '',   -- when operation completed
    status          TEXT    NOT NULL DEFAULT 'done', -- done | failed | rolled_back
    hidden          INTEGER NOT NULL DEFAULT 0,    -- 1 = hidden from history
    exclude_from_stats INTEGER NOT NULL DEFAULT 0, -- 1 = excluded from statistics
    deleted         INTEGER NOT NULL DEFAULT 0,    -- 1 = soft-deleted (hidden from history and stats)
    rollback_failure_reason TEXT NOT NULL DEFAULT '', -- non-empty = rollback failed, can't retry; grey in history
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_tags_filepath   ON tags(file_path);
CREATE INDEX IF NOT EXISTS idx_tags_project    ON tags(project_id);
CREATE INDEX IF NOT EXISTS idx_tags_tagname    ON tags(tag_name);
CREATE INDEX IF NOT EXISTS idx_op_project      ON operation_records(project_id);
CREATE INDEX IF NOT EXISTS idx_op_batch        ON operation_records(batch_id);
CREATE INDEX IF NOT EXISTS idx_op_time         ON operation_records(operation_time);
CREATE INDEX IF NOT EXISTS idx_op_type         ON operation_records(operation_type);

-- Schema version tracking
PRAGMA user_version = 2;
