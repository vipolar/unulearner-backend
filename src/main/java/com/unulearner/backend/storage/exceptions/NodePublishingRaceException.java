package com.unulearner.backend.storage.exceptions;

public class NodePublishingRaceException extends Exception {
    public NodePublishingRaceException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public NodePublishingRaceException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
