package com.unulearner.backend.storage.exceptions;

public class NodeTypeNotSupportedException extends Exception {
    public NodeTypeNotSupportedException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public NodeTypeNotSupportedException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
