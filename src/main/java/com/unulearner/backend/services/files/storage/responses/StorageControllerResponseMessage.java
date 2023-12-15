package com.unulearner.backend.services.files.storage.responses;

public class StorageControllerResponseMessage {
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public StorageControllerResponseMessage(String message) {
        this.message = message;
    }
}