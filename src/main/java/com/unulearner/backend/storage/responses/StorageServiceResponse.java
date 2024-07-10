package com.unulearner.backend.storage.responses;

import java.util.UUID;

import com.unulearner.backend.storage.tasks.StorageTaskBase.StorageTaskAction;

public class StorageServiceResponse {
    private final UUID taskID;
    private final String taskState;
    private final StorageTaskAction action;

    public UUID getTaskID() {
        return this.taskID;
    }

    public String getTaskState() {
        return this.taskState;
    }

    public StorageTaskAction getAction() {
        return this.action;
    }

    public StorageServiceResponse(UUID taskID, String state, StorageTaskAction taskAction) {
        this.taskID = taskID;
        this.taskState = state;
        this.action = taskAction;
    }
}
