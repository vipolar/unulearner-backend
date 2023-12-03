package com.unulearner.backend.services.files.storage.tree;

import java.util.List;
import java.util.ArrayList;

public class TreeDirectoryNode {
    private String name;
    private List<TreeFileNode> files = new ArrayList<>();
    private List<TreeDirectoryNode> directories = new ArrayList<>();

    public TreeDirectoryNode(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public List<TreeDirectoryNode> getDirectories() {
        return directories;
    }
    public void setDirectories(List<TreeDirectoryNode> directories) {
        this.directories = directories;
    }

    public List<TreeFileNode> getFiles() {
        return files;
    }
    public void setFiles(List<TreeFileNode> files) {
        this.files = files;
    }
}