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
public class StorageTaskChmodNode extends StorageTaskBaseBatch {
    private StorageNode rootTargetStorageNode;

    public StorageTaskChmodNode initialize(StorageNode targetStorageNode, String permissionsOptions, Boolean taskIsRecursive) {
        final Boolean updateTaskIsRecursive = taskIsRecursive != null ? taskIsRecursive : false;
        this.rootTargetStorageNode = targetStorageNode;

        final StorageTaskChmodNodeCurrentAction storageTaskAction = new StorageTaskChmodNodeCurrentAction(null, targetStorageNode, permissionsOptions, updateTaskIsRecursive);

        storageTaskAction.setActionHeader("Update %s '%s' permission flags".formatted(this.rootTargetStorageNode.getIsDirectory() ? "directory" : "file", this.rootTargetStorageNode.getUrl()));
        storageTaskAction.setMessage("%s permission flags update task has been successfully initialized".formatted(this.rootTargetStorageNode.getIsDirectory() ? "Directory" : "File"));
    
        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return this;
    }

    @Override
    public synchronized void execute(Map<String, Object> taskParameters) {
        this.advanceStorageTask(); /* Will advance task only if the current task is either done or waiting for the children tasks to be done */
        final StorageTaskChmodNodeCurrentAction storageTaskCurrentAction = (StorageTaskChmodNodeCurrentAction) this.getStorageTaskAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getUpdateCommitted()) {
            storageTaskCurrentAction.setMessage("%s '%s' permission flags update task finished successfully!".formatted(this.rootTargetStorageNode.getIsDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getUrl()));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (cancel != null && cancel == true) {
            storageTaskCurrentAction.setMessage("%s '%s' permission flags update task was cancelled...".formatted(this.rootTargetStorageNode.getIsDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getUrl()));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        storageTaskCurrentAction.incrementAttemptCounter();
        while (storageTaskCurrentAction.getChmodified() != true || storageTaskCurrentAction.getUpdateCommitted() != true) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(storageTaskCurrentAction.getTargetStorageNode(), exceptionType);

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Permission flags update of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' permission flags could not be updated due to a persistent I/O exception".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Permission flags update of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' permission flags could not be updated due to an unexpected exception".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }   
                    }
                }

                if (storageTaskCurrentAction.getChmodified() != true) {
                    this.storageTreeExecute().chmodStorageNode(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getPermissionsOptions());
                    storageTaskCurrentAction.setChmodified(true);
                }

                if (storageTaskCurrentAction.getUpdateCommitted() != true) {
                    this.storageTreeExecute().publishStorageNode(storageTaskCurrentAction.getTargetStorageNode());
                    storageTaskCurrentAction.setUpdateCommitted(true);
                }

                storageTaskCurrentAction.setMessage("%s '%s' permission flags have been updated successfully!".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                storageTaskCurrentAction.setExceptionMessage(null);
                storageTaskCurrentAction.setExceptionType(null);
                this.setCurrentState(TaskState.EXECUTING);
                this.advanceStorageTask();
                return;
            } catch (IOException exception) { /* TODO: catch some actual fucking exceptions! */
                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            } catch (Exception exception) {
                storageTaskCurrentAction.setExceptionType("RuntimeException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            }
        }
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

        if (!storageTaskCurrentAction.getChmodified() || !storageTaskCurrentAction.getUpdateCommitted()) {
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
        private Boolean chmodified;
        private Boolean updateCommitted;
        private String permissionsOptions;
        private StorageNode targetStorageNode;

        protected StorageTaskChmodNodeCurrentAction(StorageTaskChmodNodeCurrentAction parentStorageTaskAction, StorageNode targetStorageNode, String permissionsOptions, Boolean taskIsRecursive) {
            super(parentStorageTaskAction);

            this.chmodified = false;
            this.updateCommitted = false;
            this.permissionsOptions = permissionsOptions;
            this.targetStorageNode = targetStorageNode;

            if (taskIsRecursive == true && this.targetStorageNode.getIsDirectory()) {
                for (StorageNode childNode : this.targetStorageNode.getChildren()) {
                    this.getChildStorageTaskActions().add(new StorageTaskChmodNodeCurrentAction(this, childNode, permissionsOptions, taskIsRecursive));
                    this.getChildStorageTaskActions().previous(); /* Required because iterator pushes forward on .add() which is an expected but unwanted behavior */
                }
            }
        }

        public Boolean getChmodified() {
            return this.chmodified;
        }

        public void setChmodified(Boolean chmodified) {
            this.chmodified = chmodified;
        }

        public Boolean getUpdateCommitted() {
            return this.updateCommitted;
        }

        public void setUpdateCommitted(Boolean updateCommitted) {
            this.updateCommitted = updateCommitted;
        }

        public String getPermissionsOptions() {
            return this.permissionsOptions;
        }

        public void setPermissionsOptions(String permissionsOptions) {
            this.permissionsOptions = permissionsOptions;
        }

        public StorageNode getTargetStorageNode() {
            return this.targetStorageNode;
        }

        protected void setTargetStorageNode(StorageNode targetStorageNode) {
            this.targetStorageNode = targetStorageNode;
        }
    }
}
