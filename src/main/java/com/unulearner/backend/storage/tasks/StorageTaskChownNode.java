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
public class StorageTaskChownNode extends StorageTaskBaseBatch {
    private StorageNode rootTargetStorageNode;

    public StorageTaskChownNode initialize(StorageNode targetStorageNode, String pairedOwners, Boolean taskIsRecursive) {
        final Boolean updateTaskIsRecursive = taskIsRecursive != null ? taskIsRecursive : false;
        this.rootTargetStorageNode = targetStorageNode;

        final StorageTaskChownNodeCurrentAction storageTaskAction = new StorageTaskChownNodeCurrentAction(null, targetStorageNode, pairedOwners, updateTaskIsRecursive);

        storageTaskAction.setActionHeader("Change %s %s owners".formatted(this.rootTargetStorageNode.getIsDirectory() ? "directory" : "file", this.rootTargetStorageNode.getUrl()));
        storageTaskAction.setMessage("%s owner change task has been successfully initialized".formatted(this.rootTargetStorageNode.getIsDirectory() ? "Directory" : "File"));

        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return this;
    }

    @Override
    public synchronized void execute(Map<String, Object> taskParameters) {
        this.advanceStorageTask(); /* Will advance task only if the current task is either done or waiting for the children tasks to be done */
        final StorageTaskChownNodeCurrentAction storageTaskCurrentAction = (StorageTaskChownNodeCurrentAction) this.getStorageTaskAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getUpdateSuccessful()) {
            storageTaskCurrentAction.setMessage("%s '%s' ownership change task finished successfully!".formatted(this.rootTargetStorageNode.getIsDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getUrl()));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (cancel != null && cancel == true) {
            storageTaskCurrentAction.setMessage("%s '%s' ownership change task was cancelled...".formatted(this.rootTargetStorageNode.getIsDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getUrl()));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        storageTaskCurrentAction.incrementAttemptCounter();
        while (storageTaskCurrentAction.getChownSuccessful() != true || storageTaskCurrentAction.getUpdateSuccessful() != true) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(storageTaskCurrentAction.getTargetStorageNode(), exceptionType);

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Ownership change of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' ownership could not be changed due to a persistent I/O exception".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Ownership change of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' ownership could not be changed due to an unexpected exception".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }   
                    }
                }

                if (storageTaskCurrentAction.getChownSuccessful() != true) {
                    this.storageTreeExecute().chownStorageNode(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getPairedOwners());
                    storageTaskCurrentAction.setChownSuccessful(true);
                }
                
                if (storageTaskCurrentAction.getUpdateSuccessful() != true) {
                    this.storageTreeExecute().publishStorageNode(storageTaskCurrentAction.getTargetStorageNode());
                    storageTaskCurrentAction.setUpdateSuccessful(true);
                }

                storageTaskCurrentAction.setMessage("%s '%s' ownership has been changed successfully!".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
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
        StorageTaskChownNodeCurrentAction storageTaskCurrentAction = (StorageTaskChownNodeCurrentAction) this.getStorageTaskAction();

        if (storageTaskCurrentAction.getParentStorageTaskAction() != null) {
            this.setStorageTaskAction((StorageTaskChownNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction());
        }
    }

    @Override
    protected void advanceStorageTask() {
        StorageTaskChownNodeCurrentAction storageTaskCurrentAction = (StorageTaskChownNodeCurrentAction) this.getStorageTaskAction();

        if (!storageTaskCurrentAction.getChownSuccessful() || !storageTaskCurrentAction.getUpdateSuccessful()) {
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
        private String pairedOwners;
        private Boolean chownSuccessful;
        private Boolean updateSuccessful;
        private StorageNode targetStorageNode;

        protected StorageTaskChownNodeCurrentAction(StorageTaskChownNodeCurrentAction parentStorageTaskAction, StorageNode editedStorageNode, String pairedOwners, Boolean taskIsRecursive) {
            super(parentStorageTaskAction);

            this.chownSuccessful = false;
            this.updateSuccessful = false;
            this.targetStorageNode = editedStorageNode;

            if (taskIsRecursive == true && this.targetStorageNode.getIsDirectory()) {
                for (StorageNode childNode : targetStorageNode.getChildren()) {
                    this.getChildStorageTaskActions().add(new StorageTaskChownNodeCurrentAction(this, childNode, pairedOwners, taskIsRecursive));
                    this.getChildStorageTaskActions().previous(); /* Required because iterator pushes forward on .add() which is an expected but unwanted behavior */
                }
            }
        }

        public String getPairedOwners() {
            return this.pairedOwners;
        }

        public void setPairedOwners(String pairedOwners) {
            this.pairedOwners = pairedOwners;
        }

        public Boolean getChownSuccessful() {
            return this.chownSuccessful;
        }

        public void setChownSuccessful(Boolean chownSuccessful) {
            this.chownSuccessful = chownSuccessful;
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
