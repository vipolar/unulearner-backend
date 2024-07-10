package com.unulearner.backend.storage.tasks;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;

import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.statics.StorageFileName;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import com.unulearner.backend.storage.exceptions.FileNameGenerationException;
import com.unulearner.backend.storage.exceptions.FileNameValidationException;

public class StorageTaskTransferNode extends StorageTaskBaseBatch {
    private final StorageNode rootDestinationStorageTreeNode;
    private final StorageNode rootTargetStorageTreeNode;
    private final Boolean persistOriginalStorageTreeNode;

    public StorageTaskTransferNode(StorageTree storageTree, StorageNode targetStorageTreeNode, StorageNode destinationStorageTreeNode, Boolean persistOriginal, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        this.persistOriginalStorageTreeNode = persistOriginal != null ? persistOriginal : false;
        this.rootDestinationStorageTreeNode = destinationStorageTreeNode;
        this.rootTargetStorageTreeNode = targetStorageTreeNode;

        final StorageTaskTransferNodeCurrentAction storageTaskAction = new StorageTaskTransferNodeCurrentAction(null, targetStorageTreeNode, destinationStorageTreeNode);

        storageTaskAction.setActionHeader("%s '%s' %s to '%s'".formatted(this.persistOriginalStorageTreeNode ? "Copy" : "Move", this.rootTargetStorageTreeNode.isDirectory() ? "directory" : "file", this.rootTargetStorageTreeNode.getOnDiskURL(), this.rootDestinationStorageTreeNode.getOnDiskURL()));
        storageTaskAction.setMessage("%s transfer task has been successfully initialized".formatted(this.rootTargetStorageTreeNode.isDirectory() ? "Directory" : "File"));

        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    @Override
    public synchronized void execute(String updatedName, String onExceptionAction, Boolean onExceptionActionIsPersistent) {
        final StorageTaskTransferNodeCurrentAction storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) this.getStorageTaskAction();

        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getNewStorageTreeNode().getId() != null) {
            storageTaskCurrentAction.setMessage("%s '%s' %s to %s task finished successfully!".formatted(this.rootTargetStorageTreeNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageTreeNode.getOnDiskURL(), this.persistOriginalStorageTreeNode ? "copy" : "move", this.rootDestinationStorageTreeNode.getOnDiskURL()));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (onExceptionAction != null && onExceptionAction.equals("cancel")) {
            storageTaskCurrentAction.setMessage("%s '%s' %s to %s task was cancelled...".formatted(this.rootTargetStorageTreeNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageTreeNode.getOnDiskURL(), this.persistOriginalStorageTreeNode ? "copy" : "move", this.rootDestinationStorageTreeNode.getOnDiskURL()));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            onExceptionActionIsPersistent = onExceptionActionIsPersistent != null ? onExceptionActionIsPersistent : false;
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetStorageTreeNode(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        Integer IOAttempts = 0;
        Boolean replaceExisting = false;
        storageTaskCurrentAction.incrementAttemptCounter();
        while (storageTaskCurrentAction.getNewStorageTreeNode().getId() == null) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(exceptionType, storageTaskCurrentAction.getTargetStorageTreeNode());

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "FileAlreadyExistsException":  /* TODO: add option to publish the conflicting node if it isn't published */
                            if (storageTaskCurrentAction.getConflictStorageTreeNode() == null) {
                                throw new RuntimeException("%s '%s' could not be %s to directory '%s' due to a supposed conflicting node, existence of which can neither be confirmed nor denied...".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL(), this.persistOriginalStorageTreeNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageTreeNode().getOnDiskURL()));
                            }

                            switch (exceptionAction) {
                                case "keep":
                                    storageTaskCurrentAction.getNewStorageTreeNode().setOnDiskName(StorageFileName.findNextAvailableFileName(storageTaskCurrentAction.getNewStorageTreeNode().getOnDiskName()));
                                    storageTaskCurrentAction.setConflictStorageTreeNode(null);
                                    break;
                                case "merge":
                                    if (!storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() || !storageTaskCurrentAction.getConflictStorageTreeNode().isDirectory()) {
                                        throw new FileAlreadyExistsException("Merge option is invalid: both nodes must be directories!");
                                    }

                                    /* here we leave the conflicting node intact to indicate that this is a merger while adopting its on disk path to skip the transfer process */
                                    storageTaskCurrentAction.getNewStorageTreeNode().setNodePath(storageTaskCurrentAction.getConflictStorageTreeNode().getNodePath());
                                    break;
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing transfer attempt */
                                    storageTaskCurrentAction.getNewStorageTreeNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageTreeNode(null);
                                    break;
                                case "replace":
                                    if (storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() || storageTaskCurrentAction.getConflictStorageTreeNode().isDirectory()) {
                                        throw new FileAlreadyExistsException("Replace option is invalid: both nodes must be files!");
                                    }
                                    
                                    this.storageTreeExecute().deleteStorageTreeNode(storageTaskCurrentAction.getConflictStorageTreeNode());
                                    storageTaskCurrentAction.setConflictStorageTreeNode(null);
                                    replaceExisting = true;
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL(), this.persistOriginalStorageTreeNode ? "copy" : "move", storageTaskCurrentAction.getDestinationStorageTreeNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentNodesChildren();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("keep", "Keep both".formatted(), true));
                                        if (storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() && storageTaskCurrentAction.getConflictStorageTreeNode().isDirectory()) {
                                            add(new OnExceptionOption("merge", "Merge directories".formatted(), true));
                                        }
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        if (!storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() && !storageTaskCurrentAction.getConflictStorageTreeNode().isDirectory()) {
                                            add(new OnExceptionOption("replace", "Replace".formatted(), true));
                                        }
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a conflicting node already in place.".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL(), this.persistOriginalStorageTreeNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageTreeNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameValidationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskCurrentAction.getNewStorageTreeNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageTreeNode(null);
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getNewStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getNewStorageTreeNode().getOnDiskName(), storageTaskCurrentAction.getNewStorageTreeNode().isDirectory() ? "creation in" : "upload to", storageTaskCurrentAction.getNewStorageTreeNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a provided file name being incompatible.".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL(), this.persistOriginalStorageTreeNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageTreeNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameGenerationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskCurrentAction.getNewStorageTreeNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageTreeNode(null);
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getNewStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getNewStorageTreeNode().getOnDiskName(), storageTaskCurrentAction.getNewStorageTreeNode().isDirectory() ? "creation in" : "upload to", storageTaskCurrentAction.getNewStorageTreeNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a failed name generation attempt.".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL(), this.persistOriginalStorageTreeNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageTreeNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL(), this.persistOriginalStorageTreeNode ? "copy" : "move", storageTaskCurrentAction.getDestinationStorageTreeNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentNodesChildren();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onPhysicalOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};
        
                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a persistent I/O exception occurring".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL(), this.persistOriginalStorageTreeNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageTreeNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onPhysicalOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL(), this.persistOriginalStorageTreeNode ? "copy" : "move", storageTaskCurrentAction.getDestinationStorageTreeNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentNodesChildren();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onGeneralOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};
        
                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to an unexpected exception occurring".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL(), this.persistOriginalStorageTreeNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageTreeNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onGeneralOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }   
                    }
                }

