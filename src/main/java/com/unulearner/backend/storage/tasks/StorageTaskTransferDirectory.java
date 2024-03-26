package com.unulearner.backend.storage.tasks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;

import java.nio.file.FileAlreadyExistsException;

import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.exceptions.StorageServiceException;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.statics.StateCode;

public class StorageTaskTransferDirectory extends StorageTask {
    private StorageTreeNodeTransferReady currentTransferReadyStorageNode;
    private Deque<StorageTreeNodeTransferReady> currentTransferReadyDeque;
    private final Boolean persistOriginal;

    public StorageTaskTransferDirectory(StorageTree storageTree, StorageTreeNode targetNode, StorageTreeNode destinationNode, Boolean persistOriginal, StorageTasksMap storageTasksMap) {
        super(storageTree, targetNode, destinationNode, storageTasksMap);

        this.persistOriginal = persistOriginal;
        this.currentTransferReadyStorageNode = new StorageTreeNodeTransferReady(targetNode, destinationNode);
        this.currentTransferReadyDeque = this.currentTransferReadyStorageNode.getChildrenTRNodes();
        this.setOnExceptionOptions(new ArrayList<>(Arrays.asList(
            new Option("overwrite", "Overwrite", true, false),
            new Option("rename", "Rename", true, true),
            new Option("merge", "Merge", false, true),
            new Option("skip", "Skip", true, true)
        ))); //TODO: HANDLE EXCEPTIONS BETTER!!!

        this.setTaskHeading("%s %s to %s".formatted(this.persistOriginal ? "Copy" : "Move", this.getRootTarget().getOnDiskURL(), this.getRootDestination().getOnDiskURL()));
        this.setTaskCurrentState("Directory %s task was initiated...".formatted(this.persistOriginal ? "copy" : "move"), StateCode.RUNNING);
    }

