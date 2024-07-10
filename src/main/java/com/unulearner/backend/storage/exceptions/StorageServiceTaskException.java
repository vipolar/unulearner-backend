package com.unulearner.backend.storage.exceptions;

public class StorageServiceTaskException extends Exception {
    public StorageServiceTaskException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public StorageServiceTaskException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
