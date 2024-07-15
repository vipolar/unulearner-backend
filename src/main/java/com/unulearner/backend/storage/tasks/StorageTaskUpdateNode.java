package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.util.Optional;
import java.util.List;

import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.statics.StorageFileName;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import com.unulearner.backend.storage.exceptions.FileNameValidationException;

public class StorageTaskUpdateNode extends StorageTaskBaseBatch {
    private final StorageNode rootTargetStorageNode;

    public StorageTaskUpdateNode(StorageTree storageTree, String targetName, StorageNode targetStorageNode, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        this.rootTargetStorageNode = targetStorageNode;

        final StorageTaskUpdateNodeCurrentAction storageTaskAction = new StorageTaskUpdateNodeCurrentAction(null, targetName, targetStorageNode);

        storageTaskAction.setActionHeader("Rename '%s' %s".formatted(this.rootTargetStorageNode.getOnDiskFormattedURL(), this.rootTargetStorageNode.isDirectory() ? "directory" : "file"));
        storageTaskAction.setMessage("%s update task has been successfully initialized".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File"));
    
        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    @Override
    public synchronized void execute(String updatedName, String onExceptionAction, Boolean onExceptionActionIsPersistent) {
        final StorageTaskUpdateNodeCurrentAction storageTaskCurrentAction = (StorageTaskUpdateNodeCurrentAction) this.getStorageTaskAction();

        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getTargetStorageNode().getId() != null) {
            storageTaskCurrentAction.setMessage("%s '%s' update task finished successfully!".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getOnDiskFormattedURL()));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (onExceptionAction != null && onExceptionAction.equals("cancel")) {
            storageTaskCurrentAction.setMessage("%s '%s' update task was cancelled...".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getOnDiskFormattedURL()));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            onExceptionActionIsPersistent = onExceptionActionIsPersistent != null ? onExceptionActionIsPersistent : false;
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        Integer IOAttempts = 0;
        storageTaskCurrentAction.incrementAttemptCounter();
        while (storageTaskCurrentAction.getNewStorageNode().getId() == null) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(exceptionType, storageTaskCurrentAction.getTargetStorageNode());

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "FileAlreadyExistsException": /* TODO: add option to publish the conflicting node if it isn't published */
                            if (storageTaskCurrentAction.getConflictStorageNode() == null) {
                                throw new RuntimeException("%s '%s' could not be updated due to a supposed conflicting node, existence of which can neither be confirmed nor denied...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL()));
                            }

                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing transfer attempt */
                                    storageTaskCurrentAction.getNewStorageNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' update was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be updated due to a conflicting node already in place.".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameValidationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskCurrentAction.getNewStorageNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Update of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL(), storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be updated due to a provided file name being incompatible.".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Update of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL(), storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onPhysicalOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"), true));
                                    }};
        
                                    storageTaskCurrentAction.setMessage("%s '%s' could not be updated due to a persistent I/O exception occurring".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onPhysicalOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Update of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL(), storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onGeneralOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"), true));
                                    }};
        
