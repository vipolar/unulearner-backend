package com.unulearner.backend.storage.tasks;

import java.util.ArrayDeque;
import java.util.Deque;

import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.statics.StateCode;
import com.unulearner.backend.storage.statics.TaskOptions;
import com.unulearner.backend.storage.repository.StorageTasksMap;


public class StorageTaskDeleteDirectory extends StorageTask {
    private final Deque<StorageTreeNode> taskNodeDeque;
    
    public StorageTaskDeleteDirectory(StorageTree storageTree, StorageTreeNode targetNode, StorageTreeNode destinationNode, StorageTasksMap storageTasksMap) {
        super(storageTree, targetNode, destinationNode, storageTasksMap);

        this.taskNodeDeque = new ArrayDeque<>();
        this.buildTaskDeque(targetNode);
        this.advanceTask();

        this.setTaskHeading("Remove %s from disk".formatted(this.getRootTarget().getOnDiskURL()));
        this.setTaskCurrentState("Directory removal task was initiated...".formatted(), null, StateCode.RUNNING);
    }

    @Override
    public synchronized void executeTask(Boolean skipOnException, Boolean skipOnExceptionIsPersistent, String onExceptionAction, Boolean onExceptionActionIsPersistent, Boolean cancelTaskExecution) {
        final StorageTreeNode currentTarget = this.getCurrentTarget();

        if (currentTarget == null) {
            this.setTaskCurrentState("Directory removal task finished successfully!".formatted(), null, StateCode.COMPLETED);
            return;
        }

        if (cancelTaskExecution != null && cancelTaskExecution == true) {
            this.setTaskCurrentState("Directory removal task was cancelled...".formatted(), null, StateCode.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            onExceptionActionIsPersistent = onExceptionActionIsPersistent != null ? onExceptionActionIsPersistent : false;
            this.setOnExceptionAction(currentTarget, onExceptionAction, onExceptionActionIsPersistent);
        }

        if (skipOnException != null) {
            skipOnExceptionIsPersistent = skipOnExceptionIsPersistent != null ? skipOnExceptionIsPersistent : false;
            this.setSkipOnException(currentTarget, skipOnException, skipOnExceptionIsPersistent);

            if (this.getSkipOnException(currentTarget) == true && this.getAttemptCounter() > 0) {
                this.setTaskCurrentState("%s '%s' removal was was skipped...".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL()), null, StateCode.RUNNING);
                this.advanceTask();
                return;
            }
        }

        try {
            this.incrementAttemptCounter();
            this.storageTreeExecute().removeStorageTreeNode(currentTarget);
            this.setTaskCurrentState("%s '%s' was removed successfully!".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL()), null, StateCode.RUNNING);
            this.advanceTask();
            return;
        } catch (Exception exception) {
            if (this.getSkipOnException(currentTarget) == true && this.getAttemptCounter() > 0) {
                this.setTaskCurrentState("%s '%s' removal was was skipped automatically...".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL()), null, StateCode.RUNNING);
                this.advanceTask();
                return;
            }

            if (this.getAttemptCounter() > 3) {
                this.setTaskCurrentState("%s '%s' could not be removed - %s".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL(), exception.getMessage()), null, StateCode.ERROR);
                return; /* Absolute failure!!! */
            }

            final TaskOptions onExceptionOptions = new TaskOptions(false, false, null);
            this.setTaskCurrentState("%s '%s' could not be removed - %s".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL(), exception.getMessage()), onExceptionOptions, StateCode.EXCEPTION);
            return;
        }
    }

    @Override
    protected void advanceTask() {
        final StorageTreeNode currentTarget = this.getCurrentTarget();

        if (currentTarget != null) {
            this.resetOnExceptionAction(currentTarget);
            this.resetSkipOnException(currentTarget);
            this.resetAttemptCounter();
        }

        this.setCurrentTarget(this.taskNodeDeque.pollLast());
    }

    private void buildTaskDeque(StorageTreeNode currentNode) {
        this.taskNodeDeque.offer(currentNode);

        if (currentNode.getChildren() != null && !currentNode.getChildren().isEmpty()) {
            for (StorageTreeNode childNode : currentNode.getChildren()) {
                this.buildTaskDeque(childNode);
            }
        }
    }
}
