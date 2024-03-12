package com.unulearner.backend.storage.responses;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.unulearner.backend.storage.StorageTreeNode;

public class StorageServiceResponse {
    private UUID taskUUID;
    private String message;
    private List<String> log;
    private HttpStatus status;
    private StorageTreeNode node;
    private Integer timeToRespond;
    private List<String> options;

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

    public HttpStatus getStatus() {
        return status;
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

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public Integer getTimeToRespond() {
        return timeToRespond;
    }

    public void setTimeToRespond(Integer timeToRespond) {
        this.timeToRespond = timeToRespond;
    }

    //TODO: remove the whole thing so this is removed!!!
    public StorageServiceResponse(String message) {
        this.message = message;
    }

    public StorageServiceResponse(String message, HttpStatus status, StorageTreeNode node, UUID taskUUID, List<String> log, List<String> options, Integer time) {
        this.timeToRespond = time;
        this.taskUUID = taskUUID;
        this.options = options;
        this.message = message;
        this.status = status;
        this.node = node;
        this.log = log;
    }
}