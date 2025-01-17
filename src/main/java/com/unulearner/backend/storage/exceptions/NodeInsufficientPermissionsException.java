package com.unulearner.backend.storage.exceptions;

public class NodeInsufficientPermissionsException extends Exception {
    public NodeInsufficientPermissionsException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public NodeInsufficientPermissionsException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
