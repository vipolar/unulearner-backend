package com.unulearner.backend.storage.exceptions;

public class StorageServiceException extends Exception {
    public StorageServiceException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public StorageServiceException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
