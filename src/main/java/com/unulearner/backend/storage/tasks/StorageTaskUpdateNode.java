package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.util.Map;

import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.statics.StorageFileName;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption.Parameter;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import com.unulearner.backend.storage.exceptions.FileNameValidationException;

public class StorageTaskUpdateNode extends StorageTaskBaseBatch {
    private final StorageNode rootTargetStorageNode;

    public StorageTaskUpdateNode(StorageTree storageTree, String targetName, StorageNode targetStorageNode, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        this.rootTargetStorageNode = targetStorageNode;

        final StorageTaskUpdateNodeCurrentAction storageTaskAction = new StorageTaskUpdateNodeCurrentAction(null, targetName, targetStorageNode);

        storageTaskAction.setActionHeader("Rename '%s' %s".formatted(this.rootTargetStorageNode.getOnDiskURL(), this.rootTargetStorageNode.isDirectory() ? "directory" : "file"));
        storageTaskAction.setMessage("%s update task has been successfully initialized".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File"));
    
        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    @Override
    public synchronized void execute(Map<String, Object> taskParameters) {
        final StorageTaskUpdateNodeCurrentAction storageTaskCurrentAction = (StorageTaskUpdateNodeCurrentAction) this.getStorageTaskAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String updatedName = taskParameters != null ? (String) taskParameters.get("updatedName") : null; 
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getTargetStorageNode().getId() != null) {
            storageTaskCurrentAction.setMessage("%s '%s' update task finished successfully!".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getOnDiskURL()));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (cancel != null && cancel == true) {
            storageTaskCurrentAction.setMessage("%s '%s' update task was cancelled...".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getOnDiskURL()));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        storageTaskCurrentAction.incrementAttemptCounter();
        while (storageTaskCurrentAction.getNewStorageNode().getId() == null) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(exceptionType, storageTaskCurrentAction.getTargetStorageNode());

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "FileAlreadyExistsException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing transfer attempt */
                                    storageTaskCurrentAction.getNewStorageNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' update was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be updated due to a conflicting node already in place.".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
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
                                    storageTaskCurrentAction.setMessage("Update of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL(), storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be updated due to the provided %s name being invalid.".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL(), storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Update of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL(), storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be updated due to a persistent I/O exception occurring".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Update of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL(), storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be updated due to an unexpected exception occurring".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
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

                storageTaskCurrentAction.setMessage("%s '%s' has been updated successfully!".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL()));
                this.setCurrentState(TaskState.EXECUTING);
                this.advanceStorageTask();
                return;
            } catch (FileAlreadyExistsException exception) {
                try { /* Here we attempt to find the conflicting node and recover it */
                    storageTaskCurrentAction.setConflictStorageNode(this.storageTreeExecute().recoverStorageNode(storageTaskCurrentAction.getTargetStorageNode().getOnDiskName(), storageTaskCurrentAction.getTargetStorageNode().getParent()));
                } catch (Exception recoveryException) {
                    /* Wild territories... conflict without a conflicting node? Gonna be fun!!! */
                    storageTaskCurrentAction.setConflictStorageNode(null);
                    /* TODO: Simply log this stuff for later and move on... */
                }

                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            } catch (FileNameValidationException exception) {
                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            } catch (IOException exception) {
                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            } catch (Exception exception) {
                storageTaskCurrentAction.setExceptionType("RuntimeException");
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
