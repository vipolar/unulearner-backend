package com.unulearner.backend.storage.responses;

import java.util.ArrayList;
import java.util.UUID;

import com.unulearner.backend.storage.tasks.StorageTaskBase.StorageTaskAction;
import com.unulearner.backend.storage.tasks.exception.Option;

public class StorageServiceResponse {
    private final UUID taskID;
    private final String taskState;
    private final StorageTaskAction action;
    private final ArrayList<Option> options;

    public UUID getTaskID() {
        return this.taskID;
    }

    public String getTaskState() {
        return this.taskState;
    }

    public StorageTaskAction getAction() {
        return this.action;
    }

    public ArrayList<Option> getOptions() {
        return this.options;
    }

    public StorageServiceResponse(UUID taskID, String state, StorageTaskAction taskAction, ArrayList<Option> taskOptions) {
        this.taskID = taskID;
        this.taskState = state;
        this.action = taskAction;
        this.options = taskOptions;
    }
}
