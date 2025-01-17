package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.util.Map;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.statics.StorageFileName;
import com.unulearner.backend.storage.tasks.exception.Option;
import com.unulearner.backend.storage.tasks.exception.Option.Parameter;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.DirectoryNotEmptyException;
import com.unulearner.backend.storage.exceptions.NodeNameGenerationException;
import com.unulearner.backend.storage.exceptions.NodeNameValidationException;

@Component
@Scope("prototype")
public class StorageTaskTransferNode extends StorageTaskBaseBatch {
    private StorageNode rootDestinationStorageNode;
    private StorageNode rootTargetStorageNode;

    public StorageTaskTransferNode initialize(StorageNode targetStorageNode, StorageNode destinationStorageNode, String newName, Boolean persistOriginal) {
        final Boolean persistOriginalStorageNode = persistOriginal != null ? persistOriginal : false;
        this.rootDestinationStorageNode = destinationStorageNode;
        this.rootTargetStorageNode = targetStorageNode;

        final StorageTaskTransferNodeCurrentAction storageTaskAction = new StorageTaskTransferNodeCurrentAction(null, targetStorageNode, destinationStorageNode, newName, persistOriginalStorageNode);

        storageTaskAction.setActionHeader("%s %s '%s' to '%s'".formatted(persistOriginalStorageNode == true ? "Copy" : "Move", this.rootTargetStorageNode.getIsDirectory() ? "directory" : "file", this.rootTargetStorageNode.getUrl(), this.rootDestinationStorageNode.getUrl()));
        storageTaskAction.setMessage("%s transfer task has been successfully initialized".formatted(this.rootTargetStorageNode.getIsDirectory() ? "Directory" : "File"));

        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return this;
    }

    public StorageTaskTransferNode initialize(StorageNode targetStorageNode, String newName) {
        this.rootDestinationStorageNode = targetStorageNode.getParent();
        this.rootTargetStorageNode = targetStorageNode;

        final StorageTaskTransferNodeCurrentAction storageTaskAction = new StorageTaskTransferNodeCurrentAction(null, targetStorageNode, targetStorageNode.getParent(), newName, false);

        storageTaskAction.setActionHeader("Rename %s '%s' to '%s'".formatted(this.rootTargetStorageNode.getIsDirectory() ? "directory" : "file", this.rootTargetStorageNode.getName(), newName));
        storageTaskAction.setMessage("%s rename task has been successfully initialized".formatted(this.rootTargetStorageNode.getIsDirectory() ? "Directory" : "File"));

        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return this;
    }

