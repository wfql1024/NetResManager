package com.netresmanager.model;

/**
 * Represents a single recycle operation record from the database.
 */
public class RecycleRecord {
    public int id;
    public int projectId;
    public String batchId;
    public String sourcePath;
    public String originalName;
    public String renamedPath;
    public String fileType;
    public long fileSize;
    public String status;
    public boolean hidden;
    public boolean excludeFromStats;
    public String operatedAt;

    public RecycleRecord() {}
}
