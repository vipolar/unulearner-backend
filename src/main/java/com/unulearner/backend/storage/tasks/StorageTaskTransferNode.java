package com.unulearner.backend.storage.tasks;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.statics.StorageFileName;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption.Parameter;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import com.unulearner.backend.storage.exceptions.FileNameGenerationException;
import com.unulearner.backend.storage.exceptions.FileNameValidationException;

public class StorageTaskTransferNode extends StorageTaskBaseBatch {
    private final StorageNode rootDestinationStorageNode;
    private final StorageNode rootTargetStorageNode;
    private final Boolean persistOriginalStorageNode;

    public StorageTaskTransferNode(StorageTree storageTree, StorageNode targetStorageNode, StorageNode destinationStorageNode, Boolean persistOriginal, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        this.persistOriginalStorageNode = persistOriginal != null ? persistOriginal : false;
        this.rootDestinationStorageNode = destinationStorageNode;
        this.rootTargetStorageNode = targetStorageNode;

        final StorageTaskTransferNodeCurrentAction storageTaskAction = new StorageTaskTransferNodeCurrentAction(null, targetStorageNode, destinationStorageNode);

        storageTaskAction.setActionHeader("%s '%s' %s to '%s'".formatted(this.persistOriginalStorageNode ? "Copy" : "Move", this.rootTargetStorageNode.isDirectory() ? "directory" : "file", this.rootTargetStorageNode.getOnDiskURL(), this.rootDestinationStorageNode.getOnDiskURL()));
        storageTaskAction.setMessage("%s transfer task has been successfully initialized".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File"));

        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    @Override
    public synchronized void execute(Map<String, Object> taskParameters) {
        final StorageTaskTransferNodeCurrentAction storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) this.getStorageTaskAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String updatedName = taskParameters != null ? (String) taskParameters.get("updatedName") : null; 
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getNewStorageNode().getId() != null) {
            storageTaskCurrentAction.setMessage("%s '%s' %s to %s task finished successfully!".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getOnDiskURL(), this.persistOriginalStorageNode ? "copy" : "move", this.rootDestinationStorageNode.getOnDiskURL()));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (cancel != null && cancel == true) {
            storageTaskCurrentAction.setMessage("%s '%s' %s to %s task was cancelled...".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getOnDiskURL(), this.persistOriginalStorageNode ? "copy" : "move", this.rootDestinationStorageNode.getOnDiskURL()));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        Integer IOAttempts = 0;
        Boolean replaceExisting = false;
        storageTaskCurrentAction.incrementAttemptCounter();
        while (storageTaskCurrentAction.getNewStorageNode().getId() == null) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(exceptionType, storageTaskCurrentAction.getTargetStorageNode());

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "FileAlreadyExistsException":
                            if (storageTaskCurrentAction.getConflictStorageNode() == null) {
                                throw new RuntimeException("%s '%s' could not be %s to directory '%s' due to a supposed conflicting node, existence of which can neither be confirmed nor denied...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL(), this.persistOriginalStorageNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskURL()));
                            }

                            switch (exceptionAction) {
                                case "keep":
                                    storageTaskCurrentAction.getNewStorageNode().setOnDiskName(StorageFileName.findNextAvailableFileName(storageTaskCurrentAction.getNewStorageNode().getOnDiskName()));
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "merge":
                                    if (!storageTaskCurrentAction.getTargetStorageNode().isDirectory() || !storageTaskCurrentAction.getConflictStorageNode().isDirectory()) {
                                        throw new FileAlreadyExistsException("Merge option is invalid: both nodes must be directories!");
                                    }

                                    /* here we leave the conflicting node intact to indicate that this is a merger while adopting its on disk path to skip the transfer process */
                                    storageTaskCurrentAction.getNewStorageNode().setNodePath(storageTaskCurrentAction.getConflictStorageNode().getNodePath());
                                    break;
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing transfer attempt */
                                    storageTaskCurrentAction.getNewStorageNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "replace":
                                    if (storageTaskCurrentAction.getTargetStorageNode().isDirectory() || storageTaskCurrentAction.getConflictStorageNode().isDirectory()) {
                                        throw new FileAlreadyExistsException("Replace option is invalid: both nodes must be files!");
                                    }
                                    
                                    this.storageTreeExecute().deleteStorageNode(storageTaskCurrentAction.getConflictStorageNode());
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    replaceExisting = true;
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL(), this.persistOriginalStorageNode ? "copy" : "move", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentNodesChildren();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("keep", "Keep both".formatted(),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    if (storageTaskCurrentAction.getNewStorageNode().isDirectory() && storageTaskCurrentAction.getConflictStorageNode().isDirectory()) {
                                        onExceptionOptions.add(new OnExceptionOption("merge", "Merge directories".formatted(),
                                            new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                        ));
                                    }
                                    onExceptionOptions.add(new OnExceptionOption("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    if (!storageTaskCurrentAction.getNewStorageNode().isDirectory() && !storageTaskCurrentAction.getConflictStorageNode().isDirectory()) {
                                        onExceptionOptions.add(new OnExceptionOption("replace", "Replace file".formatted(),
                                            new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                        ));
                                    }
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a conflicting node already in place.".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL(), this.persistOriginalStorageNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameValidationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskCurrentAction.getNewStorageNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getNewStorageNode().getOnDiskName(), storageTaskCurrentAction.getNewStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskCurrentAction.getNewStorageNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>(Arrays.asList(
                                        new OnExceptionOption("rename", "Rename manually".formatted(),
                                            new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                            new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                        ),
                                        new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                            new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                        )
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a provided file name being incompatible.".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL(), this.persistOriginalStorageNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameGenerationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskCurrentAction.getNewStorageNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getNewStorageNode().getOnDiskName(), storageTaskCurrentAction.getNewStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskCurrentAction.getNewStorageNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>(Arrays.asList(
                                        new OnExceptionOption("rename", "Rename manually".formatted(),
                                            new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                            new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                        ),
                                        new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                            new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                        )
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a failed name generation attempt.".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL(), this.persistOriginalStorageNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL(), this.persistOriginalStorageNode ? "copy" : "move", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentNodesChildren();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>(Arrays.asList(
                                        new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                            new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                        )
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a persistent I/O exception occurring".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL(), this.persistOriginalStorageNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL(), this.persistOriginalStorageNode ? "copy" : "move", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentNodesChildren();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>(Arrays.asList(
                                        new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                            new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                        )
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to an unexpected exception occurring".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL(), this.persistOriginalStorageNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }   
                    }
                }

                if (storageTaskCurrentAction.getNewStorageNode().getNodePath() == null) {
                    if (storageTaskCurrentAction.getTargetStorageNode().isDirectory()) {
                        storageTaskCurrentAction.setNewStorageNode(this.storageTreeExecute().transferStorageNode(storageTaskCurrentAction.getNewStorageNode(), storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getDestinationStorageNode(), this.persistOriginalStorageNode, replaceExisting));
                    } else {
                        storageTaskCurrentAction.setNewStorageNode(this.storageTreeExecute().transferStorageNode(storageTaskCurrentAction.getNewStorageNode(), storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getDestinationStorageNode(), this.persistOriginalStorageNode, replaceExisting));
                    }
                }

                /* TODO: handle this better */
                if (storageTaskCurrentAction.getNewStorageNode().getId() == null) {
                    if (storageTaskCurrentAction.getConflictStorageNode() == null) {
                        if (this.persistOriginalStorageNode == true) { /* copy */
                            this.storageTreeExecute().publishStorageNode(storageTaskCurrentAction.getNewStorageNode());
                        } else { /* move */
                            storageTaskCurrentAction.getTargetStorageNode().setNodePath(storageTaskCurrentAction.getNewStorageNode().getNodePath());
                            storageTaskCurrentAction.setNewStorageNode(this.storageTreeExecute().publishStorageNode(storageTaskCurrentAction.getTargetStorageNode()));
                        }
                    } else { /* merge */
                        storageTaskCurrentAction.setNewStorageNode(storageTaskCurrentAction.getConflictStorageNode());
                        storageTaskCurrentAction.setConflictStorageNode(null);
                    }
                }

                /* TODO: handle this better */
                if (!this.persistOriginalStorageNode == false) {
                    if (!Files.isDirectory(storageTaskCurrentAction.getTargetStorageNode().getNodePath().getPath())) {
                        Files.deleteIfExists(storageTaskCurrentAction.getTargetStorageNode().getNodePath().getPath());
                    }

                    if (storageTaskCurrentAction.getTargetStorageNode().getIsAccessible()) {
                        this.storageTreeExecute().deleteStorageNode(storageTaskCurrentAction.getTargetStorageNode());
                    }
                }
                
                storageTaskCurrentAction.setMessage("%s '%s' has been %s to directory '%s' successfully!".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskURL(), this.persistOriginalStorageNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskURL()));
                this.setCurrentState(TaskState.EXECUTING);
                this.advanceStorageTask();
                return;
            } catch (FileAlreadyExistsException exception) {
                try { /* Here we attempt to find the conflicting node and recover it */
                    storageTaskCurrentAction.setConflictStorageNode(this.storageTreeExecute().recoverStorageNode(storageTaskCurrentAction.getNewStorageNode().getOnDiskName(), storageTaskCurrentAction.getDestinationStorageNode()));
                } catch (Exception recoveryException) {
                    /* Wild territories... conflict without a conflicting node? Gonna be fun!!! */
                    storageTaskCurrentAction.setExceptionType(recoveryException.getClass().getSimpleName());
                    storageTaskCurrentAction.setExceptionMessage(recoveryException.getMessage());
                    storageTaskCurrentAction.setConflictStorageNode(null);
                    continue;
                }

                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            } catch (FileNameValidationException exception) {
                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            } catch (FileNameGenerationException exception) {
                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            } catch (IOException exception) {
                if (IOAttempts++ < 3) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException sleepException) {
                        storageTaskCurrentAction.setExceptionType(sleepException.getClass().getSimpleName());
                        storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                        continue;
                    }
                }

                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            } catch (Exception exception) {
                storageTaskCurrentAction.setExceptionType("RuntimeException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            }
        }
    }

    private void skipCurrentNodesChildren() {
        StorageTaskTransferNodeCurrentAction storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) this.getStorageTaskAction();

        if (storageTaskCurrentAction.getParentStorageTaskAction() != null) {
            this.setStorageTaskAction(storageTaskCurrentAction.getParentStorageTaskAction());
        }
    }

    @Override
    protected void advanceStorageTask() {
        StorageTaskTransferNodeCurrentAction storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) this.getStorageTaskAction();

        while (!storageTaskCurrentAction.getChildStorageTaskActions().hasNext()) {
            if (this.persistOriginalStorageNode == false) {
                try {
                    Files.deleteIfExists(storageTaskCurrentAction.getTargetStorageNode().getNodePath().getPath());
                } catch (IOException exception) {
                    //TODO: log...
                    exception.printStackTrace();
                }
            }

            if (storageTaskCurrentAction.getParentStorageTaskAction() == null) {
                break;
            }

            storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction();
        }

        if (storageTaskCurrentAction.getChildStorageTaskActions().hasNext()) {
            storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) storageTaskCurrentAction.getChildStorageTaskActions().next();
        }

        //TODO: look into completion status!
        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getNewStorageNode().getId() != null) {
            this.setCurrentState(TaskState.COMPLETED);
        }

        this.setStorageTaskAction(storageTaskCurrentAction);
        this.getTaskExceptionHandler().resetOnExceptionAction();
    }

    protected class StorageTaskTransferNodeCurrentAction extends StorageTaskCurrentAction {
        private StorageNode newStorageNode;
        private StorageNode targetStorageNode;
        private StorageNode conflictStorageNode;
        private StorageNode destinationStorageNode;

        protected StorageTaskTransferNodeCurrentAction(StorageTaskTransferNodeCurrentAction parentStorageTaskAction, StorageNode targetStorageNode, StorageNode destinationStorageNode) {
            super(parentStorageTaskAction);

            this.targetStorageNode = targetStorageNode;
            this.destinationStorageNode = destinationStorageNode;
            this.newStorageNode = new StorageNode(null, targetStorageNode.isDirectory() ? new ArrayList<>() : null, null, targetStorageNode.getDescription());
            this.newStorageNode.setOnDiskName(targetStorageNode.getOnDiskName());

            if (this.targetStorageNode.isDirectory()) {
                for (StorageNode childNode : this.targetStorageNode.getChildren()) {
                    this.getChildStorageTaskActions().add(new StorageTaskTransferNodeCurrentAction(this, childNode, null));
                }
            }
        }

        public StorageNode getNewStorageNode() {
            return this.newStorageNode;
        }

        protected void setNewStorageNode(StorageNode newStorageNode) {
            this.newStorageNode = newStorageNode;
        }

        public StorageNode getTargetStorageNode() {
            return this.targetStorageNode;
        }
        
        protected void setTargetStorageNode(StorageNode targetStorageNode) {
            this.targetStorageNode = targetStorageNode;
        }
    
        public StorageNode getConflictStorageNode() {
            return this.conflictStorageNode;
        }

        protected void setConflictStorageNode(StorageNode conflictStorageNode) {
            this.conflictStorageNode = conflictStorageNode;
        }

        public StorageNode getDestinationStorageNode() {
            return this.destinationStorageNode;
        }

        protected void setDestinationStorageNode(StorageNode destinationStorageNode) {
            this.destinationStorageNode = destinationStorageNode;
        }
    }
}
