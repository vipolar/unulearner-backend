package com.unulearner.backend.storage.exceptions;

public class StorageUtilException extends Exception {
    public StorageUtilException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public StorageUtilException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
