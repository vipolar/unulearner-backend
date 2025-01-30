package com.unulearner.backend.storage.exceptions.entry;

import com.unulearner.backend.storage.exceptions.StorageEntryException;

public class EntryPublishingRaceException extends StorageEntryException {
    public EntryPublishingRaceException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public EntryPublishingRaceException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
