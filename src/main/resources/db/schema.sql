-- NetResManager Database Schema
-- Version 1

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

-- Export operation records
CREATE TABLE IF NOT EXISTS export_records (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id      INTEGER NOT NULL,
    batch_id        TEXT    NOT NULL,
    source_path     TEXT    NOT NULL,
    dest_path       TEXT    NOT NULL,
    original_name   TEXT    NOT NULL,
    new_name        TEXT    NOT NULL,
    file_type       TEXT    NOT NULL DEFAULT '',
    file_size       INTEGER NOT NULL DEFAULT 0,
    status          TEXT    NOT NULL DEFAULT 'pending',
    hidden          INTEGER NOT NULL DEFAULT 0,
    exclude_from_stats INTEGER NOT NULL DEFAULT 0,
    operated_at     TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- Recycle operation records
CREATE TABLE IF NOT EXISTS recycle_records (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id      INTEGER NOT NULL,
    batch_id        TEXT    NOT NULL,
    source_path     TEXT    NOT NULL,
    original_name   TEXT    NOT NULL,
    renamed_path    TEXT    NOT NULL,
    file_type       TEXT    NOT NULL DEFAULT '',
    file_size       INTEGER NOT NULL DEFAULT 0,
    status          TEXT    NOT NULL DEFAULT 'pending',
    hidden          INTEGER NOT NULL DEFAULT 0,
    exclude_from_stats INTEGER NOT NULL DEFAULT 0,
    operated_at     TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_tags_filepath  ON tags(file_path);
CREATE INDEX IF NOT EXISTS idx_tags_project   ON tags(project_id);
CREATE INDEX IF NOT EXISTS idx_tags_tagname   ON tags(tag_name);
CREATE INDEX IF NOT EXISTS idx_export_project ON export_records(project_id);
CREATE INDEX IF NOT EXISTS idx_export_batch   ON export_records(batch_id);
CREATE INDEX IF NOT EXISTS idx_recycle_project ON recycle_records(project_id);
CREATE INDEX IF NOT EXISTS idx_recycle_batch   ON recycle_records(batch_id);

-- Schema version tracking
PRAGMA user_version = 1;
