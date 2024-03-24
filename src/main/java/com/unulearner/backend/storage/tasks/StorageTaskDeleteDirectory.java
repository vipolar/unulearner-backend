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

public class StorageTaskDeleteDirectory extends StorageTask {
    private final Deque<StorageTreeNode> taskNodeDeque;
    
    public StorageTaskDeleteDirectory(@NonNull StorageTree storageTree, @NonNull StorageTreeNode targetNode) {
        super(storageTree, targetNode);

        this.taskNodeDeque = this.buildTaskDeque(targetNode, new ArrayDeque<>());
        this.setOnConflictOptions(new ArrayList<>(Arrays.asList(
            new Option("skip", "Skip", true, true)
        )));

        final String exitMessage = "Directory removal task initiated on %s!".formatted(Instant.now().toString());
        this.setExitStatus(HttpStatus.ACCEPTED);
        this.setExitMessage(exitMessage);
        this.logMessage(exitMessage);
        this.advanceTaskForward();
    }

    protected Deque<StorageTreeNode> buildTaskDeque(StorageTreeNode currentNode, Deque<StorageTreeNode> nodeDeque) {
        nodeDeque.offer(currentNode);

        if (currentNode.getChildren() != null && !currentNode.getChildren().isEmpty()) {
            for (StorageTreeNode childNode : currentNode.getChildren()) {
                this.buildTaskDeque(childNode, nodeDeque);
            }
        }

        return nodeDeque;
    }

    @Override
    protected void advanceTaskForward() {
        this.setCurrentTarget(this.taskNodeDeque.pollLast());
    }

    @Override
    public synchronized void run(String onConflictAction, Boolean onConflictActionIsPersistent, Boolean cancelTaskExecution) {
        String exitMessage = null;

        if (cancelTaskExecution != null && cancelTaskExecution == true) {
            exitMessage = "Directory removal task cancelled on %s!".formatted(Instant.now().toString());
            this.setExitStatus(HttpStatus.OK);
            this.setExitMessage(exitMessage);
            this.logMessage(exitMessage);
            this.setTaskAsDone();
            return;
        }

        final StorageTreeNode currentTarget = this.getCurrentTarget();
        if (currentTarget == null) {
            exitMessage = "Directory removal task finished successfully on %s!".formatted(Instant.now().toString());
            this.setExitStatus(HttpStatus.OK);
            this.setExitMessage(exitMessage);
            this.logMessage(exitMessage);
            this.setTaskAsDone();
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
            this.storageTreeExecute().removeStorageTreeNode(currentTarget);
            exitMessage = "%s '%s' removed successfully!".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL());

            if (this.getAttemptCounter() > 1) {
                exitMessage.concat(" (Attempt %s)".formatted(this.getAttemptCounter().toString()));
            }

            this.setExitStatus(HttpStatus.OK);
            this.setExitMessage(exitMessage);
            this.logMessage(exitMessage);
        } catch (Exception exception) {
            if (onConflict != null && onConflict.equals("skip")) {
                exitMessage = "%s '%s' skipped!".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL());

                if (this.getAttemptCounter() > 1) {
                    exitMessage.concat(" (Attempt %s)".formatted(this.getAttemptCounter().toString()));
                }

                this.setExitStatus(HttpStatus.OK);
                this.setExitMessage(exitMessage);
                this.logMessage(exitMessage);
            } else {
                exitMessage = "%s '%s' could not be removed: %s".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL(), exception.getMessage());
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
}