    @Override
    public synchronized void executeTask(String newOnExceptionAction, Boolean newOnExceptionActionIsPersistent, Boolean cancelTaskExecution) {
        final StorageTreeNode currentTarget = this.getCurrentTarget();
        final StorageTreeNode currentDestination = this.getCurrentDestination();

        if (currentTarget == null) {
            this.setTaskCurrentState("Directory %s task finished successfully!".formatted(this.persistOriginal ? "copy" : "move"), StateCode.COMPLETED);
            return;
        }

        if (currentDestination == null) {
            this.setTaskCurrentState("Directory %s task, in defiance of all logic, has somehow failed... miserably...".formatted(this.persistOriginal ? "copy" : "move"), StateCode.ERROR);
            return;
        }

        if (cancelTaskExecution != null && cancelTaskExecution == true) {
            this.setTaskCurrentState("Directory %s task was cancelled...".formatted(this.persistOriginal ? "copy" : "move"), StateCode.CANCELLED);
            return;
        }

        if (newOnExceptionAction != null) {
            newOnExceptionActionIsPersistent = newOnExceptionActionIsPersistent != null ? newOnExceptionActionIsPersistent : false;
            this.setOnExceptionAction(currentTarget, newOnExceptionAction, newOnExceptionActionIsPersistent);
        }

        /* If new rule was set it will be returned here, otherwise we'll get "default" */
        final String onExceptionAction = this.getOnExceptionAction(currentTarget);

        try {
            if (onExceptionAction.equals("skip") && this.getAttemptCounter() > 0) {
                this.setTaskCurrentState("Directory '%s' %s to directory '%s' skipped...".formatted(currentTarget.getOnDiskURL(), this.persistOriginal ? "copy" : "move", currentDestination.getOnDiskURL()), StateCode.RUNNING);
                this.advanceTask();
                return;
            }

            this.incrementAttemptCounter();
            this.currentTransferReadyStorageNode.setNewStorageNode(this.storageTreeExecute().commitStorageTreeNode(currentTarget, currentDestination, this.persistOriginal, onExceptionAction));
            this.setTaskCurrentState("%s '%s' was %s to directory '%s' successfully!".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL(), this.persistOriginal ? "copied" : "moved", currentDestination.getOnDiskURL()), StateCode.RUNNING);
            this.advanceTask();
            return;
        } catch (FileAlreadyExistsException exception) {
            this.setTaskCurrentState("Directory '%s' could not be %s to directory '%s' - %s".formatted(currentTarget.getOnDiskURL(), this.persistOriginal ? "copied" : "moved", currentDestination.getOnDiskURL(), exception.getMessage()), StateCode.EXCEPTION);
            return; //TODO: HANDLE EXCEPTIONS BETTER!!!
        } catch (StorageServiceException exception) {
            this.setTaskCurrentState("Directory '%s' could not be %s to directory '%s' - %s".formatted(currentTarget.getOnDiskURL(), this.persistOriginal ? "copied" : "moved", currentDestination.getOnDiskURL(), exception.getMessage()), StateCode.EXCEPTION);
            return;
        } catch (Exception exception) {
            this.setTaskCurrentState("Directory '%s' could not be %s to directory '%s' - %s".formatted(currentTarget.getOnDiskURL(), this.persistOriginal ? "copied" : "moved", currentDestination.getOnDiskURL(), exception.getMessage()), this.getAttemptCounter() > 3 ? StateCode.ERROR : StateCode.EXCEPTION);
            return;
        }
    }

    @Override
    protected void advanceTask() {
        this.resetOnExceptionAction(this.getCurrentTarget());
        this.resetAttemptCounter();

        while (this.currentTransferReadyDeque != null && this.currentTransferReadyDeque.peekFirst() == null) {
            this.currentTransferReadyStorageNode = this.currentTransferReadyStorageNode.getParentTRNode();

            if (this.currentTransferReadyStorageNode != null) {
                this.currentTransferReadyDeque = this.currentTransferReadyStorageNode.getChildrenTRNodes();
            } else {
                this.currentTransferReadyDeque = null;
            }
        }

        if (this.currentTransferReadyDeque == null) {
            this.setCurrentDestination(null);
            this.setCurrentTarget(null);
            return;
        }

        this.currentTransferReadyStorageNode = this.currentTransferReadyDeque.pollFirst();

        this.setCurrentTarget(this.currentTransferReadyStorageNode.getTargetStorageNode());
        this.setCurrentDestination(this.currentTransferReadyStorageNode.getParentTRNode().getNewStorageNode());
        
        if (!this.currentTransferReadyStorageNode.getChildrenTRNodes().isEmpty()) {
            this.currentTransferReadyDeque = this.currentTransferReadyStorageNode.getChildrenTRNodes();
        }
    }

    private static class StorageTreeNodeTransferReady {
        private StorageTreeNode newStorageNode;
        private final StorageTreeNode targetStorageNode;
        private final StorageTreeNodeTransferReady parentTRNode;
        private final Deque<StorageTreeNodeTransferReady> childrenTRNodes;

        public StorageTreeNodeTransferReady(StorageTreeNode targetNode, StorageTreeNode destinationNode) {
            this.childrenTRNodes = new ArrayDeque<>();
            this.targetStorageNode = destinationNode;
            this.newStorageNode = destinationNode;
            this.parentTRNode = null; /* EXIT */

            this.childrenTRNodes.offer(new StorageTreeNodeTransferReady(targetNode, this));
        }

        private StorageTreeNodeTransferReady(StorageTreeNode targetNode, StorageTreeNodeTransferReady parentTRNode) {
            this.childrenTRNodes = new ArrayDeque<>();
            this.targetStorageNode = targetNode;
            this.parentTRNode = parentTRNode;

            if (targetNode.getChildren() != null && !targetNode.getChildren().isEmpty()) {
                for (StorageTreeNode childNode : targetNode.getChildren()) {
                    this.childrenTRNodes.offer(new StorageTreeNodeTransferReady(childNode, this));
                }
            } 
        }

        public void setNewStorageNode(StorageTreeNode newStorageNode) {
            this.newStorageNode = newStorageNode;
        }

        public StorageTreeNode getNewStorageNode() {
            return this.newStorageNode;
        }

        public StorageTreeNode getTargetStorageNode() {
            return this.targetStorageNode;
        }

        public StorageTreeNodeTransferReady getParentTRNode() {
            return this.parentTRNode;
        }

        public Deque<StorageTreeNodeTransferReady> getChildrenTRNodes() {
            return this.childrenTRNodes;
        }
    }
}
