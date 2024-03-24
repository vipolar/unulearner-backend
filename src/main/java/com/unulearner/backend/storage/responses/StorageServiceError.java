package com.unulearner.backend.storage.responses;

public class StorageServiceError {
    private String message;

    public StorageServiceError(String message) {
        this.message = message;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
