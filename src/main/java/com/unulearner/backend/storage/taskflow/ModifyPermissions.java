package com.unulearner.backend.storage.taskflow;

import java.util.ArrayList;
import java.util.Map;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.unulearner.backend.storage.model.Entry;
import com.unulearner.backend.storage.taskflow.constant.TaskState;
import com.unulearner.backend.storage.taskflow.exception.Option;
import com.unulearner.backend.storage.taskflow.exception.Option.Parameter;

import java.io.IOException;

@Component
@Scope("prototype")
public class ModifyPermissions extends Base {
    private Entry targetEntry;

    public ModifyPermissions initialize(Entry targetEntry, String permissionsOptions, Boolean taskIsRecursive) {
        final Boolean modifyRecursively = taskIsRecursive != null ? taskIsRecursive : false;
        this.targetEntry = targetEntry;

        final ModifyPermissionsAction storageTaskAction = new ModifyPermissionsAction(null, targetEntry, permissionsOptions, modifyRecursively);

        storageTaskAction.setActionHeader("Update %s '%s' permission flags".formatted(this.targetEntry.getIsDirectory() ? "directory" : "file", this.targetEntry.getUrl()));
        storageTaskAction.setMessage("%s permission flags update task has been successfully initialized".formatted(this.targetEntry.getIsDirectory() ? "Directory" : "File"));
    
        this.setCurrentAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return this;
    }

    @Override
    public synchronized void execute(Map<String, Object> taskParameters) {
        this.advance(); /* Will advance task only if the current task is either done or waiting for the children tasks to be done */
        final ModifyPermissionsAction storageTaskCurrentAction = (ModifyPermissionsAction) this.getCurrentAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskCurrentAction.getParentAction() == null && storageTaskCurrentAction.getUpdateCommitted()) {
            storageTaskCurrentAction.setMessage("%s '%s' permission flags update task finished successfully!".formatted(this.targetEntry.getIsDirectory() ? "Directory" : "File", this.targetEntry.getUrl()));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (cancel != null && cancel == true) {
            storageTaskCurrentAction.setMessage("%s '%s' permission flags update task was cancelled...".formatted(this.targetEntry.getIsDirectory() ? "Directory" : "File", this.targetEntry.getUrl()));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            this.getExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetEntry(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        storageTaskCurrentAction.incrementAttemptCounter();
        while (storageTaskCurrentAction.getPermissionsModified() != true || storageTaskCurrentAction.getUpdateCommitted() != true) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getExceptionHandler().getOnExceptionAction(storageTaskCurrentAction.getTargetEntry(), exceptionType);

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Permission flags update of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetEntry().getUrl(), storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentAction();
                                    this.advance();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' permission flags could not be updated due to a persistent I/O exception".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("Permission flags update of '%s' %s was skipped...".formatted(storageTaskCurrentAction.getTargetEntry().getUrl(), storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentAction();
                                    this.advance();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' permission flags could not be updated due to an unexpected exception".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }   
                    }
                }

                if (storageTaskCurrentAction.getPermissionsModified() != true) {
                    this.storageExecutor().modifyEntryPermissions(storageTaskCurrentAction.getTargetEntry(), storageTaskCurrentAction.getPermissionsOptions());
                    storageTaskCurrentAction.setPermissionsModified(true);
                }

                if (storageTaskCurrentAction.getUpdateCommitted() != true) {
                    this.storageExecutor().publishEntry(storageTaskCurrentAction.getTargetEntry());
                    storageTaskCurrentAction.setUpdateCommitted(true);
                }

                storageTaskCurrentAction.setMessage("%s '%s' permission flags have been updated successfully!".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl()));
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
        ModifyPermissionsAction storageTaskCurrentAction = (ModifyPermissionsAction) this.getCurrentAction();

        if (storageTaskCurrentAction.getParentAction() != null) {
            this.setCurrentAction((ModifyPermissionsAction) storageTaskCurrentAction.getParentAction());
        }
    }

    @Override
    public void advance() {
        ModifyPermissionsAction storageTaskCurrentAction = (ModifyPermissionsAction) this.getCurrentAction();

        if (!storageTaskCurrentAction.getPermissionsModified() || !storageTaskCurrentAction.getUpdateCommitted()) {
            return;
        }

        while (!storageTaskCurrentAction.getChildActions().hasNext()) {
            if (storageTaskCurrentAction.getParentAction() == null) {
                break;
            }

            storageTaskCurrentAction = (ModifyPermissionsAction) storageTaskCurrentAction.getParentAction();
        }

        if (storageTaskCurrentAction.getChildActions().hasNext()) {
            storageTaskCurrentAction = (ModifyPermissionsAction) storageTaskCurrentAction.getChildActions().next();
        }

        this.setCurrentAction(storageTaskCurrentAction);
        this.getExceptionHandler().resetOnExceptionAction();
    }

    protected class ModifyPermissionsAction extends Action {
        private Entry targetEntry;
        private Boolean updateCommitted;
        private String permissionsOptions;
        private Boolean permissionsModified;

        protected ModifyPermissionsAction(ModifyPermissionsAction parentAction, Entry targetEntry, String permissionsOptions, Boolean taskIsRecursive) {
            super(parentAction);

            this.updateCommitted = false;
            this.targetEntry = targetEntry;
            this.permissionsModified = false;
            this.permissionsOptions = permissionsOptions;

            if (taskIsRecursive == true && this.targetEntry.getIsDirectory()) {
                for (Entry childNode : this.targetEntry.getChildren()) {
                    this.getChildActions().add(new ModifyPermissionsAction(this, childNode, permissionsOptions, taskIsRecursive));
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

        public Boolean getPermissionsModified() {
            return this.permissionsModified;
        }

        public void setPermissionsModified(Boolean chmodified) {
            this.permissionsModified = chmodified;
        }
    }
}
