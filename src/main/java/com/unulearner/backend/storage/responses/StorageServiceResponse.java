package com.unulearner.backend.storage.responses;

import java.util.UUID;
import java.util.Map;

import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption;
import com.unulearner.backend.storage.tasks.StorageTaskBase.StorageTaskAction;

public class StorageServiceResponse {
    private final UUID taskID;
    private final String taskState;
    private final StorageTaskAction action;
    private final Map<String, OnExceptionOption> options;

    public UUID getTaskID() {
        return this.taskID;
    }

    public String getTaskState() {
        return this.taskState;
    }

    public StorageTaskAction getAction() {
        return this.action;
    }

    public Map<String, OnExceptionOption> getOptions() {
        return this.options;
    }

    public StorageServiceResponse(UUID taskID, String state, StorageTaskAction taskAction, Map<String, OnExceptionOption> taskOptions) {
        this.taskID = taskID;
        this.taskState = state;
        this.action = taskAction;
        this.options = taskOptions;
    }
}
