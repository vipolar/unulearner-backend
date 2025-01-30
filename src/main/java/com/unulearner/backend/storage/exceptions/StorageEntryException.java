package com.unulearner.backend.storage.exceptions;

public class StorageEntryException extends Exception {
    public StorageEntryException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public StorageEntryException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
