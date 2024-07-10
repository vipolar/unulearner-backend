package com.unulearner.backend.storage.exceptions;

public class FileIsInaccessibleException extends Exception {
    public FileIsInaccessibleException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public FileIsInaccessibleException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
