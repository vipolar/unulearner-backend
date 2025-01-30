package com.unulearner.backend.storage.exceptions.entry;

import com.unulearner.backend.storage.exceptions.StorageEntryException;

public class EntryTypeInDatabaseMismatchException extends StorageEntryException {
    public EntryTypeInDatabaseMismatchException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public EntryTypeInDatabaseMismatchException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
