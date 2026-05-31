package com.netresmanager.model;

import java.util.List;

/**
 * Groups FileEntry items by their source path for display in the file list.
 */
public class FileEntryGroup {
    public String sourcePath;
    public List<FileEntry> files;

    public FileEntryGroup() {}

    public FileEntryGroup(String sourcePath, List<FileEntry> files) {
        this.sourcePath = sourcePath;
        this.files = files;
    }
}
