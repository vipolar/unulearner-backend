package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.util.Map;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.unulearner.backend.storage.models.Entry;
import com.unulearner.backend.storage.tasks.constant.TaskState;

import com.unulearner.backend.storage.tasks.exception.Option;
import com.unulearner.backend.storage.tasks.exception.Option.Parameter;

import java.io.IOException;

@Component
@Scope("prototype")
public class ModifyOwnership extends Base {
    private Entry targetEntry;

    public ModifyOwnership initialize(Entry targetEntry, String pairedOwners, Boolean taskIsRecursive) {
        final Boolean modifyRecursively = taskIsRecursive != null ? taskIsRecursive : false;
        this.targetEntry = targetEntry;

        final ModifyOwnershipAction storageTaskAction = new ModifyOwnershipAction(null, targetEntry, pairedOwners, modifyRecursively);

        storageTaskAction.setActionHeader("Change %s %s owners".formatted(this.targetEntry.getIsDirectory() ? "directory" : "file", this.targetEntry.getUrl()));
        storageTaskAction.setMessage("%s owner change task has been successfully initialized".formatted(this.targetEntry.getIsDirectory() ? "Directory" : "File"));

        this.setCurrentAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return this;
    }

    @Override
    public synchronized void execute(Map<String, Object> taskParameters) {
        this.advance(); /* Will advance task only if the current task is either done or waiting for the children tasks to be done */
        final ModifyOwnershipAction storageTaskCurrentAction = (ModifyOwnershipAction) this.getCurrentAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskCurrentAction.getParentAction() == null && storageTaskCurrentAction.getUpdateCommitted()) {
            storageTaskCurrentAction.setMessage("%s '%s' ownership change task finished successfully!".formatted(this.targetEntry.getIsDirectory() ? "Directory" : "File", this.targetEntry.getUrl()));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (cancel != null && cancel == true) {
            storageTaskCurrentAction.setMessage("%s '%s' ownership change task was cancelled...".formatted(this.targetEntry.getIsDirectory() ? "Directory" : "File", this.targetEntry.getUrl()));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            this.getExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetEntry(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        storageTaskCurrentAction.incrementAttemptCounter();
        while (storageTaskCurrentAction.getOwnershipModified() != true || storageTaskCurrentAction.getUpdateCommitted() != true) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getExceptionHandler().getOnExceptionAction(storageTaskCurrentAction.getTargetEntry(), exceptionType);

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Ownership change of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetEntry().getUrl(), storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentAction();
                                    this.advance();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' ownership could not be changed due to a persistent I/O exception".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Ownership change of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetEntry().getUrl(), storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentAction();
                                    this.advance();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' ownership could not be changed due to an unexpected exception".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }   
                    }
                }

                if (storageTaskCurrentAction.getOwnershipModified() != true) {
                    this.storageExecutor().modifyEntryOwnership(storageTaskCurrentAction.getTargetEntry(), storageTaskCurrentAction.getPairedOwners());
                    storageTaskCurrentAction.setOwnershipModified(true);
                }
                
                if (storageTaskCurrentAction.getUpdateCommitted() != true) {
                    this.storageExecutor().publishEntry(storageTaskCurrentAction.getTargetEntry());
                    storageTaskCurrentAction.setUpdateCommitted(true);
                }

                storageTaskCurrentAction.setMessage("%s '%s' ownership has been changed successfully!".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl()));
                storageTaskCurrentAction.setExceptionMessage(null);
                storageTaskCurrentAction.setExceptionType(null);
                this.setCurrentState(TaskState.EXECUTING);
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
    public void skipCurrentAction() { /* TODO: THIS MAKES NO FUCKING SENSE! */
        ModifyOwnershipAction storageTaskCurrentAction = (ModifyOwnershipAction) this.getCurrentAction();

        if (storageTaskCurrentAction.getParentAction() != null) {
            this.setCurrentAction((ModifyOwnershipAction) storageTaskCurrentAction.getParentAction());
        }
    }

    @Override
    public void advance() {
        ModifyOwnershipAction storageTaskCurrentAction = (ModifyOwnershipAction) this.getCurrentAction();

        if (!storageTaskCurrentAction.getOwnershipModified() || !storageTaskCurrentAction.getUpdateCommitted()) {
            return;
        }

        while (!storageTaskCurrentAction.getChildActions().hasNext()) {
            if (storageTaskCurrentAction.getParentAction() == null) {
                break;
            }

            storageTaskCurrentAction = (ModifyOwnershipAction) storageTaskCurrentAction.getParentAction();
        }

        if (storageTaskCurrentAction.getChildActions().hasNext()) {
            storageTaskCurrentAction = (ModifyOwnershipAction) storageTaskCurrentAction.getChildActions().next();
        }

        this.setCurrentAction(storageTaskCurrentAction);
        this.getExceptionHandler().resetOnExceptionAction();
    }

    protected class ModifyOwnershipAction extends Action {
        private Entry targetEntry;
        private String pairedOwners;
        private Boolean updateCommitted;
        private Boolean ownershipModified;

        protected ModifyOwnershipAction(ModifyOwnershipAction parentAction, Entry targetEntry, String pairedOwners, Boolean taskIsRecursive) {
            super(parentAction);

            this.updateCommitted = false;
            this.ownershipModified = false;
            this.targetEntry = targetEntry;

            if (taskIsRecursive == true && this.targetEntry.getIsDirectory()) {
                for (Entry childNode : targetEntry.getChildren()) {
                    this.getChildActions().add(new ModifyOwnershipAction(this, childNode, pairedOwners, taskIsRecursive));
                    this.getChildActions().previous(); /* Required because iterator pushes forward on .add() which is an expected but unwanted behavior */
                }
            }
        }

        public Entry getTargetEntry() {
            return this.targetEntry;
        }

        protected void setTargetEntry(Entry targetStorageNode) {
            this.targetEntry = targetStorageNode;
        }

        public String getPairedOwners() {
            return this.pairedOwners;
        }

        public void setPairedOwners(String pairedOwners) {
            this.pairedOwners = pairedOwners;
        }

        public Boolean getUpdateCommitted() {
            return this.updateCommitted;
        }

        public void setUpdateCommitted(Boolean updateSuccessful) {
            this.updateCommitted = updateSuccessful;
        }

        public Boolean getOwnershipModified() {
            return this.ownershipModified;
        }

        public void setOwnershipModified(Boolean chownSuccessful) {
            this.ownershipModified = chownSuccessful;
        }
    }
}
