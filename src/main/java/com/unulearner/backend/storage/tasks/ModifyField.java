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
public class ModifyField extends Base { /* TODO: everything about this!!! */
    public ModifyField initialize(Entry targetEntry, Map<String, Object> metadataValues) {
        final ModifyFieldAction storageTaskAction = new ModifyFieldAction(targetEntry);

        storageTaskAction.setActionHeader("Update '%s' %s".formatted(targetEntry.getUrl(), targetEntry.getIsDirectory() ? "directory" : "file"));
        storageTaskAction.setMessage("%s update task has been successfully initialized".formatted(targetEntry.getIsDirectory() ? "Directory" : "File"));
    
        this.setCurrentAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return this;
    }

    @Override
    public synchronized void execute(Map<String, Object> taskParameters) {
        final ModifyFieldAction storageTaskAction = (ModifyFieldAction) this.getCurrentAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskAction.getUpdateSuccessful() == true) {
            storageTaskAction.setMessage("%s '%s' update task finished successfully!".formatted(storageTaskAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskAction.getTargetEntry().getUrl()));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (cancel != null && cancel == true) {
            storageTaskAction.setMessage("%s '%s' update task was cancelled...".formatted(storageTaskAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskAction.getTargetEntry().getUrl()));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            this.getExceptionHandler().setOnExceptionAction(storageTaskAction.getTargetEntry(), storageTaskAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        storageTaskAction.incrementAttemptCounter();
        while (storageTaskAction.getUpdateSuccessful() != true) {
            try {
                final String exceptionType = storageTaskAction.getExceptionType();
                final String exceptionAction = this.getExceptionHandler().getOnExceptionAction(storageTaskAction.getTargetEntry(), exceptionType);

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskAction.setMessage("Update of '%s' %s was skipped...".formatted(storageTaskAction.getTargetEntry().getUrl(), storageTaskAction.getTargetEntry().getIsDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskAction.getTargetEntry().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskAction.setMessage("%s '%s' could not be updated due to a persistent I/O exception occurring".formatted(storageTaskAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskAction.getTargetEntry().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskAction.setMessage("Update of '%s' %s was skipped...".formatted(storageTaskAction.getTargetEntry().getUrl(), storageTaskAction.getTargetEntry().getIsDirectory() ? "directory" : "file"));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskAction.getTargetEntry().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskAction.setMessage("%s '%s' could not be updated due to an unexpected exception occurring".formatted(storageTaskAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskAction.getTargetEntry().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }   
                    }
                }

                this.storageExecutor().modifyEntry(storageTaskAction.getTargetEntry());

                storageTaskAction.setMessage("%s '%s' has been updated successfully!".formatted(storageTaskAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskAction.getTargetEntry().getUrl()));
                storageTaskAction.setUpdateSuccessful(true);
                storageTaskAction.setExceptionMessage(null);
                storageTaskAction.setExceptionType(null);
                this.setCurrentState(TaskState.EXECUTING);
                return;
            } catch (IOException exception) {
                storageTaskAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (Exception exception) {
                storageTaskAction.setExceptionType("RuntimeException");
                storageTaskAction.setExceptionMessage(exception.getMessage());
            }
        }
    }

    protected class ModifyFieldAction extends Action {
        private Boolean updateSuccessful;
        private Entry targetEntry;

        protected ModifyFieldAction(Entry targetEntry) {
            super(null);

            this.updateSuccessful = false;
            this.targetEntry = targetEntry;
        }

        public Boolean getUpdateSuccessful() {
            return this.updateSuccessful;
        }

        public void setUpdateSuccessful(Boolean updateSuccessful) {
            this.updateSuccessful = updateSuccessful;
        }

        public Entry getTargetEntry() {
            return this.targetEntry;
        }

        protected void setTargetEntry(Entry targetEntry) {
            this.targetEntry = targetEntry;
        }
    }
}
