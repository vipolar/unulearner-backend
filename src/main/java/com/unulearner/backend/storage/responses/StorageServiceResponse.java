package com.unulearner.backend.storage.responses;

import java.util.List;
import java.util.UUID;

import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption;
import com.unulearner.backend.storage.tasks.StorageTaskBase.StorageTaskAction;

public class StorageServiceResponse {
    private final UUID taskID;
    private final String taskState;
    private final StorageTaskAction action;
    private final List<OnExceptionOption> options;

    public UUID getTaskID() {
        return this.taskID;
    }

    public String getTaskState() {
        return this.taskState;
    }

    public StorageTaskAction getAction() {
        return this.action;
    }

    public List<OnExceptionOption> getOptions() {
        return this.options;
    }

    public StorageServiceResponse(UUID taskID, String state, StorageTaskAction taskAction, List<OnExceptionOption> taskOptions) {
        this.taskID = taskID;
        this.taskState = state;
        this.action = taskAction;
        this.options = taskOptions;
    }
}
