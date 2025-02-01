package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.util.Map;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.unulearner.backend.storage.models.Entry;
import com.unulearner.backend.storage.models.utility.EntryName;
import com.unulearner.backend.storage.tasks.constant.TaskState;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.DirectoryNotEmptyException;
import com.unulearner.backend.storage.tasks.exception.Option;
import com.unulearner.backend.storage.tasks.exception.Option.Parameter;
import com.unulearner.backend.storage.exceptions.entry.EntryNameGenerationException;
import com.unulearner.backend.storage.exceptions.entry.EntryNameValidationException;

@Component
@Scope("prototype")
public class Transfer extends Base {
    private Entry initialDestinationEntry;
    private Entry initialTargetEntry;

    public Transfer initialize(Entry targetEntry, Entry destinationEntry, String optionalNewName, Boolean persistOriginal) {
        final Boolean persistOriginalStorageNode = persistOriginal != null ? persistOriginal : false;
        this.initialDestinationEntry = destinationEntry;
        this.initialTargetEntry = targetEntry;

        final TransferAction storageTaskAction = new TransferAction(null, targetEntry, destinationEntry, optionalNewName, persistOriginalStorageNode);

        storageTaskAction.setActionHeader("%s %s '%s' to '%s'".formatted(persistOriginalStorageNode == true ? "Copy" : "Move", this.initialTargetEntry.getIsDirectory() ? "directory" : "file", this.initialTargetEntry.getUrl(), this.initialDestinationEntry.getUrl()));
        storageTaskAction.setMessage("%s transfer task has been successfully initialized".formatted(this.initialTargetEntry.getIsDirectory() ? "Directory" : "File"));

        this.setCurrentAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return this;
    }

    public Transfer initialize(Entry targetEntry, String newName) {
        this.initialDestinationEntry = targetEntry.getParent();
        this.initialTargetEntry = targetEntry;

        final TransferAction storageTaskAction = new TransferAction(null, targetEntry, targetEntry.getParent(), newName, false);

        storageTaskAction.setActionHeader("Rename %s '%s' to '%s'".formatted(this.initialTargetEntry.getIsDirectory() ? "directory" : "file", this.initialTargetEntry.getName(), newName));
        storageTaskAction.setMessage("%s rename task has been successfully initialized".formatted(this.initialTargetEntry.getIsDirectory() ? "Directory" : "File"));

        this.setCurrentAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return this;
    }

