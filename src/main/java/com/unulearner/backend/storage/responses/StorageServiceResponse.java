package com.unulearner.backend.storage.responses;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;

import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.tasks.StorageTask.Option;

public class StorageServiceResponse {
    private UUID taskUUID;
    private String message;
    private List<String> log;
    private HttpStatus status;
    private StorageTreeNode node;
    private Integer timeToRespond;
    private List<Option> onConflict;

    public UUID getTaskUUID() {
        return taskUUID;
    }

    public void setTaskUUID(UUID taskUUID) {
        this.taskUUID = taskUUID;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getLog() {
        return log;
    }

    public void setLog(List<String> log) {
        this.log = log;
    }

    public @NonNull HttpStatus getStatus() {
        if (this.status != null) {
            return this.status;
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public void setStatus(HttpStatus status) {
        this.status = status;
    }

    public StorageTreeNode getNode() {
        return node;
    }

    public void setNode(StorageTreeNode node) {
        this.node = node;
    }

    public Integer getTimeToRespond() {
        return timeToRespond;
    }

    public void setTimeToRespond(Integer timeToRespond) {
        this.timeToRespond = timeToRespond;
    }

    public List<Option> getOnConflict() {
        return onConflict;
    }

    public void setOnConflict(List<Option> onConflict) {
        this.onConflict = onConflict;
    }

    //TODO: remove the whole thing so this is removed!!!
    public StorageServiceResponse(String message) {
        this.message = message;
    }

    public StorageServiceResponse(UUID taskUUID, Integer time, List<String> log, HttpStatus status, String message, StorageTreeNode node,  List<Option> onConflict) {
        this.log = log;
        this.node = node;
        this.status = status;
        this.message = message;
        this.taskUUID = taskUUID;
        this.timeToRespond = time;
        this.onConflict = onConflict;
    }
}