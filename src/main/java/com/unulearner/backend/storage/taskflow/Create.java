package com.unulearner.backend.storage.taskflow;

import java.io.File;

import java.util.ArrayList;
import java.util.Map;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.unulearner.backend.storage.model.Entry;
import com.unulearner.backend.storage.formalization.EntryName;
import com.unulearner.backend.storage.taskflow.constant.TaskState;
import com.unulearner.backend.storage.taskflow.exception.Option;
import com.unulearner.backend.storage.taskflow.exception.Option.Parameter;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import com.unulearner.backend.storage.exceptions.entry.EntryNameGenerationException;
import com.unulearner.backend.storage.exceptions.entry.EntryNameValidationException;

@Component
@Scope("prototype")
public class Create extends Base {
    public Create initialize(Entry destinationStorageNode, String newFileName, File newFile) {
        final CreateAction storageTaskAction = new CreateAction(destinationStorageNode, newFileName, newFile);

        storageTaskAction.setActionHeader("Upload '%s' to '%s' directory".formatted(newFileName, destinationStorageNode.getUrl()));
        storageTaskAction.setMessage("File upload task has been successfully initialized".formatted());

        this.setCurrentAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return this;
    }

    public Create initialize(Entry destinationStorageNode, String newDirectoryName) {        
        final CreateAction storageTaskAction = new CreateAction(destinationStorageNode, newDirectoryName);

        storageTaskAction.setActionHeader("Create '%s' directory in '%s' directory".formatted(newDirectoryName, destinationStorageNode.getUrl()));
        storageTaskAction.setMessage("Directory creation task has been successfully initialized".formatted());
    
        this.setCurrentAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return this;
    }

