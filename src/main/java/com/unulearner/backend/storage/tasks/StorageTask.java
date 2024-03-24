package com.unulearner.backend.storage.tasks;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;

import java.time.Instant;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;

import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;

public class StorageTask {
    private List<Option> onConflictOptions;
    private StorageTreeNode currentDestination;
    private StorageTreeNode taskRootDestination;
    private StorageTreeNode currentTarget;
    private StorageTreeNode taskRootNode;
    private StorageTree taskStorageTree;
    private Instant timeStampInitiated;
    private Instant timeStampFinished;
    private Integer taskRetryAttempt;
    private HttpStatus exitStatus;
    private String exitMessage;
    private Boolean taskIsDone;
    private List<String> log;
    
    public synchronized void run(String onConflictAction, Boolean onConflictActionIsPersistent, Boolean cancelTaskExecution) { }
    public StorageTask(@NonNull StorageTree storageTree, @NonNull StorageTreeNode targetNode) {
        this.onConflictOptions = new ArrayList<>();
        this.timeStampInitiated = Instant.now();
        this.taskStorageTree = storageTree;
        this.currentTarget = targetNode;
        this.currentDestination = null;
        this.taskRootNode = targetNode;
        this.log = new ArrayList<>();
        this.taskRetryAttempt = 0;
        this.taskIsDone = false;
        this.exitMessage = null;
        this.exitStatus = null;
        //TODO: affects n nodes;
        //TODO: log as single Strings?;
    }

    protected void setCurrentDestination(StorageTreeNode node) {
        this.currentDestination = node;
    }

    protected void setCurrentTarget(StorageTreeNode node) {
        this.currentTarget = node;
    }

    protected void setExitStatus(HttpStatus exitStatus) {
        this.exitStatus = exitStatus;
    }

    protected void setExitMessage(String exitMessage) {
        this.exitMessage = exitMessage;
    }

    public StorageTreeNode getCurrentDestination() {
        return this.currentDestination;
    }

    protected StorageTree storageTreeExecute() {
        return this.taskStorageTree;
    }

    public StorageTreeNode getCurrentTarget() {
        return this.currentTarget;
    }

    protected void logMessage(String message) {
        this.log.add(message);
    }

    protected void incrementAttemptCounter() {
        this.taskRetryAttempt++;
    }

    protected Integer getAttemptCounter() {
        return this.taskRetryAttempt;
    }

    protected void resetAttemptCounter() {
        this.taskRetryAttempt = 0;
    }

    protected void advanceTaskForward() {
        this.setTaskAsDone();
    }

    public HttpStatus getExitStatus() {
        return this.exitStatus;
    }

    public Instant getInitiatedOn() {
        return this.timeStampInitiated;
    }

    public Instant getFinishedOn() {
        return this.timeStampFinished;
    }

    public String getExitMessage() {
        return this.exitMessage;
    }

    public Boolean getTaskIsDone() {
        return this.taskIsDone;
    }

    protected void setTaskAsDone() {
        this.timeStampFinished = Instant.now();
        this.taskIsDone = true;
    }

    public List<String> getTaskLog() {
        return this.log;
    }

    /* Subclass to represent onConflict options and their flags */
    public static class Option {
        private final String value;
        private final String displayText;
        private final boolean applicableToFiles;
        private final boolean applicableToDirectories;

        public Option(String value, String displayText, boolean applicableToFiles, boolean applicableToDirectories) {
            this.value = value;
            this.displayText = displayText;
            this.applicableToFiles = applicableToFiles;
            this.applicableToDirectories = applicableToDirectories;
        }

        public String getValue() {
            return this.value;
        }

        public String getDisplayText() {
            return this.displayText;
        }

        public boolean isApplicableToFiles() {
            return this.applicableToFiles;
        }

        public boolean isApplicableToDirectories() {
            return this.applicableToDirectories;
        }
    }

    /* Methods to manipulate the aforementioned onConflict options */
    private Boolean onDirectoryConflictOptionIsPersistent;
    private String onDirectoryConflictOption;
    private Boolean onFileConflictOptionIsPersistent;
    private String onFileConflictOption;

    protected String getOnConflict() {
        if (this.currentTarget == null) {
            return null;
        }

        if (this.currentTarget.getChildren() != null) {
            return this.onDirectoryConflictOption;
        } else {
            return this.onFileConflictOption;
        }
    }

    protected void resetOnConflict() {
        if (this.currentTarget.getChildren() != null) {
            this.onDirectoryConflictOption = null;
        } else {
            this.onFileConflictOption = null;
        }
    }

    protected Boolean onConflictIsPersistent() {
        if (this.currentTarget.getChildren() != null) {
            if (this.onDirectoryConflictOptionIsPersistent != null) {
                return this.onDirectoryConflictOptionIsPersistent;
            }
        } else {
            if (this.onFileConflictOptionIsPersistent != null) {
                return this.onFileConflictOptionIsPersistent;
            }
        }

        return false;
    }

    protected void setOnConflict(@NonNull String optionValue, @NonNull Boolean persistOption) {
        for (Option option : this.onConflictOptions) {
            if (this.currentTarget.getChildren() != null) {
                if (option.getValue().equals(optionValue) && option.isApplicableToDirectories()) {
                    this.onDirectoryConflictOption = optionValue;

                    if (persistOption == true) {
                        this.onDirectoryConflictOptionIsPersistent = true;
                    }

                    return;
                }
            } else {
                if (option.getValue().equals(optionValue) && option.isApplicableToFiles()) {
                    this.onFileConflictOption = optionValue;

                    if (persistOption == true) {
                        this.onFileConflictOptionIsPersistent = true;
                    }

                    return;
                }
            }
        }

        this.exitMessage = "Action '%s' is not a valid on-conflict option!".formatted(optionValue);
        this.exitStatus = HttpStatus.BAD_REQUEST;
        return;
    }

    protected void setOnConflictOptions(List<Option> optionsList) {
        this.onConflictOptions = optionsList;
    }

    public List<Option> getOnConflictOptions() {
        if (this.currentTarget != null) {
            return this.onConflictOptions.stream()
            .filter(this.currentTarget.getChildren() != null ? Option::isApplicableToDirectories : Option::isApplicableToFiles)
            .collect(Collectors.toList());
        }

        return null;
    }
}
