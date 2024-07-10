package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.util.List;

import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption;

import java.io.IOException;

public class StorageTaskDestroyNode extends StorageTaskBaseBatch {
    private final StorageNode rootTargetStorageTreeNode;

    public StorageTaskDestroyNode(StorageTree storageTree, StorageNode targetStorageTreeNode, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        this.rootTargetStorageTreeNode = targetStorageTreeNode;

        final StorageTaskDestroyNodeCurrentAction storageTaskAction = new StorageTaskDestroyNodeCurrentAction(null, targetStorageTreeNode);

        storageTaskAction.setActionHeader("Remove %s '%s' permanently".formatted(this.rootTargetStorageTreeNode.isDirectory() ? "directory" : "file", this.rootTargetStorageTreeNode.getOnDiskURL()));
        storageTaskAction.setMessage("%s removal task has been successfully initialized".formatted(this.rootTargetStorageTreeNode.isDirectory() ? "Directory" : "File"));

        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    @Override
    public synchronized void execute(String updatedName, String onExceptionAction, Boolean onExceptionActionIsPersistent) {
        final StorageTaskDestroyNodeCurrentAction storageTaskCurrentAction = (StorageTaskDestroyNodeCurrentAction) this.getStorageTaskAction();

        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getTargetStorageTreeNode().getId() == null) {
            storageTaskCurrentAction.setMessage("%s '%s' removal task finished successfully!".formatted(this.rootTargetStorageTreeNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageTreeNode.getOnDiskURL()));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (onExceptionAction != null && onExceptionAction.equals("cancel")) {
            storageTaskCurrentAction.setMessage("%s '%s' removal task was cancelled...".formatted(this.rootTargetStorageTreeNode.isDirectory() ? "Directory" : "File", this.rootTargetStorageTreeNode.getOnDiskURL()));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            onExceptionActionIsPersistent = onExceptionActionIsPersistent != null ? onExceptionActionIsPersistent : false;
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetStorageTreeNode(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        Integer IOAttempts = 0;
        storageTaskCurrentAction.incrementAttemptCounter();
        while (storageTaskCurrentAction.getTargetStorageTreeNode().getId() != null) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(exceptionType, storageTaskCurrentAction.getTargetStorageTreeNode());

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' removal was skipped...".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onPhysicalOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};
        
                                    storageTaskCurrentAction.setMessage("%s '%s' could not be removed due to a persistent I/O exception occurring".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onPhysicalOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }  
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' removal was skipped...".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.advanceStorageTask();
                                    return;
                                default:
                                    List<OnExceptionOption> onGeneralOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};
        
                                    storageTaskCurrentAction.setMessage("%s '%s' could not be removed due to an unexpected exception occurring".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onGeneralOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }  
                    }
                }

                /* TODO: better checks (present ones are sufficient for the current implementations) */
                if (storageTaskCurrentAction.getTargetStorageTreeNode().getIsAccessible() || storageTaskCurrentAction.getTargetStorageTreeNode().getIsConfirmed()) {
                    storageTaskCurrentAction.setTargetStorageTreeNode(this.storageTreeExecute().deleteStorageTreeNode(storageTaskCurrentAction.getTargetStorageTreeNode()));
                }

                storageTaskCurrentAction.setMessage("%s '%s' has been permanently removed from storage successfully.".formatted(storageTaskCurrentAction.getTargetStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageTreeNode().getOnDiskURL()));
                storageTaskCurrentAction.setExceptionMessage(null);
                storageTaskCurrentAction.setExceptionType(null);

                this.setCurrentState(TaskState.EXECUTING);
                this.advanceStorageTask();
                return;
            } catch (IOException exception) {
                if (IOAttempts++ < 3) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException sleepException) {
                        storageTaskCurrentAction.setExceptionType(sleepException.getClass().getSimpleName());
                        storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                        continue;
                    }
                }

                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            } catch (Exception exception) {
                storageTaskCurrentAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
            }
        }
    }

    @Override
    protected void advanceStorageTask() {
        StorageTaskDestroyNodeCurrentAction storageTaskCurrentAction = (StorageTaskDestroyNodeCurrentAction) this.getStorageTaskAction();

        while (!storageTaskCurrentAction.getChildStorageTaskActions().hasNext()) {
            if (storageTaskCurrentAction.getParentStorageTaskAction() == null) {
                break;
            }

            storageTaskCurrentAction = (StorageTaskDestroyNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction();
        }

        if (storageTaskCurrentAction.getChildStorageTaskActions().hasNext()) {
            storageTaskCurrentAction = (StorageTaskDestroyNodeCurrentAction) storageTaskCurrentAction.getChildStorageTaskActions().next();
        }

        this.setStorageTaskAction(storageTaskCurrentAction);
        this.getTaskExceptionHandler().resetOnExceptionAction();
    }

    protected class StorageTaskDestroyNodeCurrentAction extends StorageTaskCurrentAction {
        private StorageNode targetStorageTreeNode;

        protected StorageTaskDestroyNodeCurrentAction(StorageTaskDestroyNodeCurrentAction parentStorageTaskAction, StorageNode targetStorageTreeNode) {
            super(parentStorageTaskAction);

            this.targetStorageTreeNode = targetStorageTreeNode;

            if (this.targetStorageTreeNode.isDirectory()) {
                for (StorageNode childNode : targetStorageTreeNode.getChildren()) {
                    this.getChildStorageTaskActions().add(new StorageTaskDestroyNodeCurrentAction(this, childNode));
                }
            }
        }

        public StorageNode getTargetStorageTreeNode() {
            return this.targetStorageTreeNode;
        }
        
        protected void setTargetStorageTreeNode(StorageNode targetStorageTreeNode) {
            this.targetStorageTreeNode = targetStorageTreeNode;
        }
    }
}
