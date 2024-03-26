package com.unulearner.backend.storage.responses;

import java.time.Instant;
import java.util.UUID;

import com.unulearner.backend.storage.statics.StateCode;

public class StorageServiceResponse {
    private final UUID taskID;
    private final String message;
    private final Integer timeLeft;
    private final Instant timeStamp;
    private final String taskHeading;
    private final StateCode taskState;

    public UUID getTaskID() {
        return this.taskID;
    }

    public String getMessage() {
        return this.message;
    }

    public Integer getTimeLeft() {
        return this.timeLeft;
    }

    public Instant getTimeStamp() {
        return this.timeStamp;
    }

    public String getTaskHeading() {
        return this.taskHeading;
    }

    public StateCode getTaskState() {
        return this.taskState;
    }

    //TODO: remove the whole thing so this is removed!!!
    public StorageServiceResponse(String message) {
        this.taskID = null;
        this.message = message;
        this.timeLeft = null;
        this.taskState = null;
        this.timeStamp = null;
        this.taskHeading = null;
    }

    public StorageServiceResponse(UUID taskID, String taskHeading, String message, Integer timeLeft, StateCode taskState) {
        this.taskID = taskID;
        this.message = message;
        this.timeLeft = timeLeft;
        this.taskState = taskState;
        this.timeStamp = Instant.now();
        this.taskHeading = taskHeading;
    }
}
