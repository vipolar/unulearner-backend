package com.unulearner.backend.storage.tasks;

import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Deque;
import java.util.List;

import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.statics.StoragePath;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.tasks.StorageTask.StorageTaskState.Option;
import com.unulearner.backend.storage.tasks.StorageTask.StorageTaskState.StateCode;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.unulearner.backend.storage.exceptions.StorageServiceException;

public class StorageTaskTransfer extends StorageTask {
    private final StorageTaskCurrentAction rootStorageTaskTransferAction;
    private final StorageTreeNode rootDestinationStorageTreeNode;
    private final StorageTreeNode rootTargetStorageTreeNode;
    private final Boolean persistOriginal;

    private void buildStorageTaskActionTree(StorageTaskCurrentAction parentStorageTaskAction) {
        parentStorageTaskAction.getTargetStorageTreeNode().setBusyWith(this.getTaskUUID());

        if (parentStorageTaskAction.getDestinationStorageTreeNode() != null) {
            parentStorageTaskAction.getDestinationStorageTreeNode().setBusyWith(this.getTaskUUID());
        }

        final List<StorageTreeNode> targetStorageTreeNodeChildren = parentStorageTaskAction.getTargetStorageTreeNode().getChildren();
        if (targetStorageTreeNodeChildren != null && !targetStorageTreeNodeChildren.isEmpty()) {
            for (StorageTreeNode currentStorageTreeNode : targetStorageTreeNodeChildren) {
                this.buildStorageTaskActionTree(new StorageTaskCurrentAction(parentStorageTaskAction, currentStorageTreeNode, null));
            }
        }
    }

    private void cleanUpStorageTaskActionTree(StorageTaskCurrentAction parentStorageTaskAction) {
        parentStorageTaskAction.getNewStorageTreeNode().setBusyWith(null);
        parentStorageTaskAction.getTargetStorageTreeNode().setBusyWith(null);
        parentStorageTaskAction.getDestinationStorageTreeNode().setBusyWith(null);
        parentStorageTaskAction.getConflictingStorageTreeNode().setBusyWith(null);

        final Deque<StorageTaskCurrentAction> childrenStorageTaskAction = parentStorageTaskAction.getChildrenStorageTaskAction();

        while (childrenStorageTaskAction.peekFirst() != null) {
            this.cleanUpStorageTaskActionTree(childrenStorageTaskAction.pollFirst());
        }
    }

    public StorageTaskTransfer(StorageTree storageTree, StorageTreeNode targetStorageTreeNode, StorageTreeNode destinationStorageTreeNode, Boolean persistOriginal, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        this.persistOriginal = persistOriginal != null ? persistOriginal : false;
        this.rootDestinationStorageTreeNode = destinationStorageTreeNode;
        this.rootTargetStorageTreeNode = targetStorageTreeNode;

        final StorageTaskCurrentAction initialStorageTaskAction = new StorageTaskCurrentAction(null, targetStorageTreeNode, destinationStorageTreeNode);
        initialStorageTaskAction.setTaskStateLogMessage("%s %s task was initiated...".formatted(targetStorageTreeNode.isDirectory() ? "Directory" : "File", this.persistOriginal ? "copy" : "move"));
        initialStorageTaskAction.setTaskHeading("%s %s to %s".formatted(this.persistOriginal ? "Copy" : "Move", targetStorageTreeNode.getOnDiskURL(), destinationStorageTreeNode.getOnDiskURL()));
        this.rootStorageTaskTransferAction = initialStorageTaskAction; 
        this.buildStorageTaskActionTree(initialStorageTaskAction);
        this.setStorageTaskState(initialStorageTaskAction);
    }

