package com.unulearner.backend.storage.exceptions.entry;

import com.unulearner.backend.storage.exceptions.StorageEntryException;

public class EntryToParentRelationException extends StorageEntryException {
    public EntryToParentRelationException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public EntryToParentRelationException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
