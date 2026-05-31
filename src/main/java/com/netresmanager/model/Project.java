package com.netresmanager.model;

import java.util.List;

/**
 * Represents a project — the top-level organizational unit.
 */
public class Project {
    public int id;
    public String name;
    public List<String> paths;
    public String exportDir;
    public String exportPrefix;
    public String recyclePrefix;
    public String createdAt;
    public String updatedAt;

    public Project() {}

    public Project(String name, List<String> paths, String exportDir,
                   String exportPrefix, String recyclePrefix) {
        this.name = name;
        this.paths = paths;
        this.exportDir = exportDir;
        this.exportPrefix = exportPrefix;
        this.recyclePrefix = recyclePrefix;
    }

    @Override
    public String toString() {
        return name + " (" + (paths != null ? paths.size() : 0) + " paths)";
    }
}
