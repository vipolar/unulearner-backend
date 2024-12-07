package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;

import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.responses.StorageServiceResponse;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption.Parameter;

import java.io.IOException;

public class StorageTaskChownNode extends StorageTaskBaseBatch {
    private final StorageNode rootTargetStorageNode;

    public StorageTaskChownNode(StorageTree storageTree, StorageNode targetStorageNode, UUID user, UUID group, Boolean taskIsRecursive, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        final Boolean updateTaskIsRecursive = taskIsRecursive != null ? taskIsRecursive : false;
        this.rootTargetStorageNode = targetStorageNode;

        final StorageTaskChownNodeCurrentAction storageTaskAction = new StorageTaskChownNodeCurrentAction(null, targetStorageNode, user, group, updateTaskIsRecursive);

        storageTaskAction.setActionHeader("Change %s %s owners".formatted(this.rootTargetStorageNode.isDirectory() ? "directory" : "file", this.rootTargetStorageNode.getUrl()));
        storageTaskAction.setMessage("%s owner change task has been successfully initialized".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File"));
    
        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    @Override
    public synchronized StorageServiceResponse execute(Map<String, Object> taskParameters) {
        this.advanceStorageTask(); /* Will advance task only if the current task is either done or waiting for the children tasks to be done */
        final StorageTaskChownNodeCurrentAction storageTaskCurrentAction = (StorageTaskChownNodeCurrentAction) this.getStorageTaskAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getTargetStorageNode().getId() != null) {
            storageTaskCurrentAction.setMessage("%s '%s' ownership change task finished successfully!".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getUrl()));
            this.setCurrentState(TaskState.COMPLETED);
            
            return this.getStorageServiceResponse();
        }

        if (cancel != null && cancel == true) {
            storageTaskCurrentAction.setMessage("%s '%s' ownership change task was cancelled...".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getUrl()));
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
                                    storageTaskCurrentAction.setMessage("Ownership change of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' ownership could not be changed due to a persistent I/O exception".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Ownership change of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' ownership could not be changed due to an unexpected exception".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }   
                    }
                }

                this.storageTreeExecute().chownStorageNode(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getOwnerUserUUID(), storageTaskCurrentAction.getOwnerGroupUUID());

                storageTaskCurrentAction.setMessage("%s '%s' ownership has been changed successfully!".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
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
        StorageTaskChownNodeCurrentAction storageTaskCurrentAction = (StorageTaskChownNodeCurrentAction) this.getStorageTaskAction();

        if (storageTaskCurrentAction.getParentStorageTaskAction() != null) {
            this.setStorageTaskAction((StorageTaskChownNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction());
        }
    }

    @Override
    protected void advanceStorageTask() {
        StorageTaskChownNodeCurrentAction storageTaskCurrentAction = (StorageTaskChownNodeCurrentAction) this.getStorageTaskAction();

        if (!storageTaskCurrentAction.getUpdateSuccessful()) {
            return;
        }

        while (!storageTaskCurrentAction.getChildStorageTaskActions().hasNext()) {
            if (storageTaskCurrentAction.getParentStorageTaskAction() == null) {
                break;
            }

            storageTaskCurrentAction = (StorageTaskChownNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction();
        }

        if (storageTaskCurrentAction.getChildStorageTaskActions().hasNext()) {
            storageTaskCurrentAction = (StorageTaskChownNodeCurrentAction) storageTaskCurrentAction.getChildStorageTaskActions().next();
        }

        this.setStorageTaskAction(storageTaskCurrentAction);
        this.getTaskExceptionHandler().resetOnExceptionAction();
    }

    protected class StorageTaskChownNodeCurrentAction extends StorageTaskCurrentAction {
        private UUID ownerUserUUID;
        private UUID ownerGroupUUID;
        private Boolean updateSuccessful;
        private StorageNode targetStorageNode;

        protected StorageTaskChownNodeCurrentAction(StorageTaskChownNodeCurrentAction parentStorageTaskAction, StorageNode editedStorageNode, UUID user, UUID group, Boolean taskIsRecursive) {
            super(parentStorageTaskAction);

            this.updateSuccessful = false;
            this.targetStorageNode = editedStorageNode;

            if (taskIsRecursive == true && this.targetStorageNode.isDirectory()) {
                for (StorageNode childNode : targetStorageNode.getChildren()) {
                    this.getChildStorageTaskActions().add(new StorageTaskChownNodeCurrentAction(this, childNode, user, group, taskIsRecursive));
                    this.getChildStorageTaskActions().previous(); /* Required because iterator pushes forward on .add() which is an expected but unwanted behavior */
                }
            }
        }

        public UUID getOwnerUserUUID() {
            return this.ownerUserUUID;
        }


        public void setOwnerUserUUID(UUID ownerUserUUID) {
            this.ownerUserUUID = ownerUserUUID;
        }


        public UUID getOwnerGroupUUID() {
            return this.ownerGroupUUID;
        }

        public void setOwnerGroupUUID(UUID ownerGroupUUID) {
            this.ownerGroupUUID = ownerGroupUUID;
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
