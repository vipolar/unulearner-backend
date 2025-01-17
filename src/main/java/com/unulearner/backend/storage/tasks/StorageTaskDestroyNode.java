package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.util.Map;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.tasks.exception.Option;
import com.unulearner.backend.storage.tasks.exception.Option.Parameter;

import java.io.IOException;

@Component
@Scope("prototype")
public class StorageTaskDestroyNode extends StorageTaskBaseBatch {
    private StorageNode rootTargetStorageNode;

    public StorageTaskDestroyNode initialize(StorageNode targetStorageNode) {
        this.rootTargetStorageNode = targetStorageNode;

        final StorageTaskDestroyNodeCurrentAction storageTaskAction = new StorageTaskDestroyNodeCurrentAction(null, targetStorageNode);

        storageTaskAction.setActionHeader("Remove %s '%s' permanently".formatted(this.rootTargetStorageNode.getIsDirectory() ? "directory" : "file", this.rootTargetStorageNode.getUrl()));
        storageTaskAction.setMessage("%s removal task has been successfully initialized.".formatted(this.rootTargetStorageNode.getIsDirectory() ? "Directory" : "File"));

        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return this;
    }

    @Override
    public synchronized void execute(Map<String, Object> taskParameters) {
        this.advanceStorageTask(); /* Will advance task only if the current task is either done or waiting for the children tasks to be done */
        final StorageTaskDestroyNodeCurrentAction storageTaskCurrentAction = (StorageTaskDestroyNodeCurrentAction) this.getStorageTaskAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getTargetStorageNode().getId() == null) {
            storageTaskCurrentAction.setMessage("%s removal task finished successfully!".formatted(this.rootTargetStorageNode.getIsDirectory() ? "Directory" : "File"));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (cancel != null && cancel == true) {
            storageTaskCurrentAction.setMessage("%s removal task was cancelled.".formatted(this.rootTargetStorageNode.getIsDirectory() ? "Directory" : "File"));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        storageTaskCurrentAction.incrementAttemptCounter();
        storageTaskCurrentAction.setDeletionInProgress(true);
        while (storageTaskCurrentAction.getTargetStorageNode().getId() != null) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(storageTaskCurrentAction.getTargetStorageNode(), exceptionType);

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' removal was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be removed due to a persistent I/O exception".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }  
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' removal was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be removed due to an unexpected exception".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }  
                    }
                }

                storageTaskCurrentAction.setTargetStorageNode(this.storageTreeExecute().deleteStorageNode(storageTaskCurrentAction.getTargetStorageNode()));

                storageTaskCurrentAction.setMessage("%s '%s' has been permanently removed from storage successfully.".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                storageTaskCurrentAction.setExceptionMessage(null);
                storageTaskCurrentAction.setExceptionType(null);

                storageTaskCurrentAction.setDeletionInProgress(false);
                this.setCurrentState(TaskState.EXECUTING);                
                return;
            } catch (IOException exception) {
                storageTaskCurrentAction.setExceptionType("IOException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            } catch (Exception exception) {
                storageTaskCurrentAction.setExceptionType("Exception");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            }
        }
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

            if (this.targetStorageNode.getIsDirectory()) {
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
