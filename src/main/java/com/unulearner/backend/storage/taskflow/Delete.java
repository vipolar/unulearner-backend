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
public class Delete extends Base {
    private Entry targetEntry;

    public Delete initialize(Entry targetEntry) {
        this.targetEntry = targetEntry;

        final DeleteAction storageTaskAction = new DeleteAction(null, targetEntry);

        storageTaskAction.setActionHeader("Remove %s '%s' permanently".formatted(this.targetEntry.getIsDirectory() ? "directory" : "file", this.targetEntry.getUrl()));
        storageTaskAction.setMessage("%s removal task has been successfully initialized.".formatted(this.targetEntry.getIsDirectory() ? "Directory" : "File"));

        this.setCurrentAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return this;
    }

    @Override
    public synchronized void execute(Map<String, Object> taskParameters) {
        this.advance(); /* Will advance task only if the current task is either done or waiting for the children tasks to be done */
        final DeleteAction storageTaskCurrentAction = (DeleteAction) this.getCurrentAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskCurrentAction.getParentAction() == null && storageTaskCurrentAction.getTargetEntry().getId() == null) {
            storageTaskCurrentAction.setMessage("%s removal task finished successfully!".formatted(this.targetEntry.getIsDirectory() ? "Directory" : "File"));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (cancel != null && cancel == true) {
            storageTaskCurrentAction.setMessage("%s removal task was cancelled.".formatted(this.targetEntry.getIsDirectory() ? "Directory" : "File"));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            this.getExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetEntry(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        storageTaskCurrentAction.incrementAttemptCounter();
        while (storageTaskCurrentAction.getTargetEntry().getId() != null) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getExceptionHandler().getOnExceptionAction(storageTaskCurrentAction.getTargetEntry(), exceptionType);

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' removal was skipped...".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentAction();
                                    this.advance();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be removed due to a persistent I/O exception".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }  
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' removal was skipped...".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentAction();
                                    this.advance();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be removed due to an unexpected exception".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }  
                    }
                }

                storageTaskCurrentAction.setTargetEntry(this.storageExecutor().deleteEntry(storageTaskCurrentAction.getTargetEntry()));

                storageTaskCurrentAction.setMessage("%s '%s' has been permanently removed from storage successfully.".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl()));
                storageTaskCurrentAction.setExceptionMessage(null);
                storageTaskCurrentAction.setExceptionType(null);

                storageTaskCurrentAction.setDeletionSuccessful(true);
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
    public void skipCurrentAction() {
        DeleteAction storageTaskCurrentAction = (DeleteAction) this.getCurrentAction();

        if (storageTaskCurrentAction.getParentAction() != null) {
            storageTaskCurrentAction = (DeleteAction) storageTaskCurrentAction.getParentAction();

            if (storageTaskCurrentAction.getChildActions().hasPrevious()) {
                storageTaskCurrentAction = (DeleteAction) storageTaskCurrentAction.getChildActions().previous();
            }
        }

        this.setCurrentAction(storageTaskCurrentAction);
    }

    @Override
    public void advance() {
        DeleteAction storageTaskCurrentAction = (DeleteAction) this.getCurrentAction();

        if (storageTaskCurrentAction.getDeletionSuccessful() != true) {
            return;
        }

        while (storageTaskCurrentAction.getChildActions().hasPrevious() || storageTaskCurrentAction.getParentAction() != null) {
            while (storageTaskCurrentAction.getChildActions().hasPrevious()) {
                storageTaskCurrentAction = (DeleteAction) storageTaskCurrentAction.getChildActions().previous();
                if (storageTaskCurrentAction.getChildActions().hasPrevious()) {
                    storageTaskCurrentAction.getParentAction().getChildActions().next();
                }
            }

            if (storageTaskCurrentAction.getTargetEntry().getIsAccessible()) {
                break;
            }

            while (!storageTaskCurrentAction.getChildActions().hasPrevious() && storageTaskCurrentAction.getParentAction() != null) {
                storageTaskCurrentAction = (DeleteAction) storageTaskCurrentAction.getParentAction();
            }
        }

        this.getExceptionHandler().resetOnExceptionAction();
        this.setCurrentAction(storageTaskCurrentAction);
    }

    protected class DeleteAction extends Action {
        private Entry targetEntry;
        private Boolean deletionSuccessful;

        protected DeleteAction(DeleteAction parentAction, Entry targetEntry) {
            super(parentAction);

            this.targetEntry = targetEntry;
            this.deletionSuccessful = false;

            if (this.targetEntry.getIsDirectory()) {
                for (Entry childNode : targetEntry.getChildren()) {
                    this.getChildActions().add(new DeleteAction(this, childNode));
                }
            }
        }

        protected Entry getTargetEntry() {
            return this.targetEntry;
        }

        protected void setTargetEntry(Entry targetStorageNode) {
            this.targetEntry = targetStorageNode;
        }

        protected Boolean getDeletionSuccessful() {
            return this.deletionSuccessful;
        }

        protected void setDeletionSuccessful(Boolean deleted) {
            this.deletionSuccessful = deleted;
        }
    }
}
