package com.unulearner.backend.storage.exceptions.entry;

import com.unulearner.backend.storage.exceptions.StorageEntryException;

public class EntryTypeNotSupportedException extends StorageEntryException {
    public EntryTypeNotSupportedException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public EntryTypeNotSupportedException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
