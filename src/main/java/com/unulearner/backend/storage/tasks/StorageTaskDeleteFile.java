package com.unulearner.backend.storage.tasks;

import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.statics.StateCode;
import com.unulearner.backend.storage.statics.TaskOptions;
import com.unulearner.backend.storage.repository.StorageTasksMap;

public class StorageTaskDeleteFile extends StorageTask {

    public StorageTaskDeleteFile(StorageTree storageTree, StorageTreeNode targetNode, StorageTreeNode destinationNode, StorageTasksMap storageTasksMap) {
        super(storageTree, targetNode, destinationNode, storageTasksMap);
        
        this.setCurrentTarget(targetNode);

        this.setTaskHeading("Remove %s from disk".formatted(this.getRootTarget().getOnDiskURL()));
        this.setTaskCurrentState("File removal task was initiated...".formatted(), null, StateCode.RUNNING);
    }

    @Override
    public synchronized void executeTask(Boolean skipOnException, Boolean skipOnExceptionIsPersistent, String onExceptionAction, Boolean onExceptionActionIsPersistent, Boolean cancelTaskExecution) {
        final StorageTreeNode currentTarget = this.getCurrentTarget();

        if (currentTarget == null) {
            this.setTaskCurrentState("File removal task finished successfully!".formatted(), null, StateCode.COMPLETED);
            return;
        }

        if (cancelTaskExecution != null && cancelTaskExecution == true) {
            this.setTaskCurrentState("File removal task was cancelled...".formatted(), null, StateCode.CANCELLED);
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
                this.setTaskCurrentState("File '%s' removal was was skipped...".formatted(currentTarget.getOnDiskURL()), null, StateCode.RUNNING);
                this.advanceTask();
                return;
            }
        }

        try {
            this.incrementAttemptCounter();
            this.storageTreeExecute().removeStorageTreeNode(currentTarget);
            this.setTaskCurrentState("File '%s' was removed successfully!".formatted(currentTarget.getOnDiskURL()), null, StateCode.RUNNING);
            this.advanceTask();
            return;
        } catch (Exception exception) {
            if (this.getSkipOnException(currentTarget) == true && this.getAttemptCounter() > 0) {
                this.setTaskCurrentState("File '%s' removal was was skipped automatically...".formatted(currentTarget.getOnDiskURL()), null, StateCode.RUNNING);
                this.advanceTask();
                return;
            }

            if (this.getAttemptCounter() > 3) {
                this.setTaskCurrentState("File '%s' could not be removed - %s".formatted(currentTarget.getOnDiskURL(), exception.getMessage()), null, StateCode.ERROR);
                return; /* Absolute failure!!! */
            }

            final TaskOptions onExceptionOptions = new TaskOptions(false, false, null);
            this.setTaskCurrentState("File '%s' could not be removed - %s".formatted(currentTarget.getOnDiskURL(), exception.getMessage()), onExceptionOptions, StateCode.EXCEPTION);
            return;
        }
    }

    @Override
    protected void advanceTask() {
        this.setCurrentTarget(null);
        return;
    }
}
