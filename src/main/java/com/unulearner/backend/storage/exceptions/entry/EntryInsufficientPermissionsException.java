package com.unulearner.backend.storage.exceptions.entry;

import com.unulearner.backend.storage.exceptions.StorageEntryException;

public class EntryInsufficientPermissionsException extends StorageEntryException {
    public EntryInsufficientPermissionsException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public EntryInsufficientPermissionsException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
