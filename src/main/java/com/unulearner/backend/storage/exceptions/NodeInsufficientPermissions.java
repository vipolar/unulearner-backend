package com.unulearner.backend.storage.exceptions;

public class NodeInsufficientPermissions extends Exception {
    public NodeInsufficientPermissions(String exceptionMessage) {
        super(exceptionMessage);
    }

    public NodeInsufficientPermissions(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
    
}
