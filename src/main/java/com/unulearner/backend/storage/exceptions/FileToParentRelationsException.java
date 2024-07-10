package com.unulearner.backend.storage.exceptions;

public class FileToParentRelationsException extends Exception {
    public FileToParentRelationsException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public FileToParentRelationsException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
