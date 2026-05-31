package com.netresmanager.model;

/**
 * Represents a tag assigned to a file path.
 */
public class TagPair {
    public int id;
    public String filePath;
    public String tagName;
    public int projectId;
    public String createdAt;

    public TagPair() {}

    public TagPair(String filePath, String tagName, int projectId) {
        this.filePath = filePath;
        this.tagName = tagName;
        this.projectId = projectId;
    }
}