    @Override
    public synchronized void executeTask(String updatedName, String onExceptionAction, Boolean onExceptionActionIsPersistent) {
        final StorageTaskCurrentAction currentStorageTaskAction = (StorageTaskCurrentAction) this.getStorageTaskState();
        final StorageTreeNode currentTarget = currentStorageTaskAction.getTargetStorageTreeNode();
        final StorageTreeNode currentDestination = currentStorageTaskAction.getDestinationStorageTreeNode();

        if (currentStorageTaskAction.getParentStorageTaskAction() == null && currentStorageTaskAction.getNewStorageTreeNode().getId() != null) {
            currentStorageTaskAction.setTaskStateLogMessage("%s %s to %s task finished successfully!".formatted(this.rootTargetStorageTreeNode.isDirectory() ? "Directory" : "File", this.persistOriginal ? "copy" : "move", this.rootDestinationStorageTreeNode.isDirectory()));
            currentStorageTaskAction.setTaskState(StateCode.COMPLETED);
            this.cleanTaskUp();
            return;
        }

        if (onExceptionAction != null && onExceptionAction.equals("cancel")) {
            currentStorageTaskAction.setTaskStateLogMessage("%s %s to %s task was cancelled...".formatted(this.rootTargetStorageTreeNode.isDirectory() ? "Directory" : "File", this.persistOriginal ? "copy" : "move", this.rootDestinationStorageTreeNode.isDirectory()));
            currentStorageTaskAction.setTaskState(StateCode.CANCELLED);
            this.cleanTaskUp();
            return;
        }

        if (onExceptionAction != null) {
            onExceptionActionIsPersistent = onExceptionActionIsPersistent != null ? onExceptionActionIsPersistent : false;
            this.setOnExceptionAction(currentTarget, currentStorageTaskAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        Integer IOAttempts = 0;
        Boolean replaceExisting = false;
        String currentExceptionType = null;
        currentStorageTaskAction.incrementAttemptCounter();
        while (currentStorageTaskAction.getNewStorageTreeNode().getId() == null) {
            try {
                if ((currentExceptionType = currentStorageTaskAction.getExceptionType()) != null) {
                    switch (currentExceptionType) {
                        case "conflict":
                            if (currentStorageTaskAction.getConflictingStorageTreeNode() == null) { /* TODO: catch this!!! */
                                throw new StorageServiceException("%s '%s' could not be %s to directory '%s' due to a supposed conflicting node, existence of which can neither be confirmed nor denied...".formatted(currentTarget.isDirectory() ? "Directory" : "File", currentTarget.getOnDiskURL(), this.persistOriginal ? "copied" : "moved", currentDestination.getOnDiskURL()));
                            }

                            final String onConflictAction = this.getOnExceptionAction(currentExceptionType, currentTarget);
                            switch (onConflictAction) {
                                case "keep":
                                    currentStorageTaskAction.getNewStorageTreeNode().setOnDiskName(StoragePath.findNextAvailableName(currentStorageTaskAction.getNewStorageTreeNode().getOnDiskName(), currentDestination.getRelativePath()));
                                    currentStorageTaskAction.setConflictingStorageTreeNode(null);
                                    break;
                                case "merge":
                                    if (!Files.isDirectory(currentTarget.getRelativePath()) || !Files.isDirectory(currentStorageTaskAction.getConflictingStorageTreeNode().getRelativePath())) {
                                        throw new FileAlreadyExistsException("Merge option is invalid: both nodes must be directories!");
                                    }

                                    /* here we leave the conflicting node intact to indicate that this is a merger while adopting its on disk path to skip the transfer process */
                                    currentStorageTaskAction.getNewStorageTreeNode().setRelativePath(currentStorageTaskAction.getConflictingStorageTreeNode().getRelativePath());
                                    break;
                                case "rename":
                                    if (updatedName == null || (updatedName = updatedName.trim()).isBlank()) {
                                        throw new StorageServiceException("Where da name???");
                                    }

                                    /* Set a new, manually entered name to be used in the ensuing transfer attempt */
                                    currentStorageTaskAction.getNewStorageTreeNode().setOnDiskName(updatedName);
                                    currentStorageTaskAction.setConflictingStorageTreeNode(null);
                                    break;
                                case "replace":
                                    if (Files.isDirectory(currentTarget.getRelativePath()) || Files.isDirectory(currentStorageTaskAction.getConflictingStorageTreeNode().getRelativePath())) {
                                        throw new FileAlreadyExistsException("Replace option is invalid: both nodes must be files!");
                                    }
                                    
                                    this.storageTreeExecute().unPublishStorageTreeNode(currentStorageTaskAction.getConflictingStorageTreeNode());
                                    currentStorageTaskAction.setConflictingStorageTreeNode(null);
                                    replaceExisting = true;
                                    break;
                                case "skip":
                                    currentStorageTaskAction.setTaskStateLogMessage("%s '%s' %s to directory '%s' was skipped...".formatted(currentTarget.isDirectory() ? "Directory" : "File", currentTarget.getOnDiskURL(), this.persistOriginal ? "copy" : "move", currentDestination.getOnDiskURL()));
                                    currentStorageTaskAction.setTaskState(StateCode.EXECUTING);
                                    this.advanceTask(true);
                                    return;
                                default:
                                    List<Option> onConflictOptions = new ArrayList<>() {{
                                        add(new Option("keep", "Keep both".formatted(), true));
                                        if (currentTarget.isDirectory() && currentStorageTaskAction.getConflictingStorageTreeNode().isDirectory()) {
                                            add(new Option("merge", "Merge directories".formatted(), true));
                                        }
                                        add(new Option("rename", "Rename manually".formatted(), false));
                                        if (!currentTarget.isDirectory() && !currentStorageTaskAction.getConflictingStorageTreeNode().isDirectory()) {
                                            add(new Option("replace", "Replace".formatted(), true));
                                        }
                                        add(new Option("skip", "Skip".formatted(), true));
                                    }};

                                    currentStorageTaskAction.setTaskStateLogMessage("%s '%s' could not be %s to directory '%s' due to a conflicting node already in place".formatted(currentTarget.isDirectory() ? "Directory" : "File", currentTarget.getOnDiskURL(), this.persistOriginal ? "copied" : "moved", currentDestination.getOnDiskURL()));
                                    currentStorageTaskAction.setExceptionOptions(onConflictOptions);
                                    currentStorageTaskAction.setTaskState(StateCode.EXCEPTION);
                                    return;
                            }
                        case "physical":
                            final String onPhysicalExceptionAction = this.getOnExceptionAction(currentExceptionType, currentTarget);
                            if (onPhysicalExceptionAction.equals("skip")) {
                                currentStorageTaskAction.setTaskStateLogMessage("%s '%s' %s to directory '%s' was skipped...".formatted(currentTarget.isDirectory() ? "Directory" : "File", currentTarget.getOnDiskURL(), this.persistOriginal ? "copy" : "move", currentDestination.getOnDiskURL()));
                                currentStorageTaskAction.setTaskState(StateCode.EXECUTING);
                                this.advanceTask(true);
                                return;
                            }

                            List<Option> onPhysicalOptions = new ArrayList<>() {{
                                add(new Option("skip", "Skip".formatted(), true));
                            }};

                            currentStorageTaskAction.setExceptionOptions(onPhysicalOptions);
                            currentStorageTaskAction.setTaskState(StateCode.EXCEPTION);
                            return;
                        case "general":
                            final String onGeneralExceptionAction = this.getOnExceptionAction(currentExceptionType, currentTarget);
                            if (onGeneralExceptionAction.equals("skip")) {
                                currentStorageTaskAction.setTaskStateLogMessage("%s '%s' %s to directory '%s' was skipped...".formatted(currentTarget.isDirectory() ? "Directory" : "File", currentTarget.getOnDiskURL(), this.persistOriginal ? "copy" : "move", currentDestination.getOnDiskURL()));
                                currentStorageTaskAction.setTaskState(StateCode.EXECUTING);
                                this.advanceTask(true);
                                return;
                            }

                            List<Option> onGeneralOptions = new ArrayList<>() {{
                                add(new Option("skip", "Skip".formatted(), true));
                            }};

                            currentStorageTaskAction.setExceptionOptions(onGeneralOptions);
                            currentStorageTaskAction.setTaskState(StateCode.EXCEPTION);
                            return;
                        default:
                            //TODO: unhandled
                            break;
                    }
                }

                if (currentStorageTaskAction.getNewStorageTreeNode() == null) {
                    final StorageTreeNode newStorageTreeNode = new StorageTreeNode(currentDestination, currentTarget.isDirectory() ? new ArrayList<StorageTreeNode>() : null, null, currentTarget.getDescription());
                    currentStorageTaskAction.setNewStorageTreeNode(newStorageTreeNode);
                    newStorageTreeNode.setBusyWith(this.getTaskUUID());
                }

                if (currentStorageTaskAction.getNewStorageTreeNode().getRelativePath() == null) {
                    if (currentTarget.isDirectory()) {
                        currentStorageTaskAction.getNewStorageTreeNode().setRelativePath(this.storageTreeExecute().transferNode(currentStorageTaskAction.getNewStorageTreeNode().getOnDiskName(), currentTarget.getRelativePath(), currentDestination.getRelativePath(), this.persistOriginal, replaceExisting));
                    } else {
                        currentStorageTaskAction.getNewStorageTreeNode().setRelativePath(this.storageTreeExecute().transferNode(currentStorageTaskAction.getNewStorageTreeNode().getOnDiskName(), currentTarget.getRelativePath(), currentDestination.getRelativePath(), this.persistOriginal, replaceExisting));
                    }
                }

                if (currentStorageTaskAction.getNewStorageTreeNode().getRelativePath() == null
                || (!Files.isDirectory(currentStorageTaskAction.getNewStorageTreeNode().getRelativePath()) && !StoragePath.isValidFile(currentStorageTaskAction.getNewStorageTreeNode().getRelativePath()))
                || (Files.isDirectory(currentStorageTaskAction.getNewStorageTreeNode().getRelativePath()) && !StoragePath.isValidDirectory(currentStorageTaskAction.getNewStorageTreeNode().getRelativePath()))) {
                    throw new StorageServiceException("%s '%s' has been %s to directory '%s' yet it is not accessible...".formatted(currentTarget.isDirectory() ? "Directory" : "File", currentTarget.getOnDiskURL(), this.persistOriginal ? "copied" : "moved", currentDestination.getOnDiskURL()));
                }

                if (currentStorageTaskAction.getNewStorageTreeNode().getId() == null) {
                    if (currentStorageTaskAction.getConflictingStorageTreeNode() == null) {
                        if (this.persistOriginal == true) { /* copy */
                            this.storageTreeExecute().publishStorageTreeNode(currentStorageTaskAction.getNewStorageTreeNode());
                        } else { /* move */
                            currentTarget.setRelativePath(currentStorageTaskAction.getNewStorageTreeNode().getRelativePath());
                            currentStorageTaskAction.setNewStorageTreeNode(this.storageTreeExecute().updateStorageTreeNode(currentTarget));
                        }
                    } else { /* merge */
                        currentStorageTaskAction.setNewStorageTreeNode(currentStorageTaskAction.getConflictingStorageTreeNode());
                        currentStorageTaskAction.setConflictingStorageTreeNode(null);
                    }
                }

                if (!this.persistOriginal == false) {
                    if (!Files.isDirectory(currentTarget.getRelativePath())) {
                        Files.deleteIfExists(currentTarget.getRelativePath());
                    }

                    if (currentTarget.getIsAccessible()) {
                        this.storageTreeExecute().unPublishStorageTreeNode(currentTarget);
                    }
                }

                
                currentStorageTaskAction.setTaskStateLogMessage("%s '%s' has been %s to directory '%s' successfully!".formatted(currentTarget.isDirectory() ? "Directory" : "File", currentTarget.getOnDiskURL(), this.persistOriginal ? "copied" : "moved", currentDestination.getOnDiskURL()));
                currentStorageTaskAction.setTaskState(StateCode.EXECUTING);
                this.advanceTask(false);
                return;
            } catch (FileAlreadyExistsException exception) {
                try { /* Here we attempt to find the conflicting node. And if we can't find it, we try to recover it */
                    final Optional<StorageTreeNode> possibleConflict =  currentDestination.getChildren().stream().filter(entry -> entry.getRelativePath().getFileName().toString().equals(currentStorageTaskAction.getNewStorageTreeNode().getOnDiskName())).findFirst();
                    if (possibleConflict.isPresent()) {
                        currentStorageTaskAction.setConflictingStorageTreeNode(possibleConflict.get());
                    } else {
                        final StorageTreeNode recoveredStorageNode = this.storageTreeExecute().recoverStorageTreeNode(currentStorageTaskAction.getNewStorageTreeNode().getOnDiskName(), currentDestination);
                        currentStorageTaskAction.setConflictingStorageTreeNode(this.storageTreeExecute().publishStorageTreeNode(recoveredStorageNode));
                    }
                } catch (Exception recoveryException) {
                    currentStorageTaskAction.setConflictingStorageTreeNode(null);

                    /* wild territories... */
                    currentStorageTaskAction.setExceptionType("general");
                    currentStorageTaskAction.setExceptionMessage(recoveryException.getMessage());
                    continue;
                }

                currentStorageTaskAction.setExceptionType("conflict");
                currentStorageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (IOException exception) {
                if (IOAttempts++ < 3) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException sleepException) {
                        currentStorageTaskAction.setExceptionType("general");
                        currentStorageTaskAction.setExceptionMessage(exception.getMessage());
                        continue;
                    }
                }

                currentStorageTaskAction.setExceptionType("physical");
                currentStorageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (Exception exception) {
                currentStorageTaskAction.setExceptionType("general");
                currentStorageTaskAction.setExceptionMessage(exception.getMessage());
            }
        }
    }

    @Override
    protected void advanceTask(Boolean skipChildren) {
        StorageTaskCurrentAction currentStorageTaskAction = (StorageTaskCurrentAction) this.getStorageTaskState();

        if (skipChildren != null && skipChildren == true && currentStorageTaskAction.getParentStorageTaskAction() != null) {
            currentStorageTaskAction.getNewStorageTreeNode().setBusyWith(null);
            currentStorageTaskAction.getTargetStorageTreeNode().setBusyWith(null);
            currentStorageTaskAction.getConflictingStorageTreeNode().setBusyWith(null);

            currentStorageTaskAction = currentStorageTaskAction.getParentStorageTaskAction();
        }

        while (currentStorageTaskAction.getChildrenStorageTaskAction().peekFirst() == null) {
            if (this.persistOriginal == false) {
                try {
                    Files.deleteIfExists(currentStorageTaskAction.getTargetStorageTreeNode().getRelativePath());
                } catch (IOException exception) {
                    //TODO: log...
                    exception.printStackTrace();
                }
            }

            if (currentStorageTaskAction.getParentStorageTaskAction() == null) {
                break;
            }

            /* We don't clean up the root here, we need it elsewhere!  */
            currentStorageTaskAction.getNewStorageTreeNode().setBusyWith(null);
            currentStorageTaskAction.getTargetStorageTreeNode().setBusyWith(null);
            currentStorageTaskAction.getConflictingStorageTreeNode().setBusyWith(null);

            currentStorageTaskAction = currentStorageTaskAction.getParentStorageTaskAction();
        }

        if (currentStorageTaskAction.getChildrenStorageTaskAction().peekFirst() != null) {
            currentStorageTaskAction = currentStorageTaskAction.getChildrenStorageTaskAction().pollFirst();
        }

        this.setStorageTaskState(currentStorageTaskAction);
        this.resetOnExceptionAction();
    }

    @Override
    public synchronized void cleanTaskUp() {
        this.cleanUpStorageTaskActionTree(this.rootStorageTaskTransferAction);
    }

    protected class StorageTaskCurrentAction extends StorageTaskState {
        private StorageTreeNode newStorageTreeNode;
        private StorageTreeNode targetStorageTreeNode;
        private StorageTreeNode destinationStorageTreeNode;
        private StorageTreeNode conflictingStorageTreeNode;

        @JsonIgnore
        private final StorageTaskCurrentAction parentStorageTaskAction;

        @JsonIgnore
        private final Deque<StorageTaskCurrentAction> childrenStorageTaskAction;

        public StorageTaskCurrentAction(StorageTaskCurrentAction parentAction, StorageTreeNode targetStorageTreeNode, StorageTreeNode destinationStorageTreeNode) {
            super();
            this.targetStorageTreeNode = targetStorageTreeNode;
            this.destinationStorageTreeNode = destinationStorageTreeNode;
            this.childrenStorageTaskAction =  new ArrayDeque<>();
            this.parentStorageTaskAction = parentAction;
        }

        public StorageTreeNode getNewStorageTreeNode() {
            return this.newStorageTreeNode;
        }

        public void setNewStorageTreeNode(StorageTreeNode newStorageTreeNode) {
            this.newStorageTreeNode = newStorageTreeNode;
        }

        public StorageTreeNode getTargetStorageTreeNode() {
            return this.targetStorageTreeNode;
        }
        
        public void setTargetStorageTreeNode(StorageTreeNode targetStorageTreeNode) {
            this.targetStorageTreeNode = targetStorageTreeNode;
        }
    
        public StorageTreeNode getDestinationStorageTreeNode() {
            return this.destinationStorageTreeNode;
        }

        public void setDestinationStorageTreeNode(StorageTreeNode destinationStorageTreeNode) {
            this.destinationStorageTreeNode = destinationStorageTreeNode;
        }

        public StorageTreeNode getConflictingStorageTreeNode() {
            return this.conflictingStorageTreeNode;
        }

        public void setConflictingStorageTreeNode(StorageTreeNode conflictingStorageTreeNode) {
            this.conflictingStorageTreeNode = conflictingStorageTreeNode;
        }

        public StorageTaskCurrentAction getParentStorageTaskAction() {
            return this.parentStorageTaskAction;
        }

        public Deque<StorageTaskCurrentAction> getChildrenStorageTaskAction() {
            return this.childrenStorageTaskAction;
        }
    }
}