                                    storageTaskCurrentAction.setMessage("%s '%s' could not be updated due to an unexpected exception occurring".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onGeneralOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }   
                    }
                }

                if (storageTaskCurrentAction.getNewStorageNode().getOnDiskName() != null && !storageTaskCurrentAction.getTargetStorageNode().getNodePath().getFileName().toString().equals(storageTaskCurrentAction.getNewStorageNode().getOnDiskName())) {
                    storageTaskCurrentAction.setNewStorageNode(this.storageTreeExecute().renameStorageNode(storageTaskCurrentAction.getNewStorageNode(), storageTaskCurrentAction.getTargetStorageNode()));
                }

                if (storageTaskCurrentAction.getNewStorageNode().getNodePath() == null) {
                    storageTaskCurrentAction.getNewStorageNode().setNodePath(storageTaskCurrentAction.getNewStorageNode().getParent().getNodePath().resolve(storageTaskCurrentAction.getNewStorageNode().getOnDiskName()));
                }

                if (storageTaskCurrentAction.getNewStorageNode().getId() == null) { /* basically the move operation */
                    storageTaskCurrentAction.getTargetStorageNode().setNodePath(storageTaskCurrentAction.getNewStorageNode().getNodePath());
                    storageTaskCurrentAction.setNewStorageNode(this.storageTreeExecute().publishStorageNode(storageTaskCurrentAction.getTargetStorageNode()));
                }

                storageTaskCurrentAction.setMessage("%s '%s' has been updated successfully!".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL()));
                this.setCurrentState(TaskState.EXECUTING);
                this.advanceStorageTask();
                return;
            } catch (FileAlreadyExistsException exception) {
                try { /* Here we attempt to find the conflicting node. And if we can't find it, we try to recover it */
                    final Optional<StorageNode> possibleConflict =  storageTaskCurrentAction.getTargetStorageNode().getParent().getChildren().stream().filter(entry -> entry.getNodePath().getFileName().toString().equals(storageTaskCurrentAction.getTargetStorageNode().getOnDiskName())).findFirst();
                    if (possibleConflict.isPresent()) {
                        storageTaskCurrentAction.setConflictStorageNode(possibleConflict.get());
                    } else {
                        storageTaskCurrentAction.setConflictStorageNode(this.storageTreeExecute().recoverStorageNode(storageTaskCurrentAction.getTargetStorageNode().getOnDiskName(), storageTaskCurrentAction.getTargetStorageNode().getParent()));
                    }
                } catch (Exception recoveryException) {
                    storageTaskCurrentAction.setConflictStorageNode(null);

                    /* wild territories... */
                    storageTaskCurrentAction.setExceptionType(recoveryException.getClass().getSimpleName());
                    storageTaskCurrentAction.setExceptionMessage(recoveryException.getMessage());
                    continue;
                }

                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            } catch (FileNameValidationException exception) {
                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            } catch (IOException exception) {
                if (IOAttempts++ < 3) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException sleepException) {
                        storageTaskCurrentAction.setExceptionType(sleepException.getClass().getSimpleName());
                        storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                        continue;
                    }
                }

                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            } catch (Exception exception) {
                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            }
        }
    }

    @Override
    protected void advanceStorageTask() {
        StorageTaskUpdateNodeCurrentAction storageTaskCurrentAction = (StorageTaskUpdateNodeCurrentAction) this.getStorageTaskAction();

        while (!storageTaskCurrentAction.getChildStorageTaskActions().hasNext()) {
            if (storageTaskCurrentAction.getParentStorageTaskAction() == null) {
                break;
            }

            storageTaskCurrentAction = (StorageTaskUpdateNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction();
        }

        if (storageTaskCurrentAction.getChildStorageTaskActions().hasNext()) {
            storageTaskCurrentAction = (StorageTaskUpdateNodeCurrentAction) storageTaskCurrentAction.getChildStorageTaskActions().next();
        }

        //TODO: look into completion status!
        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getTargetStorageNode().getId() != null) {
            this.setCurrentState(TaskState.COMPLETED);
        }

        this.setStorageTaskAction(storageTaskCurrentAction);
        this.getTaskExceptionHandler().resetOnExceptionAction();
    }
 
    protected class StorageTaskUpdateNodeCurrentAction extends StorageTaskCurrentAction {
        private StorageNode newStorageNode;
        private StorageNode targetStorageNode;
        private StorageNode conflictStorageNode;

        protected StorageTaskUpdateNodeCurrentAction(StorageTaskUpdateNodeCurrentAction parentStorageTaskAction, String targetName, StorageNode targetStorageNode) {
            super(parentStorageTaskAction);
            
            this.targetStorageNode = targetStorageNode;

            this.newStorageNode = new StorageNode(targetStorageNode.getParent(), targetStorageNode.getChildren(), null, targetStorageNode.getDescription());
            this.newStorageNode.setOnDiskName(targetName);
            //TODO: setBusyWith() should happen here, after checking up the ladder!

            if (this.targetStorageNode.isDirectory()) {
                for (StorageNode childNode : targetStorageNode.getChildren()) {
                    this.getChildStorageTaskActions().add(new StorageTaskUpdateNodeCurrentAction(this, null, childNode));
                }
            }
        }

        public StorageNode getNewStorageNode() {
            return this.newStorageNode;
        }

        protected void setNewStorageNode(StorageNode newStorageNode) {
            this.newStorageNode = newStorageNode;
        }

        public StorageNode getTargetStorageNode() {
            return this.targetStorageNode;
        }

        protected void setTargetStorageNode(StorageNode targetStorageNode) {
            this.targetStorageNode = targetStorageNode;
        }

        public StorageNode getConflictStorageNode() {
            return this.conflictStorageNode;
        }

        protected void setConflictStorageNode(StorageNode conflictStorageNode) {
            this.conflictStorageNode = conflictStorageNode;
        }
    }
}