                if (storageTaskCurrentAction.getNewStorageTreeNode().getNodePath() == null) {
                    if (storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory()) {
                        storageTaskCurrentAction.setNewStorageTreeNode(this.storageTreeExecute().transferStorageTreeNode(storageTaskCurrentAction.getNewStorageTreeNode(), storageTaskCurrentAction.getTargetStorageTreeNode(), storageTaskCurrentAction.getDestinationStorageTreeNode(), this.persistOriginalStorageTreeNode, replaceExisting));
                    } else {
                        storageTaskCurrentAction.setNewStorageTreeNode(this.storageTreeExecute().transferStorageTreeNode(storageTaskCurrentAction.getNewStorageTreeNode(), storageTaskCurrentAction.getTargetStorageTreeNode(), storageTaskCurrentAction.getDestinationStorageTreeNode(), this.persistOriginalStorageTreeNode, replaceExisting));
                    }
                }

                /* TODO: handle this better */
                if (storageTaskCurrentAction.getNewStorageTreeNode().getId() == null) {
                    if (storageTaskCurrentAction.getConflictStorageTreeNode() == null) {
                        if (this.persistOriginalStorageTreeNode == true) { /* copy */
                            this.storageTreeExecute().publishStorageTreeNode(storageTaskCurrentAction.getNewStorageTreeNode());
                        } else { /* move */
                            storageTaskCurrentAction.getTargetStorageTreeNode().setNodePath(storageTaskCurrentAction.getNewStorageTreeNode().getNodePath());
                            storageTaskCurrentAction.setNewStorageTreeNode(this.storageTreeExecute().publishStorageTreeNode(storageTaskCurrentAction.getTargetStorageTreeNode()));
                        }
                    } else { /* merge */
                        storageTaskCurrentAction.setNewStorageTreeNode(storageTaskCurrentAction.getConflictStorageTreeNode());
                        storageTaskCurrentAction.setConflictStorageTreeNode(null);
                    }
                }

