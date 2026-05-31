-- NetResManager Database Schema V2

PRAGMA journal_mode=WAL;
PRAGMA foreign_keys=ON;

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

CREATE TABLE IF NOT EXISTS tags (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    file_path   TEXT    NOT NULL,
    tag_name    TEXT    NOT NULL,
    project_id  INTEGER NOT NULL,
    created_at  TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    UNIQUE(file_path, tag_name, project_id)
);

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

CREATE INDEX IF NOT EXISTS idx_tags_filepath ON tags(file_path);
CREATE INDEX IF NOT EXISTS idx_tags_project  ON tags(project_id);
CREATE INDEX IF NOT EXISTS idx_tags_tagname  ON tags(tag_name);
CREATE INDEX IF NOT EXISTS idx_op_project    ON operation_records(project_id);
CREATE INDEX IF NOT EXISTS idx_op_batch      ON operation_records(batch_id);
CREATE INDEX IF NOT EXISTS idx_op_time       ON operation_records(operation_time);
CREATE INDEX IF NOT EXISTS idx_op_type       ON operation_records(operation_type);

PRAGMA user_version = 2;
