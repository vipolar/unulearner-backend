package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.util.Map;

import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.responses.StorageServiceResponse;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption.Parameter;

import java.io.IOException;

public class StorageTaskChmodNode extends StorageTaskBaseBatch {
    private final StorageNode rootTargetStorageNode;

    public StorageTaskChmodNode(StorageTree storageTree, StorageNode targetStorageNode, Short flags, Boolean taskIsRecursive, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        final Boolean updateTaskIsRecursive = taskIsRecursive != null ? taskIsRecursive : false;
        this.rootTargetStorageNode = targetStorageNode;

        final StorageTaskChmodNodeCurrentAction storageTaskAction = new StorageTaskChmodNodeCurrentAction(null, targetStorageNode, flags, updateTaskIsRecursive);

        storageTaskAction.setActionHeader("Update %s '%s' permission flags".formatted(this.rootTargetStorageNode.isDirectory() ? "directory" : "file", this.rootTargetStorageNode.getUrl()));
        storageTaskAction.setMessage("%s permission flags update task has been successfully initialized".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File"));
    
        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    @Override
    public synchronized StorageServiceResponse execute(Map<String, Object> taskParameters) {
        this.advanceStorageTask(); /* Will advance task only if the current task is either done or waiting for the children tasks to be done */
        final StorageTaskChmodNodeCurrentAction storageTaskCurrentAction = (StorageTaskChmodNodeCurrentAction) this.getStorageTaskAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getTargetStorageNode().getId() != null) {
            storageTaskCurrentAction.setMessage("%s '%s' permission flags update task finished successfully!".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getUrl()));
            this.setCurrentState(TaskState.COMPLETED);
            
            return this.getStorageServiceResponse();
        }

        if (cancel != null && cancel == true) {
            storageTaskCurrentAction.setMessage("%s '%s' permission flags update task was cancelled...".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getUrl()));
            this.setCurrentState(TaskState.CANCELLED);
            
            return this.getStorageServiceResponse();
        }

        if (onExceptionAction != null) {
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        storageTaskCurrentAction.incrementAttemptCounter();
        while (storageTaskCurrentAction.getUpdateSuccessful() != true) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(exceptionType, storageTaskCurrentAction.getTargetStorageNode());

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Permission flags update of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' permission flags could not be updated due to a persistent I/O exception".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Permission flags update of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' permission flags could not be updated due to an unexpected exception".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }   
                    }
                }

                this.storageTreeExecute().chmodStorageNode(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getPermissionFlags());

                storageTaskCurrentAction.setMessage("%s '%s' permission flags have been updated successfully!".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                storageTaskCurrentAction.setUpdateSuccessful(true);
                storageTaskCurrentAction.setExceptionMessage(null);
                storageTaskCurrentAction.setExceptionType(null);
                this.setCurrentState(TaskState.EXECUTING);
                this.advanceStorageTask();
                
                return this.getStorageServiceResponse();
            } catch (IOException exception) {
                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            } catch (Exception exception) {
                storageTaskCurrentAction.setExceptionType("RuntimeException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            }
        }

        return this.getStorageServiceResponse();
    }

    @Override
    protected void skipStorageTaskCurrentAction() { /* TODO: THIS MAKES NO FUCKING SENSE! */
        StorageTaskChmodNodeCurrentAction storageTaskCurrentAction = (StorageTaskChmodNodeCurrentAction) this.getStorageTaskAction();

        if (storageTaskCurrentAction.getParentStorageTaskAction() != null) {
            this.setStorageTaskAction((StorageTaskChmodNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction());
        }
    }

    @Override
    protected void advanceStorageTask() {
        StorageTaskChmodNodeCurrentAction storageTaskCurrentAction = (StorageTaskChmodNodeCurrentAction) this.getStorageTaskAction();

        if (!storageTaskCurrentAction.getUpdateSuccessful()) {
            return;
        }

        while (!storageTaskCurrentAction.getChildStorageTaskActions().hasNext()) {
            if (storageTaskCurrentAction.getParentStorageTaskAction() == null) {
                break;
            }

            storageTaskCurrentAction = (StorageTaskChmodNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction();
        }

        if (storageTaskCurrentAction.getChildStorageTaskActions().hasNext()) {
            storageTaskCurrentAction = (StorageTaskChmodNodeCurrentAction) storageTaskCurrentAction.getChildStorageTaskActions().next();
        }

        this.setStorageTaskAction(storageTaskCurrentAction);
        this.getTaskExceptionHandler().resetOnExceptionAction();
    }

    protected class StorageTaskChmodNodeCurrentAction extends StorageTaskCurrentAction {
        private Short permissionFlags;
        private Boolean updateSuccessful;
        private StorageNode targetStorageNode;

        protected StorageTaskChmodNodeCurrentAction(StorageTaskChmodNodeCurrentAction parentStorageTaskAction, StorageNode editedStorageNode, Short flags, Boolean taskIsRecursive) {
            super(parentStorageTaskAction);

            this.permissionFlags = flags;
            this.updateSuccessful = false;
            this.targetStorageNode = editedStorageNode;

            if (taskIsRecursive == true && this.targetStorageNode.isDirectory()) {
                for (StorageNode childNode : targetStorageNode.getChildren()) {
                    this.getChildStorageTaskActions().add(new StorageTaskChmodNodeCurrentAction(this, childNode, flags, taskIsRecursive));
                    this.getChildStorageTaskActions().previous(); /* Required because iterator pushes forward on .add() which is an expected but unwanted behavior */
                }
            }
        }

        public Short getPermissionFlags() {
            return this.permissionFlags;
        }

        public void setPermissionFlags(Short permissionFlags) {
            this.permissionFlags = permissionFlags;
        }

        public Boolean getUpdateSuccessful() {
            return this.updateSuccessful;
        }

        public void setUpdateSuccessful(Boolean updateSuccessful) {
            this.updateSuccessful = updateSuccessful;
        }

        public StorageNode getTargetStorageNode() {
            return this.targetStorageNode;
        }

        protected void setTargetStorageNode(StorageNode targetStorageNode) {
            this.targetStorageNode = targetStorageNode;
        }
    }
}