                /* TODO: handle this better */
                if (!this.persistOriginalStorageTreeNode == false) {
                    if (!Files.isDirectory(storageTaskCurrentAction.getTargetStorageTreeNode().getNodePath().getPath())) {
                        Files.deleteIfExists(storageTaskCurrentAction.getTargetStorageTreeNode().getNodePath().getPath());
                    }

                    if (storageTaskCurrentAction.getTargetStorageTreeNode().getIsAccessible()) {
                        this.storageTreeExecute().deleteStorageTreeNode(storageTaskCurrentAction.getTargetStorageTreeNode());
                    }
                }
                
                storageTaskCurrentAction.setMessage("%s '%s' has been %s to directory '%s' successfully!".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL(), this.persistOriginalStorageTreeNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageTreeNode().getOnDiskURL()));
                this.setCurrentState(TaskState.EXECUTING);
                this.advanceStorageTask();
                return;
            } catch (FileAlreadyExistsException exception) {
                try { /* Here we attempt to find the conflicting node. And if we can't find it, we try to recover it */
                    final Optional<StorageNode> possibleConflict =  storageTaskCurrentAction.getDestinationStorageTreeNode().getChildren().stream().filter(entry -> entry.getNodePath().getFileName().toString().equals(storageTaskCurrentAction.getNewStorageTreeNode().getOnDiskName())).findFirst();
                    if (possibleConflict.isPresent()) {
                        storageTaskCurrentAction.setConflictStorageTreeNode(possibleConflict.get());
                    } else {
                        storageTaskCurrentAction.setConflictStorageTreeNode(this.storageTreeExecute().recoverStorageTreeNode(storageTaskCurrentAction.getNewStorageTreeNode().getOnDiskName(), storageTaskCurrentAction.getDestinationStorageTreeNode()));
                    }
                } catch (Exception recoveryException) {
                    storageTaskCurrentAction.setConflictStorageTreeNode(null);

                    /* wild territories... */
                    storageTaskCurrentAction.setExceptionType(recoveryException.getClass().getSimpleName());
                    storageTaskCurrentAction.setExceptionMessage(recoveryException.getMessage());
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
                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
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
            if (this.persistOriginalStorageTreeNode == false) {
                try {
                    Files.deleteIfExists(storageTaskCurrentAction.getTargetStorageTreeNode().getNodePath().getPath());
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
        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getNewStorageTreeNode().getId() != null) {
            this.setCurrentState(TaskState.COMPLETED);
        }

        this.setStorageTaskAction(storageTaskCurrentAction);
        this.getTaskExceptionHandler().resetOnExceptionAction();
    }

    protected class StorageTaskTransferNodeCurrentAction extends StorageTaskCurrentAction {
        private StorageNode newStorageTreeNode;
        private StorageNode targetStorageTreeNode;
        private StorageNode conflictStorageTreeNode;
        private StorageNode destinationStorageTreeNode;

        protected StorageTaskTransferNodeCurrentAction(StorageTaskTransferNodeCurrentAction parentStorageTaskAction, StorageNode targetStorageTreeNode, StorageNode destinationStorageTreeNode) {
            super(parentStorageTaskAction);

            this.targetStorageTreeNode = targetStorageTreeNode;
            this.destinationStorageTreeNode = destinationStorageTreeNode;
            this.newStorageTreeNode = new StorageNode(null, targetStorageTreeNode.isDirectory() ? new ArrayList<>() : null, null, targetStorageTreeNode.getDescription());
            this.newStorageTreeNode.setOnDiskName(targetStorageTreeNode.getOnDiskName());

            if (this.targetStorageTreeNode.isDirectory()) {
                for (StorageNode childNode : this.targetStorageTreeNode.getChildren()) {
                    this.getChildStorageTaskActions().add(new StorageTaskTransferNodeCurrentAction(this, childNode, null));
                }
            }
        }

        public StorageNode getNewStorageTreeNode() {
            return this.newStorageTreeNode;
        }

        protected void setNewStorageTreeNode(StorageNode newStorageTreeNode) {
            this.newStorageTreeNode = newStorageTreeNode;
        }

        public StorageNode getTargetStorageTreeNode() {
            return this.targetStorageTreeNode;
        }
        
        protected void setTargetStorageTreeNode(StorageNode targetStorageTreeNode) {
            this.targetStorageTreeNode = targetStorageTreeNode;
        }
    
        public StorageNode getConflictStorageTreeNode() {
            return this.conflictStorageTreeNode;
        }

        protected void setConflictStorageTreeNode(StorageNode conflictStorageTreeNode) {
            this.conflictStorageTreeNode = conflictStorageTreeNode;
        }

        public StorageNode getDestinationStorageTreeNode() {
            return this.destinationStorageTreeNode;
        }

        protected void setDestinationStorageTreeNode(StorageNode destinationStorageTreeNode) {
            this.destinationStorageTreeNode = destinationStorageTreeNode;
        }
    }
}
