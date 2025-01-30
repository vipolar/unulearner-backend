package com.unulearner.backend.storage.exceptions.entry;

import com.unulearner.backend.storage.exceptions.StorageEntryException;

public class EntryPhysicalCreationException extends StorageEntryException {
    public EntryPhysicalCreationException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public EntryPhysicalCreationException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}