package com.unulearner.backend.storage.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "backend.storage")
public class StorageProperties {
    /**
     *
     */
    private String rootDirectory = "uploads";

    public String getRootDirectory() {
        return rootDirectory;
    }

    public void setRootDirectory(String rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    /**
     *
     */
    private String rootDirectoryDescription = "Root directory description...";

    public String getRootDirectoryDescription() {
        return rootDirectoryDescription;
    }

    public void setRootDirectoryDescription(String rootDirectoryDescription) {
        this.rootDirectoryDescription = rootDirectoryDescription;
    }

    /**
     *
     */
    private String recoveredFileDescription = "This StorageTreeNode was created automatically upon file recovery and is in need of immediate human attention!";

    public String getRecoveredFileDescription() {
        return recoveredFileDescription;
    }

    public void setRecoveredFileDescription(String recoveredFileDescription) {
        this.recoveredFileDescription = recoveredFileDescription;
    }

    /**
     *
     */
    private String recoveredDirectoryDescription = "This StorageTreeNode was created automatically upon directory recovery and is in need of immediate human attention!";

    public String getRecoveredDirectoryDescription() {
        return recoveredDirectoryDescription;
    }

    public void setRecoveredDirectoryDescription(String recoveredDirectoryDescription) {
        this.recoveredDirectoryDescription = recoveredDirectoryDescription;
    }
}