package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.nio.file.FileAlreadyExistsException;
import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.statics.StateCode;
import com.unulearner.backend.storage.statics.TaskOptions;
import com.unulearner.backend.storage.statics.TaskOptions.Option;
import com.unulearner.backend.storage.repository.StorageTasksMap;

public class StorageTaskTransferFile extends StorageTask {
    private final Boolean persistOriginal;

    public StorageTaskTransferFile(StorageTree storageTree, StorageTreeNode targetNode, StorageTreeNode destinationNode, Boolean persistOriginal, StorageTasksMap storageTasksMap) {
        super(storageTree, targetNode, destinationNode, storageTasksMap);

        this.persistOriginal = persistOriginal;
        this.setCurrentDestination(destinationNode);
        this.setCurrentTarget(targetNode);
        
        this.setTaskHeading("%s %s to %s".formatted(this.persistOriginal ? "Copy" : "Move", this.getRootTarget().getOnDiskURL(), this.getRootDestination().getOnDiskURL()));
        this.setTaskCurrentState("File %s task was initiated...".formatted(this.persistOriginal ? "copy" : "move"), null, StateCode.RUNNING);
    }

    @Override
    public synchronized void executeTask(Boolean skipOnException, Boolean skipOnExceptionIsPersistent, String onExceptionAction, Boolean onExceptionActionIsPersistent, Boolean cancelTaskExecution) {
        final StorageTreeNode currentTarget = this.getCurrentTarget();
        final StorageTreeNode currentDestination = this.getCurrentDestination();

        if (currentTarget == null) {
            this.setTaskCurrentState("File %s task finished successfully!".formatted(this.persistOriginal ? "copy" : "move"), null, StateCode.COMPLETED);
            return;
        }

        if (cancelTaskExecution != null && cancelTaskExecution == true) {
            this.setTaskCurrentState("File %s task was cancelled...".formatted(this.persistOriginal ? "copy" : "move"), null, StateCode.CANCELLED);
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
                this.setTaskCurrentState("File '%s' %s to directory '%s' was skipped...".formatted(currentTarget.getOnDiskURL(), this.persistOriginal ? "copy" : "move", currentDestination.getOnDiskURL()), null, StateCode.RUNNING);
                this.advanceTask();
                return;
            }
        }

        try {
            this.incrementAttemptCounter();
            this.storageTreeExecute().commitStorageTreeNode(currentTarget, currentDestination, this.persistOriginal, null, this.getOnExceptionAction(currentTarget));
            this.setTaskCurrentState("File '%s' was %s to directory '%s' successfully!".formatted(currentTarget.getOnDiskURL(), this.persistOriginal ? "copied" : "moved", currentDestination.getOnDiskURL()), null, StateCode.RUNNING);
            this.advanceTask();
            return;
        } catch (Exception exception) {
            if (this.getSkipOnException(currentTarget) == true && this.getAttemptCounter() > 0) {
                this.setTaskCurrentState("File '%s' %s to directory '%s' was skipped automatically...".formatted(currentTarget.getOnDiskURL(), this.persistOriginal ? "copy" : "move", currentDestination.getOnDiskURL()), null, StateCode.RUNNING);
                this.advanceTask();
                return;
            }

            if (this.getAttemptCounter() > 3) {
                this.setTaskCurrentState("File '%s' could not be %s to directory '%s' - %s".formatted(currentTarget.getOnDiskURL(), this.persistOriginal ? "copied" : "moved", currentDestination.getOnDiskURL(), exception.getMessage()), null, StateCode.ERROR);
                return; /* Absolute failure!!! */
            }

            final List<Option> onExceptionActions;
            if (exception instanceof FileAlreadyExistsException) {
                onExceptionActions = (new ArrayList<>(Arrays.asList(
                    new Option("overwrite", "Overwrite"),
                    new Option("rename", "Rename")
                )));
            } else {
                onExceptionActions = null;
            }

            final TaskOptions onExceptionOptions = new TaskOptions(false, false, onExceptionActions);
            this.setTaskCurrentState("File '%s' could not be %s to directory '%s' - %s".formatted(currentTarget.getOnDiskURL(), this.persistOriginal ? "copied" : "moved", currentDestination.getOnDiskURL(), exception.getMessage()), onExceptionOptions, StateCode.EXCEPTION);
            return;
        }
    }

    @Override
    protected void advanceTask() {
        this.setCurrentDestination(null);
        this.setCurrentTarget(null);
        return;
    }
}
