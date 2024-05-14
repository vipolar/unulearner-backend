package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.nio.file.FileAlreadyExistsException;
import java.io.IOException;
import java.nio.file.Files;

import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.statics.StateCode;
import com.unulearner.backend.storage.statics.TaskOptions;
import com.unulearner.backend.storage.statics.TaskOptions.Option;
import com.unulearner.backend.storage.repository.StorageTasksMap;

public class StorageTaskUpdateFile extends StorageTask {
    final String updatedName;
    final String updatedDescription;

    public StorageTaskUpdateFile(StorageTree storageTree, String updatedName, String updatedDescription, StorageTreeNode targetNode, StorageTreeNode destinationNode, StorageTasksMap storageTasksMap) {
        super(storageTree, targetNode, destinationNode, storageTasksMap);

        this.updatedName = updatedName != null && !targetNode.getOnDiskName().equals(updatedName) ? updatedName : null;
        this.updatedDescription = updatedDescription != null && !targetNode.getDescription().equals(updatedDescription) ? updatedDescription : null;

        this.setCurrentDestination(destinationNode);
        this.setCurrentTarget(targetNode);
        
        this.setTaskHeading("Update %s metadata".formatted(this.getRootTarget().getOnDiskURL()));
        this.setTaskCurrentState("File metadata update task was initiated...".formatted(), null, StateCode.RUNNING);
    }

    @Override
    public synchronized void executeTask(Boolean skipOnException, Boolean skipOnExceptionIsPersistent, String onExceptionAction, Boolean onExceptionActionIsPersistent, Boolean cancelTaskExecution) {
        final StorageTreeNode currentTarget = this.getCurrentTarget();
        final StorageTreeNode currentDestination = this.getCurrentDestination();

        if (currentTarget == null) {
            this.setTaskCurrentState("File metadata update task finished successfully!".formatted(), null, StateCode.COMPLETED);
            this.cleanTaskUp();
            return;
        }

        if (cancelTaskExecution != null && cancelTaskExecution == true) {
            this.setTaskCurrentState("File metadata update task was cancelled...".formatted(), null, StateCode.CANCELLED);
            this.cleanTaskUp();
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
                this.setTaskCurrentState("File '%s' upload to directory '%s' was skipped...".formatted(currentTarget.getOnDiskName(), currentDestination.getOnDiskURL()), null, StateCode.RUNNING);
                this.cleanTaskUp();
                this.advanceTask();
                return;
            }
        }

        if (this.updatedDescription != null) { //TODO: look at hten remove!
            this.getCurrentTarget().setDescription(updatedDescription);
            this.storageTreeExecute().saveToRepository(this.getCurrentTarget());
        }

        try {
            this.incrementAttemptCounter();

            if (this.updatedName != null && this.updatedDescription != null) {
                //Do the whole thing
            } else if (this.updatedName != null) {
                //Just move
            } else {
                //just desctiption...
            }
            this.storageTreeExecute().commitStorageTreeNode(currentTarget, currentDestination, false, null, this.getOnExceptionAction(currentTarget));
            this.setTaskCurrentState("File '%s' was uploaded to directory '%s' successfully!".formatted(currentTarget.getOnDiskName(), currentDestination.getOnDiskURL()), null, StateCode.RUNNING);
            this.cleanTaskUp();
            this.advanceTask();
            return;
        } catch (Exception exception) {
            if (this.getSkipOnException(currentTarget) == true && this.getAttemptCounter() > 0) {
                this.setTaskCurrentState("File '%s' upload to directory '%s' was skipped automatically...".formatted(currentTarget.getOnDiskName(), currentDestination.getOnDiskURL()), null, StateCode.RUNNING);
                this.cleanTaskUp();
                this.advanceTask();
                return;
            }

            if (this.getAttemptCounter() > 3) {
                this.setTaskCurrentState("File '%s' could not be uploaded to directory '%s' - %s".formatted(currentTarget.getOnDiskName(), currentDestination.getOnDiskURL(), exception.getMessage()), null, StateCode.ERROR);
                this.cleanTaskUp();
                return; /* Absolute failure!!! */
            }

            final List<Option> onExceptionActions;
            if (exception instanceof FileAlreadyExistsException) {
                onExceptionActions = (new ArrayList<>(Arrays.asList(
                    new Option("overwrite", "Overwrite"),
                    new Option("rename", "Rename") //TODO: add "keep" option!
                )));
            } else {
                onExceptionActions = null;
            }

            final TaskOptions onExceptionOptions = new TaskOptions(false, false, onExceptionActions);
            this.setTaskCurrentState("File '%s' could not be uploaded to directory '%s' - %s".formatted(currentTarget.getOnDiskName(), currentDestination.getOnDiskURL(), exception.getMessage()), onExceptionOptions, StateCode.EXCEPTION);
            this.cleanTaskUp();
            return;
        }
    }

    @Override
    protected void cleanTaskUp() {
        Integer attemptLimit = 10;

        while (attemptLimit-- > 0) {
            try {
                Files.deleteIfExists(this.getCurrentTarget().getAbsolutePath());
                break;
            } catch (IOException exception) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Override
    protected void advanceTask() {
        this.setCurrentDestination(null);
        this.setCurrentTarget(null);
        return;
    }
}
