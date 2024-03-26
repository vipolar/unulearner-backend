package com.unulearner.backend.storage.tasks;

import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.responses.StorageServiceResponse;
import com.unulearner.backend.storage.statics.StateCode;

public class StorageTask {
    private final List<StorageServiceResponse> taskLog;
    private final StorageTasksMap taskStorageTasksMap;
    private StorageServiceResponse taskCurrentState;
    private final StorageTree taskStorageTree;
    private List<Option> onExceptionOptions;

    private final StorageTreeNode rootDestinationNode;
    private StorageTreeNode currentDestinationNode;
    private final StorageTreeNode rootTargetNode;
    private StorageTreeNode currentTargetNode;
    private Integer taskAttemptCount;
    private final UUID taskUUID;
    private String taskHeading;
    private Integer taskTimer;
    private StateCode state;

    public StorageTask(StorageTree storageTree, StorageTreeNode targetNode, StorageTreeNode destinationNode, StorageTasksMap storageTasksMap) {
        this.taskStorageTasksMap = storageTasksMap;
        this.taskStorageTree = storageTree;
        this.taskLog = new ArrayList<>();
        this.onExceptionOptions = null;

        this.rootDestinationNode = destinationNode;
        this.currentDestinationNode = null;
        this.rootTargetNode = targetNode;
        this.currentTargetNode = null;
        this.taskAttemptCount = 0;
        this.taskHeading = null;

        /* Having this in the super() constructor is best because... */
        this.taskUUID = this.taskStorageTasksMap.addStorageTask(this);
    }

    public synchronized void executeTask(String onExceptionAction, Boolean onExceptionActionIsPersistent, Boolean cancelTaskExecution) {
        return; /* OVERRIDE THE HELL OUT OF THIS EVERYWHERE!!! */
    }

    protected void setTaskCurrentState(String taskExitMessage, StateCode taskCurrentState) {
        this.taskCurrentState = new StorageServiceResponse(this.taskUUID, this.taskHeading, taskExitMessage, this.taskTimer, taskCurrentState);

        if (this.state == StateCode.RUNNING || this.state == StateCode.EXCEPTION) {
            this.taskTimer = this.taskStorageTasksMap.scheduleStorageTaskRemoval(this.taskUUID);
        } else {
            this.taskTimer = this.taskStorageTasksMap.removeStorageTask(this.taskUUID);
        }

        this.taskLog.add(this.taskCurrentState);
        this.state = taskCurrentState;
    }

    public StorageServiceResponse getCurrentState() {
        return this.taskCurrentState;
    }

    protected StorageTree storageTreeExecute() {
        return this.taskStorageTree;
    }

    public void setTaskHeading(String title) {
        this.taskHeading = title;
    }

    protected void setCurrentDestination(StorageTreeNode node) {
        this.currentDestinationNode = node;
    }

    protected void setCurrentTarget(StorageTreeNode node) {
        this.currentTargetNode = node;
    }

    protected StorageTreeNode getCurrentDestination() {
        return this.currentDestinationNode;
    }

    protected StorageTreeNode getRootDestination() {
        return this.rootDestinationNode;
    }

    protected StorageTreeNode getCurrentTarget() {
        return this.currentTargetNode;
    }

    protected StorageTreeNode getRootTarget() {
        return this.rootTargetNode;
    }

    protected void incrementAttemptCounter() {
        this.taskAttemptCount++;
    }

    protected Integer getAttemptCounter() {
        return this.taskAttemptCount;
    }

    protected void resetAttemptCounter() {
        this.taskAttemptCount = 0;
    }

    protected String getTaskHeading() {
        return this.taskHeading;
    }

    protected Integer getTimeLeft() {
        return this.taskTimer;
    }

    protected StateCode getState() {
        return this.state;
    }

    protected UUID getTaskUUID() {
        return this.taskUUID;
    }

    protected void advanceTask() {
        return;
    }

    /* Subclass to represent onException action options and their flags */
    /* Class is public by design as it needs to be referenced by others */
    public static class Option {
        private final String value;
        private final String displayText;
        private final boolean applicableToFiles;
        private final boolean applicableToDirectories;

        protected Option(String value, String displayText, boolean applicableToFiles, boolean applicableToDirectories) {
            this.value = value;
            this.displayText = displayText;
            this.applicableToFiles = applicableToFiles;
            this.applicableToDirectories = applicableToDirectories;
        }

        protected String getValue() {
            return this.value;
        }

        protected String getDisplayText() {
            return this.displayText;
        }

        protected boolean isApplicableToFiles() {
            return this.applicableToFiles;
        }

        protected boolean isApplicableToDirectories() {
            return this.applicableToDirectories;
        }
    }

    protected void setOnExceptionOptions(List<Option> optionsList) {
        this.onExceptionOptions = optionsList;
    }

    protected List<Option> getOnExceptionOptions(StorageTreeNode exceptionNode) {
        if (exceptionNode == null) {
            return null;
        }
        
        return this.onExceptionOptions.stream()
            .filter(exceptionNode.getChildren() != null ? Option::isApplicableToDirectories : Option::isApplicableToFiles)
            .collect(Collectors.toList());
    }

    /* Methods to manipulate the aforementioned onException options */
    private Boolean onDirectoryExceptionActionIsPersistent;
    private Boolean onFileExceptionActionIsPersistent;
    private String onDirectoryExceptionAction;
    private String onFileExceptionAction;

    protected String getOnExceptionAction(StorageTreeNode exceptionNode) {
        if (exceptionNode == null) {
            return "default";
        }

        if (exceptionNode.getChildren() != null) {
            return this.onDirectoryExceptionAction;
        } else {
            return this.onFileExceptionAction;
        }
    }

    protected void resetOnExceptionAction(StorageTreeNode exceptionNode) {
        if (exceptionNode == null) {
            return;
        }

        if (exceptionNode.getChildren() != null) {
            if (this.onDirectoryExceptionActionIsPersistent != null) {
                this.onDirectoryExceptionAction = null;
            }
        } else {
            if (this.onFileExceptionActionIsPersistent != null) {
                this.onFileExceptionAction = null;
            }
        }
    }

    protected Boolean setOnExceptionAction(StorageTreeNode exceptionNode, String optionValue, Boolean persistOption) {
        if (exceptionNode == null || optionValue == null || persistOption == null) {
            return false;
        }

        for (Option option : this.onExceptionOptions) {
            if (exceptionNode.getChildren() != null) {
                if (option.getValue().equals(optionValue) && option.isApplicableToDirectories()) {
                    this.onDirectoryExceptionAction = optionValue;

                    if (persistOption == true) {
                        this.onDirectoryExceptionActionIsPersistent = true;
                    }

                    return true;
                }
            } else {
                if (option.getValue().equals(optionValue) && option.isApplicableToFiles()) {
                    this.onFileExceptionAction = optionValue;

                    if (persistOption == true) {
                        this.onFileExceptionActionIsPersistent = true;
                    }

                    return true;
                }
            }
        }

        return false;
    }
}
