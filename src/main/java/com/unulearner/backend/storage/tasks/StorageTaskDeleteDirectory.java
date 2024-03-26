package com.unulearner.backend.storage.tasks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;

import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.exceptions.StorageServiceException;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.statics.StateCode;

public class StorageTaskDeleteDirectory extends StorageTask {
    private final Deque<StorageTreeNode> taskNodeDeque;
    
    public StorageTaskDeleteDirectory(StorageTree storageTree, StorageTreeNode targetNode, StorageTreeNode destinationNode, StorageTasksMap storageTasksMap) {
        super(storageTree, targetNode, destinationNode, storageTasksMap);

        this.taskNodeDeque = new ArrayDeque<>();
        this.buildTaskDeque(targetNode);
        this.advanceTask();

        this.setOnExceptionOptions(new ArrayList<>(Arrays.asList(
            new Option("skip", "Skip", true, true)
        )));

        this.setTaskHeading("Remove %s from disk".formatted(this.getRootTarget().getOnDiskURL()));
        this.setTaskCurrentState("Directory removal task was initiated...".formatted(), StateCode.RUNNING);
    }

    @Override
    public synchronized void executeTask(String newOnExceptionAction, Boolean newOnExceptionActionIsPersistent, Boolean cancelTaskExecution) {
        final StorageTreeNode currentTarget = this.getCurrentTarget();

        if (currentTarget == null) {
            this.setTaskCurrentState("Directory removal task finished successfully!".formatted(), StateCode.COMPLETED);
            return;
        }

        if (cancelTaskExecution != null && cancelTaskExecution == true) {
            this.setTaskCurrentState("Directory removal task was cancelled...".formatted(), StateCode.CANCELLED);
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
                this.setTaskCurrentState("%s '%s' removal was skipped...".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL()), StateCode.RUNNING);
                this.advanceTask();
                return;
            }

            this.incrementAttemptCounter();
            this.storageTreeExecute().removeStorageTreeNode(currentTarget);
            this.setTaskCurrentState("%s '%s' was removed successfully!".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL()), StateCode.RUNNING);
            this.advanceTask();
            return;
        } catch (StorageServiceException exception) {
            this.setTaskCurrentState("%s '%s' could not be removed - %s".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL(), exception.getMessage()), StateCode.EXCEPTION);
            return;
        } catch (Exception exception) {
            this.setTaskCurrentState("%s '%s' could not be removed - %s".formatted(currentTarget.getChildren() != null ? "Directory" : "File", currentTarget.getOnDiskURL(), exception.getMessage()), this.getAttemptCounter() > 3 ? StateCode.ERROR : StateCode.EXCEPTION);
            return;
        }
    }

    @Override
    protected void advanceTask() {
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
