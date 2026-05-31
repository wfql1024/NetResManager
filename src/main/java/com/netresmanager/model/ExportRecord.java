package com.netresmanager.model;

/**
 * Represents a single export operation record from the database.
 */
public class ExportRecord {
    public int id;
    public int projectId;
    public String batchId;
    public String sourcePath;
    public String destPath;
    public String originalName;
    public String newName;
    public String fileType;
    public long fileSize;
    public String status;
    public boolean hidden;
    public boolean excludeFromStats;
    public String operatedAt;

    public ExportRecord() {}
}
