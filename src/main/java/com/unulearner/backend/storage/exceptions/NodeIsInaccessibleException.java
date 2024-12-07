package com.unulearner.backend.storage.exceptions;

public class NodeIsInaccessibleException extends Exception {
    public NodeIsInaccessibleException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public NodeIsInaccessibleException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
