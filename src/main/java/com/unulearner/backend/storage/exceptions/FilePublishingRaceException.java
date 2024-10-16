package com.unulearner.backend.storage.exceptions;

public class FilePublishingRaceException extends Exception {
    public FilePublishingRaceException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public FilePublishingRaceException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