    @Override
    public synchronized void execute(Map<String, Object> taskParameters) {
        this.advance(); /* Will advance task only if the current task is either done or waiting for the children tasks to be done */
        final TransferAction storageTaskCurrentAction = (TransferAction) this.getCurrentAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String updatedName = taskParameters != null ? (String) taskParameters.get("updatedName") : null; 
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskCurrentAction.getParentAction() == null && storageTaskCurrentAction.getNewEntry() != null && storageTaskCurrentAction.getNewEntry().getId() != null && (storageTaskCurrentAction.getPersistNode() == true || storageTaskCurrentAction.getTargetEntry().getIsAccessible() == false)) {
            storageTaskCurrentAction.setMessage("%s transfer task finished successfully!".formatted(this.initialTargetEntry.getIsDirectory() ? "Directory" : "File"));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (cancel != null && cancel == true) {
            storageTaskCurrentAction.setMessage("%s transfer task was cancelled...".formatted(this.initialTargetEntry.getIsDirectory() ? "Directory" : "File"));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            this.getExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetEntry(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        Boolean replaceExisting = false;
        storageTaskCurrentAction.incrementAttemptCounter();
        while (storageTaskCurrentAction.getNewEntry() == null
            || (storageTaskCurrentAction.getNewEntry() != null && storageTaskCurrentAction.getNewEntry().getId() == null)
            || (storageTaskCurrentAction.getPersistNode() == false && storageTaskCurrentAction.getChildActions().hasNext() != true && storageTaskCurrentAction.getTargetEntry().getIsAccessible() != false)) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getExceptionHandler().getOnExceptionAction(storageTaskCurrentAction.getTargetEntry(), exceptionType);

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "FileAlreadyExistsException":
                            if (storageTaskCurrentAction.getConflictEntry() == null) {
                                System.out.println("FileAlreadyExistsException (NCN): %s".formatted(storageTaskCurrentAction.getTargetEntry().getEntryPath().getPath().toString()));
                                return;
                            }
                            switch (exceptionAction) {
                                case "keep":
                                    storageTaskCurrentAction.setOptionalNewName(EntryName.findNextAvailableFileName(storageTaskCurrentAction.getOptionalNewName()));
                                    storageTaskCurrentAction.setConflictEntry(null);
                                    break;
                                case "merge":
                                    if (storageTaskCurrentAction.getConflictEntry() == null) {
                                        throw new FileAlreadyExistsException("Merge option is invalid: node to merge with is invalid!".formatted());
                                    }

                                    if (!storageTaskCurrentAction.getTargetEntry().getIsDirectory() || !storageTaskCurrentAction.getConflictEntry().getIsDirectory()) {
                                        throw new FileAlreadyExistsException("Merge option is invalid: both nodes must be directories!".formatted());
                                    }

                                    storageTaskCurrentAction.setNewEntry(storageTaskCurrentAction.getConflictEntry());
                                    storageTaskCurrentAction.setConflictEntry(null);
                                    break;
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing transfer attempt */
                                    storageTaskCurrentAction.setOptionalNewName(EntryName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictEntry(null);
                                    break;
                                case "replace":
                                    if (storageTaskCurrentAction.getConflictEntry() == null) {
                                        throw new FileAlreadyExistsException("Replace option is invalid: node to replace is invalid!".formatted());
                                    }

                                    if (storageTaskCurrentAction.getTargetEntry().getIsDirectory() || storageTaskCurrentAction.getConflictEntry().getIsDirectory()) {
                                        throw new FileAlreadyExistsException("Replace option is invalid: both nodes must be files!".formatted());
                                    }
                                    
                                    this.storageExecutor().deleteEntry(storageTaskCurrentAction.getConflictEntry());
                                    storageTaskCurrentAction.setConflictEntry(null);
                                    replaceExisting = true;
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copy" : "move", storageTaskCurrentAction.getDestinationEntry().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentAction();
                                    this.advance();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("keep", "Keep both".formatted(),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    if (storageTaskCurrentAction.getConflictEntry() != null && storageTaskCurrentAction.getTargetEntry().getIsDirectory() && storageTaskCurrentAction.getConflictEntry().getIsDirectory()) {
                                        onExceptionOptions.add(new Option("merge", "Merge directories".formatted(),
                                            new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                        ));
                                    }
                                    onExceptionOptions.add(new Option("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    if (storageTaskCurrentAction.getConflictEntry() != null && !storageTaskCurrentAction.getTargetEntry().getIsDirectory() && !storageTaskCurrentAction.getConflictEntry().getIsDirectory()) {
                                        onExceptionOptions.add(new Option("replace", "Replace file".formatted(),
                                            new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                        ));
                                    }
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a conflicting node already in place.".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationEntry().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameValidationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskCurrentAction.setOptionalNewName(EntryName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictEntry(null);
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getOptionalNewName(), storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "creation in" : "upload to", storageTaskCurrentAction.getTargetEntry().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentAction();
                                    this.advance();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to the provided %s name being invalid.".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationEntry().getUrl(), storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameGenerationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskCurrentAction.setOptionalNewName(EntryName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictEntry(null);
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getOptionalNewName(), storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "creation in" : "upload to", storageTaskCurrentAction.getDestinationEntry().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentAction();
                                    this.advance();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a failed name generation attempt.".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationEntry().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copy" : "move", storageTaskCurrentAction.getDestinationEntry().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentAction();
                                    this.advance();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a persistent I/O exception occurring".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationEntry().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copy" : "move", storageTaskCurrentAction.getDestinationEntry().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentAction();
                                    this.advance();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to an unexpected exception occurring".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationEntry().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }   
                    }
                }

                if (storageTaskCurrentAction.getConflictEntry() != null) {
                    /* TODO: I have no idea... */
                }

                if (storageTaskCurrentAction.getNewEntry() == null || storageTaskCurrentAction.getNewEntry().getEntryPath() == null) {
                    storageTaskCurrentAction.setNewEntry(this.storageExecutor().transferEntry(storageTaskCurrentAction.getTargetEntry(), storageTaskCurrentAction.getDestinationEntry(), storageTaskCurrentAction.getOptionalNewName(), storageTaskCurrentAction.getPersistNode(), replaceExisting));
                }

                if (storageTaskCurrentAction.getPersistNode() == true) {
                    if (storageTaskCurrentAction.getDestinationEntry().setuidBitIsSet()) {
                        if (!storageTaskCurrentAction.getNewEntry().getUser().equals(storageTaskCurrentAction.getDestinationEntry().getUser())) {
                            this.storageExecutor().modifyEntryOwnership(storageTaskCurrentAction.getNewEntry(), storageTaskCurrentAction.getDestinationEntry().getUser().toString());
                        }
                        
                        if (!storageTaskCurrentAction.getNewEntry().setuidBitIsSet() != true) {
                            this.storageExecutor().modifyEntryPermissions(storageTaskCurrentAction.getNewEntry(), "u+s");
                        }
                    }

                    if (storageTaskCurrentAction.getDestinationEntry().setgidBitIsSet()) {
                        if (!storageTaskCurrentAction.getNewEntry().getGroup().equals(storageTaskCurrentAction.getDestinationEntry().getGroup())) {
                            this.storageExecutor().modifyEntryOwnership(storageTaskCurrentAction.getNewEntry(), ":%s".formatted(storageTaskCurrentAction.getDestinationEntry().getGroup()));
                        }

                        if (!storageTaskCurrentAction.getNewEntry().setgidBitIsSet() != true) {
                            this.storageExecutor().modifyEntryPermissions(storageTaskCurrentAction.getNewEntry(), "g+s");
                        }
                    }
                }

                if (storageTaskCurrentAction.getNewEntry().getId() == null) {
                    if (storageTaskCurrentAction.getPersistNode() == false && storageTaskCurrentAction.getTargetEntry().getId() != null) {
                        storageTaskCurrentAction.getNewEntry().setId(storageTaskCurrentAction.getTargetEntry().getId());
                        storageTaskCurrentAction.getTargetEntry().setId(null);
                    }

                    storageTaskCurrentAction.setNewEntry(this.storageExecutor().publishEntry(storageTaskCurrentAction.getNewEntry()));
                }

                if (storageTaskCurrentAction.getPersistNode() == false && storageTaskCurrentAction.getChildActions().hasNext() != true && storageTaskCurrentAction.getTargetEntry().getIsAccessible() != false) {
                    this.storageExecutor().deleteEntry(storageTaskCurrentAction.getTargetEntry());

                    if (storageTaskCurrentAction.getChildActions().hasPrevious()) {
                        storageTaskCurrentAction.setMessage("%s '%s' has been cleaned up successfully!".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationEntry().getUrl()));
                        storageTaskCurrentAction.setExceptionMessage(null);
                        storageTaskCurrentAction.setExceptionType(null);
                        this.setCurrentState(TaskState.EXECUTING);
                        return;
                    }
                }

                storageTaskCurrentAction.setMessage("%s '%s' has been %s to directory '%s' successfully!".formatted(storageTaskCurrentAction.getTargetEntry().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetEntry().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationEntry().getUrl()));
                storageTaskCurrentAction.setExceptionMessage(null);
                storageTaskCurrentAction.setExceptionType(null);
                this.setCurrentState(TaskState.EXECUTING);
                return;
            } catch (FileAlreadyExistsException exception) {
                try { /* Here we attempt to find the conflicting node and recover it */
                    final String targetName = storageTaskCurrentAction.getOptionalNewName() != null ? storageTaskCurrentAction.getOptionalNewName() : storageTaskCurrentAction.getTargetEntry().getName();
                    storageTaskCurrentAction.setConflictEntry(this.storageExecutor().recoverEntry(targetName, storageTaskCurrentAction.getDestinationEntry()));
                } catch (Exception recoveryException) {
                    /* Wild territories... conflict without a conflicting node? Gonna be fun!!! */
                    storageTaskCurrentAction.setConflictEntry(null);
                    /* TODO: Simply log this stuff for later and move on... */
                }

                storageTaskCurrentAction.setExceptionType("FileAlreadyExistsException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                exception.printStackTrace();
            } catch (EntryNameValidationException exception) {
                storageTaskCurrentAction.setExceptionType("FileNameValidationException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                exception.printStackTrace();
            } catch (EntryNameGenerationException exception) {
                storageTaskCurrentAction.setExceptionType("FileNameGenerationException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                exception.printStackTrace();
            } catch (DirectoryNotEmptyException exception) {
                storageTaskCurrentAction.setExceptionType("DirectoryNotEmptyException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                exception.printStackTrace();
            } catch (IOException exception) {
                storageTaskCurrentAction.setExceptionType("IOException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                exception.printStackTrace();
            } catch (Exception exception) {
                storageTaskCurrentAction.setExceptionType("Exception");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                exception.printStackTrace();
            }
        }
    }

    @Override
    protected void skipCurrentAction() {
        TransferAction storageTaskCurrentAction = (TransferAction) this.getCurrentAction();

        if (storageTaskCurrentAction.getParentAction() != null) {
            this.setCurrentAction((TransferAction) storageTaskCurrentAction.getParentAction());
        }
    }

    @Override
    protected void advance() {
        TransferAction storageTaskCurrentAction = (TransferAction) this.getCurrentAction();  

        if (storageTaskCurrentAction.getNewEntry() == null) {
            return;
        }

        if (storageTaskCurrentAction.getNewEntry().getId() == null) {
            return;
        }

        while (!storageTaskCurrentAction.getChildActions().hasNext() && storageTaskCurrentAction.getParentAction() != null) {
            if (storageTaskCurrentAction.getPersistNode() == false && storageTaskCurrentAction.getTargetEntry().getIsAccessible() != false) {
                break; /* Clean-up is required after moving a node */
            }

            storageTaskCurrentAction = (TransferAction) storageTaskCurrentAction.getParentAction();
        }

        if (storageTaskCurrentAction.getChildActions().hasNext()) {
            storageTaskCurrentAction = (TransferAction) storageTaskCurrentAction.getChildActions().next();
            storageTaskCurrentAction.setDestinationEntry(((TransferAction) storageTaskCurrentAction.getParentAction()).getNewEntry());
        }

        this.setCurrentAction(storageTaskCurrentAction);
        this.getExceptionHandler().resetOnExceptionAction();
    }

    protected class TransferAction extends Action {
        private final Boolean persistNode;
        private String optionalNewName;

        private Entry newEntry;
        private Entry targetEntry;
        private Entry conflictEntry;
        private Entry destinationEntry;

        protected TransferAction(TransferAction parentAction, Entry targetEntry, Entry destinationEntry, String optionalNewName, Boolean persistOriginal) {
            super(parentAction);

            this.newEntry = null;
            this.targetEntry = targetEntry;
            this.persistNode = persistOriginal;
            this.optionalNewName = optionalNewName;
            this.destinationEntry = destinationEntry;

            if (this.targetEntry.getIsDirectory()) {
                for (Entry childNode : this.targetEntry.getChildren()) {
                    this.getChildActions().add(new TransferAction(this, childNode, null, null, persistOriginal));
                    this.getChildActions().previous(); /* Required because iterator pushes forward on .add() which is an expected but unwanted behavior */
                }
            }
        }

        protected Boolean getPersistNode() {
            return this.persistNode;
        }

        protected String getOptionalNewName() {
            return this.optionalNewName;
        }

        protected void setOptionalNewName(String optionalNewName) {
            this.optionalNewName = optionalNewName;
        }

        protected Entry getNewEntry() {
            return this.newEntry;
        }

        protected void setNewEntry(Entry newStorageNode) {
            this.newEntry = newStorageNode;
        }

        protected Entry getTargetEntry() {
            return this.targetEntry;
        }

        protected void setTargetEntry(Entry targetStorageNode) {
            this.targetEntry = targetStorageNode;
        }

        protected Entry getConflictEntry() {
            return this.conflictEntry;
        }

        protected void setConflictEntry(Entry conflictStorageNode) {
            this.conflictEntry = conflictStorageNode;
        }

        protected Entry getDestinationEntry() {
            return this.destinationEntry;
        }

        protected void setDestinationEntry(Entry destinationStorageNode) {
            this.destinationEntry = destinationStorageNode;
        }
    }
}