    @Override
    public synchronized void execute(Map<String, Object> taskParameters) {
        this.advanceStorageTask(); /* Will advance task only if the current task is either done or waiting for the children tasks to be done */
        final StorageTaskTransferNodeCurrentAction storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) this.getStorageTaskAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String updatedName = taskParameters != null ? (String) taskParameters.get("updatedName") : null; 
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getNewStorageNode() != null && storageTaskCurrentAction.getNewStorageNode().getId() != null && (storageTaskCurrentAction.getPersistNode() == true || storageTaskCurrentAction.getTargetStorageNode().getIsAccessible() == false)) {
            storageTaskCurrentAction.setMessage("%s transfer task finished successfully!".formatted(this.rootTargetStorageNode.getIsDirectory() ? "Directory" : "File"));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (cancel != null && cancel == true) {
            storageTaskCurrentAction.setMessage("%s transfer task was cancelled...".formatted(this.rootTargetStorageNode.getIsDirectory() ? "Directory" : "File"));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        Boolean replaceExisting = false;
        storageTaskCurrentAction.incrementAttemptCounter();
        while (storageTaskCurrentAction.getNewStorageNode() == null
            || (storageTaskCurrentAction.getNewStorageNode() != null && storageTaskCurrentAction.getNewStorageNode().getId() == null)
            || (storageTaskCurrentAction.getPersistNode() == false && storageTaskCurrentAction.getChildStorageTaskActions().hasNext() != true && storageTaskCurrentAction.getTargetStorageNode().getIsAccessible() != false)) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(storageTaskCurrentAction.getTargetStorageNode(), exceptionType);

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "FileAlreadyExistsException":
                            if (storageTaskCurrentAction.getConflictStorageNode() == null) {
                                System.out.println("FileAlreadyExistsException (NCN): %s".formatted(storageTaskCurrentAction.getTargetStorageNode().getNodePath().getPath().toString()));
                                return;
                            }
                            switch (exceptionAction) {
                                case "keep":
                                    storageTaskCurrentAction.setOptionalNewName(StorageFileName.findNextAvailableFileName(storageTaskCurrentAction.getOptionalNewName()));
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "merge":
                                    if (storageTaskCurrentAction.getConflictStorageNode() == null) {
                                        throw new FileAlreadyExistsException("Merge option is invalid: node to merge with is invalid!".formatted());
                                    }

                                    if (!storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() || !storageTaskCurrentAction.getConflictStorageNode().getIsDirectory()) {
                                        throw new FileAlreadyExistsException("Merge option is invalid: both nodes must be directories!".formatted());
                                    }

                                    storageTaskCurrentAction.setNewStorageNode(storageTaskCurrentAction.getConflictStorageNode());
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing transfer attempt */
                                    storageTaskCurrentAction.setOptionalNewName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "replace":
                                    if (storageTaskCurrentAction.getConflictStorageNode() == null) {
                                        throw new FileAlreadyExistsException("Replace option is invalid: node to replace is invalid!".formatted());
                                    }

                                    if (storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() || storageTaskCurrentAction.getConflictStorageNode().getIsDirectory()) {
                                        throw new FileAlreadyExistsException("Replace option is invalid: both nodes must be files!".formatted());
                                    }
                                    
                                    this.storageTreeExecute().deleteStorageNode(storageTaskCurrentAction.getConflictStorageNode());
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    replaceExisting = true;
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copy" : "move", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("keep", "Keep both".formatted(),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    if (storageTaskCurrentAction.getConflictStorageNode() != null && storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() && storageTaskCurrentAction.getConflictStorageNode().getIsDirectory()) {
                                        onExceptionOptions.add(new Option("merge", "Merge directories".formatted(),
                                            new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                        ));
                                    }
                                    onExceptionOptions.add(new Option("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    if (storageTaskCurrentAction.getConflictStorageNode() != null && !storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() && !storageTaskCurrentAction.getConflictStorageNode().getIsDirectory()) {
                                        onExceptionOptions.add(new Option("replace", "Replace file".formatted(),
                                            new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                        ));
                                    }
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a conflicting node already in place.".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameValidationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskCurrentAction.setOptionalNewName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getOptionalNewName(), storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "creation in" : "upload to", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to the provided %s name being invalid.".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getUrl(), storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameGenerationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskCurrentAction.setOptionalNewName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getOptionalNewName(), storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "creation in" : "upload to", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a failed name generation attempt.".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copy" : "move", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a persistent I/O exception occurring".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copy" : "move", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<Option> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new Option("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to an unexpected exception occurring".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }   
                    }
                }

                if (storageTaskCurrentAction.getConflictStorageNode() != null) {
                    /* TODO: I have no idea... */
                }

                if (storageTaskCurrentAction.getNewStorageNode() == null || storageTaskCurrentAction.getNewStorageNode().getNodePath() == null) {
                    storageTaskCurrentAction.setNewStorageNode(this.storageTreeExecute().transferStorageNode(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getDestinationStorageNode(), storageTaskCurrentAction.getOptionalNewName(), storageTaskCurrentAction.getPersistNode(), replaceExisting));
                }

                if (storageTaskCurrentAction.getPersistNode() == true) {
                    if (storageTaskCurrentAction.getDestinationStorageNode().setuidBitIsSet()) {
                        if (!storageTaskCurrentAction.getNewStorageNode().getUser().equals(storageTaskCurrentAction.getDestinationStorageNode().getUser())) {
                            this.storageTreeExecute().chownStorageNode(storageTaskCurrentAction.getNewStorageNode(), storageTaskCurrentAction.getDestinationStorageNode().getUser().toString());
                        }
                        
                        if (!storageTaskCurrentAction.getNewStorageNode().setuidBitIsSet() != true) {
                            this.storageTreeExecute().chmodStorageNode(storageTaskCurrentAction.getNewStorageNode(), "u+s");
                        }
                    }

                    if (storageTaskCurrentAction.getDestinationStorageNode().setgidBitIsSet()) {
                        if (!storageTaskCurrentAction.getNewStorageNode().getGroup().equals(storageTaskCurrentAction.getDestinationStorageNode().getGroup())) {
                            this.storageTreeExecute().chownStorageNode(storageTaskCurrentAction.getNewStorageNode(), ":%s".formatted(storageTaskCurrentAction.getDestinationStorageNode().getGroup()));
                        }

                        if (!storageTaskCurrentAction.getNewStorageNode().setgidBitIsSet() != true) {
                            this.storageTreeExecute().chmodStorageNode(storageTaskCurrentAction.getNewStorageNode(), "g+s");
                        }
                    }
                }

                if (storageTaskCurrentAction.getNewStorageNode().getId() == null) {
                    if (storageTaskCurrentAction.getPersistNode() == false && storageTaskCurrentAction.getTargetStorageNode().getId() != null) {
                        storageTaskCurrentAction.getNewStorageNode().setId(storageTaskCurrentAction.getTargetStorageNode().getId());
                        storageTaskCurrentAction.getTargetStorageNode().setId(null);
                    }

                    storageTaskCurrentAction.setNewStorageNode(this.storageTreeExecute().publishStorageNode(storageTaskCurrentAction.getNewStorageNode()));
                }

                if (storageTaskCurrentAction.getPersistNode() == false && storageTaskCurrentAction.getChildStorageTaskActions().hasNext() != true && storageTaskCurrentAction.getTargetStorageNode().getIsAccessible() != false) {
                    this.storageTreeExecute().deleteStorageNode(storageTaskCurrentAction.getTargetStorageNode());

                    if (storageTaskCurrentAction.getChildStorageTaskActions().hasPrevious()) {
                        storageTaskCurrentAction.setMessage("%s '%s' has been cleaned up successfully!".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                        storageTaskCurrentAction.setExceptionMessage(null);
                        storageTaskCurrentAction.setExceptionType(null);
                        this.setCurrentState(TaskState.EXECUTING);
                        return;
                    }
                }

                storageTaskCurrentAction.setMessage("%s '%s' has been %s to directory '%s' successfully!".formatted(storageTaskCurrentAction.getTargetStorageNode().getIsDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                storageTaskCurrentAction.setExceptionMessage(null);
                storageTaskCurrentAction.setExceptionType(null);
                this.setCurrentState(TaskState.EXECUTING);
                return;
            } catch (FileAlreadyExistsException exception) {
                try { /* Here we attempt to find the conflicting node and recover it */
                    final String targetName = storageTaskCurrentAction.getOptionalNewName() != null ? storageTaskCurrentAction.getOptionalNewName() : storageTaskCurrentAction.getTargetStorageNode().getName();
                    storageTaskCurrentAction.setConflictStorageNode(this.storageTreeExecute().recoverStorageNode(targetName, storageTaskCurrentAction.getDestinationStorageNode()));
                } catch (Exception recoveryException) {
                    /* Wild territories... conflict without a conflicting node? Gonna be fun!!! */
                    storageTaskCurrentAction.setConflictStorageNode(null);
                    /* TODO: Simply log this stuff for later and move on... */
                }

                storageTaskCurrentAction.setExceptionType("FileAlreadyExistsException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                exception.printStackTrace();
            } catch (NodeNameValidationException exception) {
                storageTaskCurrentAction.setExceptionType("FileNameValidationException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                exception.printStackTrace();
            } catch (NodeNameGenerationException exception) {
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
    protected void skipStorageTaskCurrentAction() {
        StorageTaskTransferNodeCurrentAction storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) this.getStorageTaskAction();

        if (storageTaskCurrentAction.getParentStorageTaskAction() != null) {
            this.setStorageTaskAction((StorageTaskTransferNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction());
        }
    }

    @Override
    protected void advanceStorageTask() {
        StorageTaskTransferNodeCurrentAction storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) this.getStorageTaskAction();  

        if (storageTaskCurrentAction.getNewStorageNode() == null) {
            return;
        }

        if (storageTaskCurrentAction.getNewStorageNode().getId() == null) {
            return;
        }

        while (!storageTaskCurrentAction.getChildStorageTaskActions().hasNext() && storageTaskCurrentAction.getParentStorageTaskAction() != null) {
            if (storageTaskCurrentAction.getPersistNode() == false && storageTaskCurrentAction.getTargetStorageNode().getIsAccessible() != false) {
                break; /* Clean-up is required after moving a node */
            }

            storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction();
        }

        if (storageTaskCurrentAction.getChildStorageTaskActions().hasNext()) {
            storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) storageTaskCurrentAction.getChildStorageTaskActions().next();
            storageTaskCurrentAction.setDestinationStorageNode(((StorageTaskTransferNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction()).getNewStorageNode());
        }

        this.setStorageTaskAction(storageTaskCurrentAction);
        this.getTaskExceptionHandler().resetOnExceptionAction();
    }

    protected class StorageTaskTransferNodeCurrentAction extends StorageTaskCurrentAction {
        private String optionalNewName;
        private final Boolean persistNode;
        private StorageNode newStorageNode;
        private StorageNode targetStorageNode;
        private StorageNode conflictStorageNode;
        private StorageNode destinationStorageNode;

        protected StorageTaskTransferNodeCurrentAction(StorageTaskTransferNodeCurrentAction parentStorageTaskAction, StorageNode targetStorageNode, StorageNode destinationStorageNode, String optionalNewName, Boolean persistOriginal) {
            super(parentStorageTaskAction);

            this.newStorageNode = null;
            this.persistNode = persistOriginal;
            this.optionalNewName = optionalNewName;
            this.targetStorageNode = targetStorageNode;
            this.destinationStorageNode = destinationStorageNode;

            if (this.targetStorageNode.getIsDirectory()) {
                for (StorageNode childNode : this.targetStorageNode.getChildren()) {
                    this.getChildStorageTaskActions().add(new StorageTaskTransferNodeCurrentAction(this, childNode, null, null, persistOriginal));
                    this.getChildStorageTaskActions().previous(); /* Required because iterator pushes forward on .add() which is an expected but unwanted behavior */
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

        protected StorageNode getNewStorageNode() {
            return this.newStorageNode;
        }

        protected void setNewStorageNode(StorageNode newStorageNode) {
            this.newStorageNode = newStorageNode;
        }

        protected StorageNode getTargetStorageNode() {
            return this.targetStorageNode;
        }

        protected void setTargetStorageNode(StorageNode targetStorageNode) {
            this.targetStorageNode = targetStorageNode;
        }

        protected StorageNode getConflictStorageNode() {
            return this.conflictStorageNode;
        }

        protected void setConflictStorageNode(StorageNode conflictStorageNode) {
            this.conflictStorageNode = conflictStorageNode;
        }

        protected StorageNode getDestinationStorageNode() {
            return this.destinationStorageNode;
        }

        protected void setDestinationStorageNode(StorageNode destinationStorageNode) {
            this.destinationStorageNode = destinationStorageNode;
        }
    }
}
