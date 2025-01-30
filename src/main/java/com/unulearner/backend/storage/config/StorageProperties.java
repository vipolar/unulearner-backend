package com.unulearner.backend.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConfigurationProperties(prefix = "backend.storage")
public class StorageProperties {
    private static final UUID defaultValidUUID = UUID.fromString("00000000-0000-4000-8000-000000000000");

    //**********************************************************//
    //*                                                        *//
    //*                         Controller                     *//
    //*                                                        *//
    //**********************************************************//

    /**
     * Controller: print stack trace on exception (all exceptions bubble up to here)
     */
    private Boolean controllerPrintExceptionStackTrace = false;

    public Boolean getControllerPrintExceptionStackTrace() {
        return this.controllerPrintExceptionStackTrace;
    }

    public void setControllerPrintExceptionStackTrace(Boolean controllerPrintExceptionStackTrace) {
        this.controllerPrintExceptionStackTrace = controllerPrintExceptionStackTrace;
    }

    //**********************************************************//
    //*                                                        *//
    //*                         Task flow                      *//
    //*                                                        *//
    //**********************************************************//

    /**
     * Amount of attempts each action of the task can make until it is automatically skipped
     */
    private Integer taskMaxRetries = 3;

    public Integer getTaskMaxRetries() {
        return this.taskMaxRetries;
    }

    public void setTaskMaxRetries(Integer taskMaxRetries) {
        if (taskMaxRetries <= 0) {
            throw new RuntimeException("StorageProperties: invalid amount of task action retry attempts allowed!");
        }

        this.taskMaxRetries = taskMaxRetries;
    }

    /**
     * Time (in seconds) of inactivity allowed before the task is cancelled and removed from the scheduler
     */
    private Integer taskTimeOutInSeconds = 120;
    
    public Integer getTaskTimeOutInSeconds() {
        return this.taskTimeOutInSeconds;
    }

    public void setTaskTimeOutInSeconds(Integer taskTimeOutInSeconds) {
        if (taskTimeOutInSeconds < 0) {
            throw new RuntimeException("StorageProperties: invalid time (in seconds) for task inactivity allowed!");
        }

        this.taskTimeOutInSeconds = taskTimeOutInSeconds;
    }

    /**
     * Grace period (in seconds) for which the scheduler will keep the task alive for the follow-up (invisible to the frontend)
     */
    private Integer taskTimeOutGracePeriodInSeconds = 30;
    
    public Integer getTaskTimeOutGracePeriodInSeconds() {
        return this.taskTimeOutGracePeriodInSeconds;
    }

    public void setTaskTimeOutGracePeriodInSeconds(Integer taskTimeOutGracePeriodInSeconds) {
        if (taskTimeOutGracePeriodInSeconds < 0) {
            throw new RuntimeException("StorageProperties: invalid grace period (in seconds) for task inactivity allowed!");
        }

        this.taskTimeOutGracePeriodInSeconds = taskTimeOutGracePeriodInSeconds;
    }

    //**********************************************************//
    //*                                                        *//
    //*                       Root directory                   *//
    //*                                                        *//
    //**********************************************************//

    /**
     * URL of the root directory of the permanent storage
     */
    private String rootDirectoryUrl = "storage";

    public String getRootDirectoryUrl() {
        return this.rootDirectoryUrl;
    }

    public void setRootDirectoryUrl(String rootDirectoryUrl) {
        if (rootDirectoryUrl == null || (rootDirectoryUrl = rootDirectoryUrl.trim()).isBlank()) {
            throw new RuntimeException("StorageProperties: root directory url cannot be NULL!");
        }

        this.rootDirectoryUrl = rootDirectoryUrl;
    }

    /**
     * Description of the root directory of the permanent storage
     */
    private String rootDirectoryDescription = "Root directory of the permanent storage...";

    public String getRootDirectoryDescription() {
        return this.rootDirectoryDescription;
    }

    public void setRootDirectoryDescription(String rootDirectoryDescription) {
        this.rootDirectoryDescription = rootDirectoryDescription;
    }


    //**********************************************************//
    //*                                                        *//
    //*                  Root user/group/umask                 *//
    //*                                                        *//
    //**********************************************************//

    /**
     * UUID of the root user to whom the root directory of the permanent storage will belong to
     */
    private UUID rootUserUUID = StorageProperties.defaultValidUUID;

    public UUID getRootUserUUID() {
        return this.rootUserUUID;
    }

    public void setRootUserUUID(UUID rootUserUUID) {
        this.rootUserUUID = rootUserUUID;
    }

    /**
     * UUID of the root group to which the root directory of the permanent storage will belong to
     */
    private UUID rootGroupUUID = StorageProperties.defaultValidUUID;

    public UUID getRootGroupUUID() {
        return this.rootGroupUUID;
    }

    public void setRootGroupUUID(UUID rootGroupUUID) {
        this.rootGroupUUID = rootGroupUUID;
    }

    /**
     * uMask that will be applied to the permission flags of the files/directories created by the root
     */
    private String rootPermissionFlagsUmask;

    public String getRootPermissionFlagsUmask() {
        return this.rootPermissionFlagsUmask;
    }

