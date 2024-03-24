package com.unulearner.backend.storage.tasks;

import java.time.Instant;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;

import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;

public class StorageTaskTransferDirectory extends StorageTask {
    private final Boolean persistOriginal;
    private StorageTreeNodeTransferReady currentTransferReadyStorageNode;
    private Deque<StorageTreeNodeTransferReady> currentTransferReadyDeque;

    public StorageTaskTransferDirectory(@NonNull StorageTree storageTree, @NonNull StorageTreeNode targetNode, @NonNull StorageTreeNode destinationNode, Boolean persistOriginal) {
        super(storageTree, targetNode);

        this.currentTransferReadyStorageNode = new StorageTreeNodeTransferReady(targetNode, destinationNode);
        this.currentTransferReadyDeque = this.currentTransferReadyStorageNode.getChildrenTRNodes();
        this.setOnConflictOptions(new ArrayList<>(Arrays.asList(
            new Option("overwrite", "Overwrite", true, false),
            new Option("rename", "Rename", true, true),
            new Option("merge", "Merge", false, true),
            new Option("skip", "Skip", true, true)
        )));

        this.persistOriginal = persistOriginal;
        final String exitMessage = "Directory transfer task initiated on %s!".formatted(Instant.now().toString());
        this.setExitStatus(HttpStatus.ACCEPTED);
        this.setExitMessage(exitMessage);
        this.logMessage(exitMessage);
        this.advanceTaskForward();
    }

    @Override
    public synchronized void run(String onConflictAction, Boolean onConflictActionIsPersistent, Boolean cancelTaskExecution) {
        String exitMessage = null;

        if (cancelTaskExecution != null && cancelTaskExecution == true) {
            exitMessage = "Directory transfer task cancelled on %s!".formatted(Instant.now().toString());
            this.setExitStatus(HttpStatus.OK);
            this.setExitMessage(exitMessage);
            this.logMessage(exitMessage);
            this.setTaskAsDone();
            return;
        }
        
        final StorageTreeNode currentTarget = this.getCurrentTarget();
        if (currentTarget == null) {
            exitMessage = "Directory transfer task finished successfully on %s!".formatted(Instant.now().toString());
            this.setExitStatus(HttpStatus.OK);
            this.setExitMessage(exitMessage);
            this.logMessage(exitMessage);
            this.setTaskAsDone();
            return;
        }

        final StorageTreeNode currentDestination = this.getCurrentDestination();
        if (currentDestination == null) {
            exitMessage = "WTF? no destination? on %s!".formatted(Instant.now().toString());
            this.setExitStatus(HttpStatus.EXPECTATION_FAILED);
            this.setExitMessage(exitMessage);
            this.logMessage(exitMessage);
            return;
        }

        final String onConflict = this.getOnConflict();
        if (onConflictAction != null) {
            onConflictActionIsPersistent = onConflictActionIsPersistent != null ? onConflictActionIsPersistent : false;
            this.setOnConflict(onConflictAction, onConflictActionIsPersistent);
        }

        try {
            if (onConflict != null && onConflict.equals("skip") && this.getAttemptCounter() > 0) {
                throw new RuntimeException("Ignore this node!");
            }

            this.incrementAttemptCounter();
            this.currentTransferReadyStorageNode.setNewStorageNode(this.storageTreeExecute().commitStorageTreeNode(currentTarget, currentDestination, this.persistOriginal, onConflict));
            exitMessage = "%s '%s' transfered to '%s' successfully!".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL(), currentDestination.getOnDiskURL());

            if (this.getAttemptCounter() > 1) {
                exitMessage.concat(" (Attempt %s)".formatted(this.getAttemptCounter().toString()));
            }

            this.setExitStatus(HttpStatus.OK);
            this.setExitMessage(exitMessage);
            this.logMessage(exitMessage);
        } catch (Exception exception) {
            if (onConflict != null && onConflict.equals("skip")) {
                exitMessage = "%s '%s' transfer to '%s' directory skipped!".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL(), currentDestination.getOnDiskURL());

                if (this.getAttemptCounter() > 1) {
                    exitMessage.concat(" (Attempt %s)".formatted(this.getAttemptCounter().toString()));
                }

                this.setExitStatus(HttpStatus.OK);
                this.setExitMessage(exitMessage);
                this.logMessage(exitMessage);
            } else {
                exitMessage = "%s '%s' could not be transfered to '%s' directory: %s".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL(), currentDestination.getOnDiskURL(), exception.getMessage());
                this.setExitStatus(HttpStatus.UNPROCESSABLE_ENTITY);
                this.setExitMessage(exitMessage);
                this.logMessage(exitMessage);
                return; /* Failure!!! */
            }
        }

        if (!this.onConflictIsPersistent()) {
            this.resetOnConflict();
        }

        this.resetAttemptCounter();
        this.advanceTaskForward();
    }

    @Override
    protected void advanceTaskForward() {
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
