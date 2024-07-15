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
    private final StorageNode rootDestinationStorageNode;
    private final StorageNode rootTargetStorageNode;
    private final Boolean persistOriginalStorageNode;

    public StorageTaskTransferNode(StorageTree storageTree, StorageNode targetStorageNode, StorageNode destinationStorageNode, Boolean persistOriginal, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        this.persistOriginalStorageNode = persistOriginal != null ? persistOriginal : false;
        this.rootDestinationStorageNode = destinationStorageNode;
        this.rootTargetStorageNode = targetStorageNode;

        final StorageTaskTransferNodeCurrentAction storageTaskAction = new StorageTaskTransferNodeCurrentAction(null, targetStorageNode, destinationStorageNode);

        storageTaskAction.setActionHeader("%s '%s' %s to '%s'".formatted(this.persistOriginalStorageNode ? "Copy" : "Move", this.rootTargetStorageNode.isDirectory() ? "directory" : "file", this.rootTargetStorageNode.getOnDiskFormattedURL(), this.rootDestinationStorageNode.getOnDiskFormattedURL()));
        storageTaskAction.setMessage("%s transfer task has been successfully initialized".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File"));

        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    @Override
    public synchronized void execute(String updatedName, String onExceptionAction, Boolean onExceptionActionIsPersistent) {
        final StorageTaskTransferNodeCurrentAction storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) this.getStorageTaskAction();

        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getNewStorageNode().getId() != null) {
            storageTaskCurrentAction.setMessage("%s '%s' %s to %s task finished successfully!".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getOnDiskFormattedURL(), this.persistOriginalStorageNode ? "copy" : "move", this.rootDestinationStorageNode.getOnDiskFormattedURL()));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (onExceptionAction != null && onExceptionAction.equals("cancel")) {
            storageTaskCurrentAction.setMessage("%s '%s' %s to %s task was cancelled...".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageNode.getOnDiskFormattedURL(), this.persistOriginalStorageNode ? "copy" : "move", this.rootDestinationStorageNode.getOnDiskFormattedURL()));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            onExceptionActionIsPersistent = onExceptionActionIsPersistent != null ? onExceptionActionIsPersistent : false;
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
                        case "FileAlreadyExistsException":  /* TODO: add option to publish the conflicting node if it isn't published */
                            if (storageTaskCurrentAction.getConflictStorageNode() == null) {
                                throw new RuntimeException("%s '%s' could not be %s to directory '%s' due to a supposed conflicting node, existence of which can neither be confirmed nor denied...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL(), this.persistOriginalStorageNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskFormattedURL()));
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
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL(), this.persistOriginalStorageNode ? "copy" : "move", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskFormattedURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentNodesChildren();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("keep", "Keep both".formatted(), true));
                                        if (storageTaskCurrentAction.getTargetStorageNode().isDirectory() && storageTaskCurrentAction.getConflictStorageNode().isDirectory()) {
                                            add(new OnExceptionOption("merge", "Merge directories".formatted(), true));
                                        }
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        if (!storageTaskCurrentAction.getTargetStorageNode().isDirectory() && !storageTaskCurrentAction.getConflictStorageNode().isDirectory()) {
                                            add(new OnExceptionOption("replace", "Replace".formatted(), true));
                                        }
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a conflicting node already in place.".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL(), this.persistOriginalStorageNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskFormattedURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
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
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getNewStorageNode().getOnDiskName(), storageTaskCurrentAction.getNewStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskCurrentAction.getNewStorageNode().getOnDiskFormattedURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a provided file name being incompatible.".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL(), this.persistOriginalStorageNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskFormattedURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
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
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getNewStorageNode().getOnDiskName(), storageTaskCurrentAction.getNewStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskCurrentAction.getNewStorageNode().getOnDiskFormattedURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a failed name generation attempt.".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL(), this.persistOriginalStorageNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskFormattedURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL(), this.persistOriginalStorageNode ? "copy" : "move", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskFormattedURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentNodesChildren();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onPhysicalOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"), true));
                                    }};
        
                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a persistent I/O exception occurring".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL(), this.persistOriginalStorageNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskFormattedURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onPhysicalOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL(), this.persistOriginalStorageNode ? "copy" : "move", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskFormattedURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipCurrentNodesChildren();
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onGeneralOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"), true));
                                    }};
        
                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to an unexpected exception occurring".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL(), this.persistOriginalStorageNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskFormattedURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onGeneralOptions);
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
                
                storageTaskCurrentAction.setMessage("%s '%s' has been %s to directory '%s' successfully!".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getOnDiskFormattedURL(), this.persistOriginalStorageNode ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getOnDiskFormattedURL()));
                this.setCurrentState(TaskState.EXECUTING);
                this.advanceStorageTask();
                return;
            } catch (FileAlreadyExistsException exception) {
                try { /* Here we attempt to find the conflicting node. And if we can't find it, we try to recover it */
                    final Optional<StorageNode> possibleConflict =  storageTaskCurrentAction.getDestinationStorageNode().getChildren().stream().filter(entry -> entry.getNodePath().getFileName().toString().equals(storageTaskCurrentAction.getNewStorageNode().getOnDiskName())).findFirst();
                    if (possibleConflict.isPresent()) {
                        storageTaskCurrentAction.setConflictStorageNode(possibleConflict.get());
                    } else {
                        storageTaskCurrentAction.setConflictStorageNode(this.storageTreeExecute().recoverStorageNode(storageTaskCurrentAction.getNewStorageNode().getOnDiskName(), storageTaskCurrentAction.getDestinationStorageNode()));
                    }
                } catch (Exception recoveryException) {
                    storageTaskCurrentAction.setConflictStorageNode(null);

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
