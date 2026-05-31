package com.netresmanager.model;

/**
 * Summary statistics for a project or across all projects.
 */
public class StatsSummary {
    public long totalProcessed;
    public long totalExported;
    public long totalRecycled;
    public int uniqueFileTypes;
    public int uniqueTags;

    public StatsSummary() {}
}