    @Override
    public synchronized void execute(Map<String, Object> taskParameters) {
        final CreateAction storageTaskAction = (CreateAction) this.getCurrentAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String updatedName = taskParameters != null ? (String) taskParameters.get("updatedName") : null; 
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskAction.getNewStorageNode().getId() != null) {
            storageTaskAction.setMessage("%s '%s' directory task finished successfully!".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "Directory creation in" : "File upload to", storageTaskAction.getNewStorageNode().getParent().getUrl()));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (cancel != null && cancel == true) {
            storageTaskAction.setMessage("%s '%s' directory task was cancelled...".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "Directory creation in" : "File upload to", storageTaskAction.getNewStorageNode().getParent().getUrl()));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            this.getExceptionHandler().setOnExceptionAction(storageTaskAction.getNewStorageNode(), storageTaskAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        storageTaskAction.incrementAttemptCounter();
        while (storageTaskAction.getNewStorageNode() == null || storageTaskAction.getNewStorageNode().getId() == null) {
            try {
                final String exceptionType = storageTaskAction.getExceptionType();
                final String exceptionAction = this.getExceptionHandler().getOnExceptionAction(storageTaskAction.getNewStorageNode(), exceptionType);
                
                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "FileAlreadyExistsException":
                            switch (exceptionAction) {
                                case "keep":
                                    storageTaskAction.getNewStorageNode().setName(EntryName.findNextAvailableFileName(storageTaskAction.getNewStorageNode().getName()));
                                    storageTaskAction.setConflictStorageNode(null);
                                    break;
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskAction.getNewStorageNode().setName(EntryName.validateFileName(updatedName));
                                    storageTaskAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().getIsDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("keep", "Keep both".formatted(),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new Option("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to a conflicting node already in place.".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().getIsDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameValidationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskAction.getNewStorageNode().setName(EntryName.validateFileName(updatedName));
                                    storageTaskAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().getIsDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to the provided %s name being invalid.".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().getIsDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getUrl(), storageTaskAction.getNewStorageNode().getIsDirectory() ? "directory" : "file"));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameGenerationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskAction.getNewStorageNode().setName(EntryName.validateFileName(updatedName));
                                    storageTaskAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().getIsDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to a failed name generation attempt.".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().getIsDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().getIsDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to a persistent I/O exception occurring.".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().getIsDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().getIsDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
        
                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to an unexpected exception occurring.".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().getIsDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getUrl()));
                                    this.getExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                    }
                }                

                if (storageTaskAction.getNewStorageNode() == null || storageTaskAction.getNewStorageNode().getEntryPath() == null) {
                    storageTaskAction.setNewStorageNode(this.storageExecutor().createEntry(storageTaskAction.getDestinationStorageNode(), storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewFile()));
                }

                if (storageTaskAction.getNewStorageNode().getParent().setuidBitIsSet()) {
                    if (!storageTaskAction.getNewStorageNode().getUser().equals(storageTaskAction.getNewStorageNode().getParent().getUser())) {
                        this.storageExecutor().modifyEntryOwnership(storageTaskAction.getNewStorageNode(), storageTaskAction.getNewStorageNode().getParent().getUser().toString());
                    }
                    
                    if (!storageTaskAction.getNewStorageNode().setuidBitIsSet() != true) {
                        this.storageExecutor().modifyEntryPermissions(storageTaskAction.getNewStorageNode(), "u+s");
                    }
                }

                if (storageTaskAction.getNewStorageNode().getParent().setgidBitIsSet()) {
                    if (!storageTaskAction.getNewStorageNode().getGroup().equals(storageTaskAction.getNewStorageNode().getParent().getGroup())) {
                        this.storageExecutor().modifyEntryOwnership(storageTaskAction.getNewStorageNode(), ":%s".formatted(storageTaskAction.getNewStorageNode().getParent().getGroup().toString()));
                    }

                    if (!storageTaskAction.getNewStorageNode().setgidBitIsSet() != true) {
                        this.storageExecutor().modifyEntryPermissions(storageTaskAction.getNewStorageNode(), "g+s");
                    }
                }

                if (storageTaskAction.getNewStorageNode().getId() == null) {
                    this.storageExecutor().publishEntry(storageTaskAction.getNewStorageNode());
                }

                storageTaskAction.setMessage("%s '%s' has been %s directory '%s' successfully!".formatted(storageTaskAction.getNewStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getUrl(), storageTaskAction.getNewStorageNode().getIsDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getUrl()));
                storageTaskAction.setExceptionMessage(null);
                storageTaskAction.setExceptionType(null);
                this.setCurrentState(TaskState.EXECUTING);
                return;
            } catch (FileAlreadyExistsException exception) {
                try { /* Here we attempt to find the conflicting node and recover it */
                    storageTaskAction.setConflictStorageNode(this.storageExecutor().recoverEntry(storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().getParent()));
                } catch (Exception recoveryException) {
                    /* Wild territories... conflict without a conflicting node? Gonna be fun!!! */
                    storageTaskAction.setConflictStorageNode(null);
                    /* TODO: Simply log this stuff for later and move on... */
                }

                storageTaskAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (EntryNameValidationException exception) {
                storageTaskAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (EntryNameGenerationException exception) {
                storageTaskAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (IOException exception) {
                storageTaskAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (Exception exception) {
                storageTaskAction.setExceptionType("RuntimeException");
                storageTaskAction.setExceptionMessage(exception.getMessage());
            }
        }
    }

    protected class CreateAction extends Action {
        private final File newFile;

        private Entry newStorageNode;
        private Entry conflictStorageNode;
        private Entry destinationStorageNode;

        protected CreateAction(Entry destinationStorageNode, String newFileName, File newFile) {
            super(null);

            this.newStorageNode = new Entry().setName(newFileName).setChildren(null);
            this.destinationStorageNode = destinationStorageNode;
            this.newFile = newFile;
        }

        protected CreateAction(Entry destinationStorageNode, String newDirectoryName) {
            super(null);

            this.newStorageNode = new Entry().setName(newDirectoryName).setChildren(new ArrayList<>());
            this.destinationStorageNode = destinationStorageNode;
            this.newFile = null;
        }

        protected File getNewFile() {
            return this.newFile;
        }

        public Entry getNewStorageNode() {
            return this.newStorageNode;
        }

        protected void setNewStorageNode(Entry newStorageNode) {
            this.newStorageNode = newStorageNode;
        }

        public Entry getConflictStorageNode() {
            return this.conflictStorageNode;
        }

        protected void setConflictStorageNode(Entry conflictStorageNode) {
            this.conflictStorageNode = conflictStorageNode;
        }

        public Entry getDestinationStorageNode() {
            return this.destinationStorageNode;
        }

        public void setDestinationStorageNode(Entry destinationStorageNode) {
            this.destinationStorageNode = destinationStorageNode;
        }
    }
}
