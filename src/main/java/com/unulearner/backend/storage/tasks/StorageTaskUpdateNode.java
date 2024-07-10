package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.util.Optional;
import java.util.List;

import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.statics.StorageFileName;
import com.unulearner.backend.storage.entities.StorageTreeNode;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import com.unulearner.backend.storage.exceptions.FileNameValidationException;

public class StorageTaskUpdateNode extends StorageTaskBaseBatch {
    private final StorageTreeNode rootTargetStorageTreeNode;

    public StorageTaskUpdateNode(StorageTree storageTree, String targetName, StorageTreeNode targetStorageTreeNode, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        this.rootTargetStorageTreeNode = targetStorageTreeNode;

        final StorageTaskUpdateNodeCurrentAction storageTaskAction = new StorageTaskUpdateNodeCurrentAction(null, targetName, targetStorageTreeNode);

        storageTaskAction.setActionHeader("Rename '%s' %s".formatted(this.rootTargetStorageTreeNode.getOnDiskURL(), this.rootTargetStorageTreeNode.isDirectory() ? "directory" : "file"));
        storageTaskAction.setMessage("%s update task has been successfully initialized".formatted(this.rootTargetStorageTreeNode.isDirectory() ? "Directory" : "File"));
    
        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    @Override
    public synchronized void execute(String updatedName, String onExceptionAction, Boolean onExceptionActionIsPersistent) {
        final StorageTaskUpdateNodeCurrentAction storageTaskCurrentAction = (StorageTaskUpdateNodeCurrentAction) this.getStorageTaskAction();

        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getTargetStorageTreeNode().getId() != null) {
            storageTaskCurrentAction.setMessage("%s '%s' update task finished successfully!".formatted(this.rootTargetStorageTreeNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageTreeNode.getOnDiskURL()));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (onExceptionAction != null && onExceptionAction.equals("cancel")) {
            storageTaskCurrentAction.setMessage("%s '%s' update task was cancelled...".formatted(this.rootTargetStorageTreeNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageTreeNode.getOnDiskURL()));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            onExceptionActionIsPersistent = onExceptionActionIsPersistent != null ? onExceptionActionIsPersistent : false;
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetStorageTreeNode(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        Integer IOAttempts = 0;
        storageTaskCurrentAction.incrementAttemptCounter();
        while (storageTaskCurrentAction.getNewStorageTreeNode().getId() == null) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(exceptionType, storageTaskCurrentAction.getTargetStorageTreeNode());

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "FileAlreadyExistsException": /* TODO: add option to publish the conflicting node if it isn't published */
                            if (storageTaskCurrentAction.getConflictStorageTreeNode() == null) {
                                throw new RuntimeException("%s '%s' could not be updated due to a supposed conflicting node, existence of which can neither be confirmed nor denied...".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL()));
                            }

                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing transfer attempt */
                                    storageTaskCurrentAction.getNewStorageTreeNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageTreeNode(null);
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' update was skipped...".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be updated due to a conflicting node already in place.".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameValidationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskCurrentAction.getNewStorageTreeNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageTreeNode(null);
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Update of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL(), storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be updated due to a provided file name being incompatible.".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Update of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL(), storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onPhysicalOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};
        
                                    storageTaskCurrentAction.setMessage("%s '%s' could not be updated due to a persistent I/O exception occurring".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onPhysicalOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Update of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL(), storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onGeneralOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};
        
                                    storageTaskCurrentAction.setMessage("%s '%s' could not be updated due to an unexpected exception occurring".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onGeneralOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }   
                    }
                }

                if (storageTaskCurrentAction.getNewStorageTreeNode().getOnDiskName() != null && !storageTaskCurrentAction.getTargetStorageTreeNode().getNodePath().getFileName().toString().equals(storageTaskCurrentAction.getNewStorageTreeNode().getOnDiskName())) {
                    storageTaskCurrentAction.setNewStorageTreeNode(this.storageTreeExecute().renameStorageTreeNode(storageTaskCurrentAction.getNewStorageTreeNode(), storageTaskCurrentAction.getTargetStorageTreeNode()));
                }

                if (storageTaskCurrentAction.getNewStorageTreeNode().getNodePath() == null) {
                    storageTaskCurrentAction.getNewStorageTreeNode().setNodePath(storageTaskCurrentAction.getNewStorageTreeNode().getParent().getNodePath().resolve(storageTaskCurrentAction.getNewStorageTreeNode().getOnDiskName()));
                }

                if (storageTaskCurrentAction.getNewStorageTreeNode().getId() == null) { /* basically the move operation */
                    storageTaskCurrentAction.getTargetStorageTreeNode().setNodePath(storageTaskCurrentAction.getNewStorageTreeNode().getNodePath());
                    storageTaskCurrentAction.setNewStorageTreeNode(this.storageTreeExecute().publishStorageTreeNode(storageTaskCurrentAction.getTargetStorageTreeNode()));
                }

                storageTaskCurrentAction.setMessage("%s '%s' has been updated successfully!".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL()));
                this.setCurrentState(TaskState.EXECUTING);
                this.advanceStorageTask();
                return;
            } catch (FileAlreadyExistsException exception) {
                try { /* Here we attempt to find the conflicting node. And if we can't find it, we try to recover it */
                    final Optional<StorageTreeNode> possibleConflict =  storageTaskCurrentAction.getTargetStorageTreeNode().getParent().getChildren().stream().filter(entry -> entry.getNodePath().getFileName().toString().equals(storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskName())).findFirst();
                    if (possibleConflict.isPresent()) {
                        storageTaskCurrentAction.setConflictStorageTreeNode(possibleConflict.get());
                    } else {
                        storageTaskCurrentAction.setConflictStorageTreeNode(this.storageTreeExecute().recoverStorageTreeNode(storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskName(), storageTaskCurrentAction.getTargetStorageTreeNode().getParent()));
                    }
                } catch (Exception recoveryException) {
                    storageTaskCurrentAction.setConflictStorageTreeNode(null);

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
        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getTargetStorageTreeNode().getId() != null) {
            this.setCurrentState(TaskState.COMPLETED);
        }

        this.setStorageTaskAction(storageTaskCurrentAction);
        this.getTaskExceptionHandler().resetOnExceptionAction();
    }
 
    protected class StorageTaskUpdateNodeCurrentAction extends StorageTaskCurrentAction {
        private StorageTreeNode newStorageTreeNode;
        private StorageTreeNode targetStorageTreeNode;
        private StorageTreeNode conflictStorageTreeNode;

        protected StorageTaskUpdateNodeCurrentAction(StorageTaskUpdateNodeCurrentAction parentStorageTaskAction, String targetName, StorageTreeNode targetStorageTreeNode) {
            super(parentStorageTaskAction);
            
            this.targetStorageTreeNode = targetStorageTreeNode;

            this.newStorageTreeNode = new StorageTreeNode(targetStorageTreeNode.getParent(), targetStorageTreeNode.getChildren(), null, targetStorageTreeNode.getDescription());
            this.newStorageTreeNode.setOnDiskName(targetName);
            //TODO: setBusyWith() should happen here, after checking up the ladder!

            if (this.targetStorageTreeNode.isDirectory()) {
                for (StorageTreeNode childNode : targetStorageTreeNode.getChildren()) {
                    this.getChildStorageTaskActions().add(new StorageTaskUpdateNodeCurrentAction(this, null, childNode));
                }
            }
        }

        public StorageTreeNode getNewStorageTreeNode() {
            return this.newStorageTreeNode;
        }

        protected void setNewStorageTreeNode(StorageTreeNode newStorageTreeNode) {
            this.newStorageTreeNode = newStorageTreeNode;
        }

        public StorageTreeNode getTargetStorageTreeNode() {
            return this.targetStorageTreeNode;
        }

        protected void setTargetStorageTreeNode(StorageTreeNode targetStorageTreeNode) {
            this.targetStorageTreeNode = targetStorageTreeNode;
        }

        public StorageTreeNode getConflictStorageTreeNode() {
            return this.conflictStorageTreeNode;
        }

        protected void setConflictStorageTreeNode(StorageTreeNode conflictStorageTreeNode) {
            this.conflictStorageTreeNode = conflictStorageTreeNode;
        }
    }
}