    public void setRootPermissionFlagsUmask(String rootPermissionFlagsUmask) {
        if (rootPermissionFlagsUmask == null || (rootPermissionFlagsUmask = rootPermissionFlagsUmask.trim()).isBlank()) {
            throw new RuntimeException("StorageProperties: root uMask cannot be NULL!");
        }

        if (rootPermissionFlagsUmask.matches("^[0-7]{3,4}$") != true) {
            throw new RuntimeException("StorageProperties: invalid root uMask!");
        }

        this.rootPermissionFlagsUmask = rootPermissionFlagsUmask;
    }

    //**********************************************************//
    //*                                                        *//
    //*                 Default user/group/umask               *//
    //*                                                        *//
    //**********************************************************//

    /**
     * UUID of the default user to whom all unaccounted for storage entries will belong to
     */
    private UUID defaultUserUUID = StorageProperties.defaultValidUUID;

    public UUID getDefaultUserUUID() {
        return this.defaultUserUUID;
    }

    public void setDefaultUserUUID(UUID defaultUserUUID) {
        this.defaultUserUUID = defaultUserUUID;
    }

    /**
     * UUID of the default group to which all unaccounted for storage entries will belong to
     */
    private UUID defaultGroupUUID = StorageProperties.defaultValidUUID;

    public UUID getDefaultGroupUUID() {
        return this.defaultGroupUUID;
    }

    public void setDefaultGroupUUID(UUID defaultGroupUUID) {
        this.defaultGroupUUID = defaultGroupUUID;
    }

    /**
     * uMask that will be applied to the permission flags of the files/directories created by default
     */
    private String defaultPermissionFlagsUmask;

    public String getDefaultPermissionFlagsUmask() {
        return this.defaultPermissionFlagsUmask;
    }

    public void setDefaultPermissionFlagsUmask(String defaultPermissionFlagsUmask) {
        if (defaultPermissionFlagsUmask == null || (defaultPermissionFlagsUmask = defaultPermissionFlagsUmask.trim()).isBlank()) {
            throw new RuntimeException("StorageProperties: default uMask cannot be NULL!");
        }

        if (defaultPermissionFlagsUmask.matches("^[0-7]{3,4}$") != true) {
            throw new RuntimeException("StorageProperties: invalid default uMask!");
        }

        this.defaultPermissionFlagsUmask = defaultPermissionFlagsUmask;
    }

    //**********************************************************//
    //*                                                        *//
    //*                    Default permissions                 *//
    //*                                                        *//
    //**********************************************************//

    /**
     * Permission flags that a newly created file will default to (uMask to be applied)
     */
    private String  defaultNewFilePermissionFlags;

    public String getDefaultNewFilePermissionFlags() {
        return this.defaultNewFilePermissionFlags;
    }

    public void setDefaultNewFilePermissionFlags(String defaultNewFilePermissionFlags) {
        if (defaultNewFilePermissionFlags == null || (defaultNewFilePermissionFlags = defaultNewFilePermissionFlags.trim()).isBlank()) {
            throw new RuntimeException("StorageProperties: default new file permission flags cannot be NULL!");
        }

        if (defaultNewFilePermissionFlags.matches("^[0-7]{3,4}$") != true) {
            throw new RuntimeException("StorageProperties: invalid default new file permission flags!");
        }

        this.defaultNewFilePermissionFlags = defaultNewFilePermissionFlags;
    }

    /**
     * Permission flags that a newly created directory will default to (uMask to be applied)
     */
    private String  defaultNewDirectoryPermissionFlags;

    public String getDefaultNewDirectoryPermissionFlags() {
        return this.defaultNewDirectoryPermissionFlags;
    }

    public void setDefaultNewDirectoryPermissionFlags(String defaultNewDirectoryPermissionFlags) {
        if (defaultNewDirectoryPermissionFlags == null || (defaultNewDirectoryPermissionFlags = defaultNewDirectoryPermissionFlags.trim()).isBlank()) {
            throw new RuntimeException("StorageProperties: default new directory permission flags cannot be NULL!");
        }

        if (defaultNewDirectoryPermissionFlags.matches("^[0-7]{3,4}$") != true) {
            throw new RuntimeException("StorageProperties: invalid default new directory permission flags!");
        }

        this.defaultNewDirectoryPermissionFlags = defaultNewDirectoryPermissionFlags;
    }

    //**********************************************************//
    //*                                                        *//
    //*                         Recovery                       *//
    //*                                                        *//
    //**********************************************************//

    /**
     * Description for the files that have been found on disk without a corresponding entry in the database to confirm them
     */
    private String recoveredFileDescription = "This storage entry was created automatically upon file recovery and is in need of immediate human attention!";

    public String getRecoveredFileDescription() {
        return this.recoveredFileDescription;
    }

    public void setRecoveredFileDescription(String recoveredFileDescription) {
        this.recoveredFileDescription = recoveredFileDescription;
    }

    /**
     * Description for the directories that have been found on disk without a corresponding entry in the database to confirm them
     */
    private String recoveredDirectoryDescription = "This storage entry was created automatically upon directory recovery and is in need of immediate human attention!";

    public String getRecoveredDirectoryDescription() {
        return this.recoveredDirectoryDescription;
    }

    public void setRecoveredDirectoryDescription(String recoveredDirectoryDescription) {
        this.recoveredDirectoryDescription = recoveredDirectoryDescription;
    }
}