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

public class StorageTaskUpdateNode extends StorageTaskBase {
    public StorageTaskUpdateNode(StorageTree storageTree, StorageNode editedStorageNode, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        final StorageTaskUpdateNodeAction storageTaskAction = new StorageTaskUpdateNodeAction(editedStorageNode);

        storageTaskAction.setActionHeader("Update '%s' %s".formatted(editedStorageNode.getUrl(), editedStorageNode.isDirectory() ? "directory" : "file"));
        storageTaskAction.setMessage("%s update task has been successfully initialized".formatted(editedStorageNode.isDirectory() ? "Directory" : "File"));
    
        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    @Override
    public synchronized StorageServiceResponse execute(Map<String, Object> taskParameters) {
        final StorageTaskUpdateNodeAction storageTaskAction = (StorageTaskUpdateNodeAction) this.getStorageTaskAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskAction.getUpdateSuccessful() == true) {
            storageTaskAction.setMessage("%s '%s' update task finished successfully!".formatted(storageTaskAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getTargetStorageNode().getUrl()));
            this.setCurrentState(TaskState.COMPLETED);
            
            return this.getStorageServiceResponse();
        }

        if (cancel != null && cancel == true) {
            storageTaskAction.setMessage("%s '%s' update task was cancelled...".formatted(storageTaskAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getTargetStorageNode().getUrl()));
            this.setCurrentState(TaskState.CANCELLED);
            
            return this.getStorageServiceResponse();
        }

        if (onExceptionAction != null) {
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskAction.getTargetStorageNode(), storageTaskAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        storageTaskAction.incrementAttemptCounter();
        while (storageTaskAction.getUpdateSuccessful() != true) {
            try {
                final String exceptionType = storageTaskAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(exceptionType, storageTaskAction.getTargetStorageNode());

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskAction.setMessage("Update of '%s' %s was skipped...".formatted(storageTaskAction.getTargetStorageNode().getUrl(), storageTaskAction.getTargetStorageNode().isDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskAction.setMessage("%s '%s' could not be updated due to a persistent I/O exception occurring".formatted(storageTaskAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getTargetStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskAction.setMessage("Update of '%s' %s was skipped...".formatted(storageTaskAction.getTargetStorageNode().getUrl(), storageTaskAction.getTargetStorageNode().isDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskAction.setMessage("%s '%s' could not be updated due to an unexpected exception occurring".formatted(storageTaskAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getTargetStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }   
                    }
                }

                this.storageTreeExecute().updateStorageNode(storageTaskAction.getTargetStorageNode());

                storageTaskAction.setMessage("%s '%s' has been updated successfully!".formatted(storageTaskAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getTargetStorageNode().getUrl()));
                storageTaskAction.setUpdateSuccessful(true);
                storageTaskAction.setExceptionMessage(null);
                storageTaskAction.setExceptionType(null);
                this.setCurrentState(TaskState.EXECUTING);

                return this.getStorageServiceResponse();
            } catch (IOException exception) {
                storageTaskAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (Exception exception) {
                storageTaskAction.setExceptionType("RuntimeException");
                storageTaskAction.setExceptionMessage(exception.getMessage());
            }
        }

        return this.getStorageServiceResponse();
    }

    protected class StorageTaskUpdateNodeAction extends StorageTaskAction {
        private Boolean updateSuccessful;
        private StorageNode targetStorageNode;

        protected StorageTaskUpdateNodeAction(StorageNode targetStorageNode) {
            super();

            this.updateSuccessful = false;
            this.targetStorageNode = targetStorageNode;
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
