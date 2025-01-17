package com.unulearner.backend.storage.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConfigurationProperties(prefix = "backend.storage")
public class StorageProperties {
    /* TODO: a lot of checking here! */
    /**
     * Print stack trace on exception (applies to storage, mainly the controller)
     */
    private Boolean debugPrintStackTrace = false;

    /**
     * Set whether to print stack trace on exception or not (applies to storage, mainly the controller)
     */
    public Boolean getDebugPrintStackTrace() {
        return this.debugPrintStackTrace;
    }

    /**
     * Get whether to print stack trace on exception or not (applies to storage, mainly the controller)
     */
    public void setDebugPrintStackTrace(Boolean debugPrintStackTrace) throws Exception {
        if (debugPrintStackTrace == null) {
            throw new RuntimeException("Boolean 'debugPrintStackTrace' variable cannot be null!");
        }

        this.debugPrintStackTrace = debugPrintStackTrace;
    }

    /**
     * UUID of the root user to whom all unaccounted for storage nodes will belong to
     */
    private UUID rootUserUUID = null;

    /**
     * Get UUID of the root user to whom all unaccounted for storage nodes will belong to
     */
    public UUID getRootUserUUID() {
        return this.rootUserUUID;
    }

    /**
     * Set UUID of the root user to whom all unaccounted for storage nodes will belong to
     */
    public void setRootUserUUID(UUID rootUserUUID) {
        this.rootUserUUID = rootUserUUID;
    }

    /**
     *
     */
    private UUID defaultUserUUID = null;

    public UUID getDefaultUserUUID() {
        return this.defaultUserUUID;
    }

    public void setDefaultUserUUID(UUID defaultUserUUID) {
        this.defaultUserUUID = defaultUserUUID;
    }

    /**
     *
     */
    private UUID defaultGroupUUID = null;

    public UUID getDefaultGroupUUID() {
        return this.defaultGroupUUID;
    }

    public void setDefaultGroupUUID(UUID defaultGroupUUID) {
        this.defaultGroupUUID = defaultGroupUUID;
    }

    /**
     *
     */
    private String defaultPermissionFlagsUmask;

    public String getDefaultPermissionFlagsUmask() {
        return this.defaultPermissionFlagsUmask;
    }

    public void setDefaultPermissionFlagsUmask(String defaultPermissionFlagsUmask) {
        this.defaultPermissionFlagsUmask = defaultPermissionFlagsUmask;
    }

    /**
     *
     */
    private String  defaultNewFilePermissionFlags;

    public String getDefaultNewFilePermissionFlags() {
        return this.defaultNewFilePermissionFlags;
    }

    public void setDefaultNewFilePermissionFlags(String defaultNewFilePermissionFlags) {
        this.defaultNewFilePermissionFlags = defaultNewFilePermissionFlags;
    }

    /**
     *
     */
    private String  defaultNewDirectoryPermissionFlags;

    public String getDefaultNewDirectoryPermissionFlags() {
        return this.defaultNewDirectoryPermissionFlags;
    }

    public void setDefaultNewDirectoryPermissionFlags(String defaultNewDirectoryPermissionFlags) {
        this.defaultNewDirectoryPermissionFlags = defaultNewDirectoryPermissionFlags;
    }

    /**
     * Determines how many attempts each step of the task can make until it is skipped automatically
     */
    private Integer taskMaxRetries = 3;

    /**
     * Get how many attempts each step of the task can make until it is skipped automatically
     */
    public Integer getTaskMaxRetries() {
        return this.taskMaxRetries;
    }

    /**
     * Set how many attempts each step of the task can make until it is skipped automatically
     */
    public void setTaskMaxRetries(Integer taskMaxRetries) throws Exception {
        if (taskMaxRetries <= 0) {
            throw new RuntimeException("Allowed amount of attempts must be more than 0!");
        }

        this.taskMaxRetries = taskMaxRetries;
    }

    /**
     * Determines how many seconds of inactivity are allowed before the task is cancelled and removed from the scheduler
     */
    private Integer taskTimeOutInSeconds = 120;
    
    /**
     * Get how many seconds of inactivity are allowed before the task is cancelled and removed from the scheduler
     */
    public Integer getTaskTimeOut() {
        return this.taskTimeOutInSeconds;
    }

    /**
     * Set the allowed time for inactivity (in seconds) before the task is cancelled and removed from the scheduler
     */
    public void setTaskTimeOut(Integer taskTimeOut) throws Exception {
        if (taskTimeOut <= 0) {
            throw new RuntimeException("Allowed time for task inactivity must be more than 0 seconds!");
        }

        this.taskTimeOutInSeconds = taskTimeOut;
    }

    /**
     * Path/Name of the root directory (permanent storage)
     */
    private String rootDirectory = "storage";

    /**
     * Get the Path/Name of the root directory (permanent storage)
     */
    public String getRootDirectory() {
        return this.rootDirectory;
    }

    /**
     * Set the Path/Name of the root directory (permanent storage)
     */
    public void setRootDirectory(String rootDirectory) throws Exception {
        if (rootDirectory == null || (rootDirectory = rootDirectory.trim()).isBlank()) {
            throw new RuntimeException("Invalid root directory!");
        }

        this.rootDirectory = rootDirectory;
    }

    /**
     * Description of the root directory (permanent storage)
     */
    private String rootDirectoryDescription = "Root directory of the permanent storage...";

    /**
     * Get the description of the root directory (permanent storage)
     */
    public String getRootDirectoryDescription() {
        return this.rootDirectoryDescription;
    }

    /**
     * Get the description of the root directory (permanent storage)
     */
    public void setRootDirectoryDescription(String rootDirectoryDescription) {
        this.rootDirectoryDescription = rootDirectoryDescription;
    }

    /**
     * Description for the files that have been found on disk without a corresponding entry in the database to confirm them
     */
    private String recoveredFileDescription = "This StorageTreeNode was created automatically upon file recovery and is in need of immediate human attention!";

    /**
     * Get the description for the files that have been found on disk without a corresponding entry in the database to confirm them
     */
    public String getRecoveredFileDescription() {
        return this.recoveredFileDescription;
    }

    /**
     * Set the description for the files that have been found on disk without a corresponding entry in the database to confirm them
     */
    public void setRecoveredFileDescription(String recoveredFileDescription) {
        this.recoveredFileDescription = recoveredFileDescription;
    }

    /**
     * Description for the directories that have been found on disk without a corresponding entry in the database to confirm them
     */
    private String recoveredDirectoryDescription = "This StorageTreeNode was created automatically upon directory recovery and is in need of immediate human attention!";

    /**
     * Get the description for the directories that have been found on disk without a corresponding entry in the database to confirm them
     */
    public String getRecoveredDirectoryDescription() {
        return this.recoveredDirectoryDescription;
    }

    /**
     * Set the description for the directories that have been found on disk without a corresponding entry in the database to confirm them
     */
    public void setRecoveredDirectoryDescription(String recoveredDirectoryDescription) {
        this.recoveredDirectoryDescription = recoveredDirectoryDescription;
    }
}