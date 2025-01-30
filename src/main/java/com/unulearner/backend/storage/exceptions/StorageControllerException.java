package com.unulearner.backend.storage.exceptions;

public class StorageControllerException extends Exception {
    public StorageControllerException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public StorageControllerException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
