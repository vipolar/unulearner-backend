package com.unulearner.backend.storage.exceptions;

public class NodeToParentRelationsException extends Exception {
    public NodeToParentRelationsException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public NodeToParentRelationsException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
