package com.unulearner.backend.storage.tasks;

import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Arrays;

import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.exceptions.StorageServiceException;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.statics.StateCode;

public class StorageTaskTransferFile extends StorageTask {
    private final Boolean persistOriginal;

    public StorageTaskTransferFile(StorageTree storageTree, StorageTreeNode targetNode, StorageTreeNode destinationNode, Boolean persistOriginal, StorageTasksMap storageTasksMap) {
        super(storageTree, targetNode, destinationNode, storageTasksMap);

        this.persistOriginal = persistOriginal;
        this.setCurrentDestination(destinationNode);
        this.setCurrentTarget(targetNode);
        this.setOnExceptionOptions(new ArrayList<>(Arrays.asList(
            new Option("overwrite", "Overwrite", true, false),
            new Option("rename", "Rename", true, true),
            new Option("merge", "Merge", false, true),
            new Option("skip", "Skip", true, true)
        )));
        
        this.setTaskHeading("%s %s to %s".formatted(this.persistOriginal ? "Copy" : "Move", this.getRootTarget().getOnDiskURL(), this.getRootDestination().getOnDiskURL()));
        this.setTaskCurrentState("File %s task was initiated...".formatted(this.persistOriginal ? "copy" : "move"), StateCode.RUNNING);
    }

    @Override
    public synchronized void executeTask(String newOnExceptionAction, Boolean newOnExceptionActionIsPersistent, Boolean cancelTaskExecution) {
        final StorageTreeNode currentTarget = this.getCurrentTarget();
        final StorageTreeNode currentDestination = this.getCurrentDestination();

        if (currentTarget == null) {
            this.setTaskCurrentState("File %s task finished successfully!".formatted(this.persistOriginal ? "copy" : "move"), StateCode.COMPLETED);
            return;
        }

        if (currentDestination == null) {
            this.setTaskCurrentState("File %s task, in defiance of all logic, has somehow failed... miserably...".formatted(this.persistOriginal ? "copy" : "move"), StateCode.ERROR);
            return;
        }

        if (cancelTaskExecution != null && cancelTaskExecution == true) {
            this.setTaskCurrentState("File %s task was cancelled...".formatted(this.persistOriginal ? "copy" : "move"), StateCode.CANCELLED);
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
                this.setTaskCurrentState("File '%s' %s to directory '%s' skipped...".formatted(currentTarget.getOnDiskURL(), this.persistOriginal ? "copy" : "move", currentDestination.getOnDiskURL()), StateCode.RUNNING);
                this.advanceTask();
                return;
            }

            this.incrementAttemptCounter();
            this.storageTreeExecute().commitStorageTreeNode(currentTarget, currentDestination, this.persistOriginal, onExceptionAction);
            this.setTaskCurrentState("File '%s' was %s to directory '%s' successfully!".formatted(currentTarget.getOnDiskURL(), this.persistOriginal ? "copied" : "moved", currentDestination.getOnDiskURL()), StateCode.RUNNING);
            this.advanceTask();
            return;
        } catch (FileAlreadyExistsException exception) {
            this.setTaskCurrentState("File '%s' could not be %s to directory '%s' - %s".formatted(currentTarget.getOnDiskURL(), this.persistOriginal ? "copied" : "moved", currentDestination.getOnDiskURL(), exception.getMessage()), StateCode.EXCEPTION);
            return; //TODO: HANDLE EXCEPTIONS BETTER!!!
        } catch (StorageServiceException exception) {
            this.setTaskCurrentState("File '%s' could not be %s to directory '%s' - %s".formatted(currentTarget.getOnDiskURL(), this.persistOriginal ? "copied" : "moved", currentDestination.getOnDiskURL(), exception.getMessage()), StateCode.EXCEPTION);
            return;
        } catch (Exception exception) {
            this.setTaskCurrentState("File '%s' could not be %s to directory '%s' - %s".formatted(currentTarget.getOnDiskURL(), this.persistOriginal ? "copied" : "moved", currentDestination.getOnDiskURL(), exception.getMessage()), this.getAttemptCounter() > 3 ? StateCode.ERROR : StateCode.EXCEPTION);
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
