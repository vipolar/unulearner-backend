package com.unulearner.backend.storage.exceptions;

public class NodeTypeInDatabaseMismatchException extends Exception {
    public NodeTypeInDatabaseMismatchException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public NodeTypeInDatabaseMismatchException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
