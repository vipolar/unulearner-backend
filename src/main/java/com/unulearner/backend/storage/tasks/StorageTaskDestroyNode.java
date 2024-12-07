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

public class StorageTaskDestroyNode extends StorageTaskBaseBatch {
    private final StorageNode rootTargetStorageNode;

    public StorageTaskDestroyNode(StorageTree storageTree, StorageNode targetStorageNode, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        this.rootTargetStorageNode = targetStorageNode;

        final StorageTaskDestroyNodeCurrentAction storageTaskAction = new StorageTaskDestroyNodeCurrentAction(null, targetStorageNode);

        storageTaskAction.setActionHeader("Remove %s '%s' permanently".formatted(this.rootTargetStorageNode.isDirectory() ? "directory" : "file", this.rootTargetStorageNode.getUrl()));
        storageTaskAction.setMessage("%s removal task has been successfully initialized.".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File"));

        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    @Override
    public synchronized StorageServiceResponse execute(Map<String, Object> taskParameters) {
        this.advanceStorageTask(); /* Will advance task only if the current task is either done or waiting for the children tasks to be done */
        final StorageTaskDestroyNodeCurrentAction storageTaskCurrentAction = (StorageTaskDestroyNodeCurrentAction) this.getStorageTaskAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getTargetStorageNode().getId() == null) {
            storageTaskCurrentAction.setMessage("%s removal task finished successfully!".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File"));
            this.setCurrentState(TaskState.COMPLETED);
            
            return this.getStorageServiceResponse();
        }

        if (cancel != null && cancel == true) {
            storageTaskCurrentAction.setMessage("%s removal task was cancelled.".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File"));
            this.setCurrentState(TaskState.CANCELLED);
            
            return this.getStorageServiceResponse();
        }

        if (onExceptionAction != null) {
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        storageTaskCurrentAction.incrementAttemptCounter();
        storageTaskCurrentAction.setDeletionInProgress(true);
        while (storageTaskCurrentAction.getTargetStorageNode().getId() != null) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(exceptionType, storageTaskCurrentAction.getTargetStorageNode());

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' removal was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be removed due to a persistent I/O exception".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);

                                    return this.getStorageServiceResponse();
                            }  
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' removal was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();

                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be removed due to an unexpected exception".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);

                                    return this.getStorageServiceResponse();
                            }  
                    }
                }

                storageTaskCurrentAction.setTargetStorageNode(this.storageTreeExecute().deleteStorageNode(storageTaskCurrentAction.getTargetStorageNode()));

                storageTaskCurrentAction.setMessage("%s '%s' has been permanently removed from storage successfully.".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                storageTaskCurrentAction.setExceptionMessage(null);
                storageTaskCurrentAction.setExceptionType(null);

                storageTaskCurrentAction.setDeletionInProgress(false);
                this.setCurrentState(TaskState.EXECUTING);                
                return this.getStorageServiceResponse();
            } catch (IOException exception) {
                storageTaskCurrentAction.setExceptionType("IOException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            } catch (Exception exception) {
                storageTaskCurrentAction.setExceptionType("Exception");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            }
        }

        return this.getStorageServiceResponse();
    }

    @Override
    protected void skipStorageTaskCurrentAction() {
        StorageTaskDestroyNodeCurrentAction storageTaskCurrentAction = (StorageTaskDestroyNodeCurrentAction) this.getStorageTaskAction();

        if (storageTaskCurrentAction.getParentStorageTaskAction() != null) {
            storageTaskCurrentAction = (StorageTaskDestroyNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction();

            if (storageTaskCurrentAction.getChildStorageTaskActions().hasPrevious()) {
                storageTaskCurrentAction = (StorageTaskDestroyNodeCurrentAction) storageTaskCurrentAction.getChildStorageTaskActions().previous();
            }
        }

        this.setStorageTaskAction(storageTaskCurrentAction);
    }

    @Override
    protected void advanceStorageTask() {
        StorageTaskDestroyNodeCurrentAction storageTaskCurrentAction = (StorageTaskDestroyNodeCurrentAction) this.getStorageTaskAction();

        if (storageTaskCurrentAction.getDeletionInProgress()) {
            return;
        }

        while (storageTaskCurrentAction.getChildStorageTaskActions().hasPrevious() || storageTaskCurrentAction.getParentStorageTaskAction() != null) {
            while (storageTaskCurrentAction.getChildStorageTaskActions().hasPrevious()) {
                storageTaskCurrentAction = (StorageTaskDestroyNodeCurrentAction) storageTaskCurrentAction.getChildStorageTaskActions().previous();
                if (storageTaskCurrentAction.getChildStorageTaskActions().hasPrevious()) {
                    storageTaskCurrentAction.getParentStorageTaskAction().getChildStorageTaskActions().next();
                }
            }

            if (storageTaskCurrentAction.getTargetStorageNode().getIsAccessible()) {
                break;
            }

            while (!storageTaskCurrentAction.getChildStorageTaskActions().hasPrevious() && storageTaskCurrentAction.getParentStorageTaskAction() != null) {
                storageTaskCurrentAction = (StorageTaskDestroyNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction();
            }
        }

        this.getTaskExceptionHandler().resetOnExceptionAction();
        this.setStorageTaskAction(storageTaskCurrentAction);
    }

    protected class StorageTaskDestroyNodeCurrentAction extends StorageTaskCurrentAction {
        private Boolean deletionInProgress;
        private StorageNode targetStorageNode;

        protected StorageTaskDestroyNodeCurrentAction(StorageTaskDestroyNodeCurrentAction parentStorageTaskAction, StorageNode targetStorageNode) {
            super(parentStorageTaskAction);

            this.deletionInProgress = false;
            this.targetStorageNode = targetStorageNode;

            if (this.targetStorageNode.isDirectory()) {
                for (StorageNode childNode : targetStorageNode.getChildren()) {
                    this.getChildStorageTaskActions().add(new StorageTaskDestroyNodeCurrentAction(this, childNode));
                }
            }
        }

        protected Boolean getDeletionInProgress() {
            return this.deletionInProgress;
        }

        protected void setDeletionInProgress(Boolean deletionInProgress) {
            this.deletionInProgress = deletionInProgress;
        }

        protected StorageNode getTargetStorageNode() {
            return this.targetStorageNode;
        }
        
        protected void setTargetStorageNode(StorageNode targetStorageNode) {
            this.targetStorageNode = targetStorageNode;
        }
    }
}
