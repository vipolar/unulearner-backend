package com.unulearner.backend.storage.exceptions.entry;

import com.unulearner.backend.storage.exceptions.StorageEntryException;

public class EntryInaccessibleException extends StorageEntryException {
    public EntryInaccessibleException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public EntryInaccessibleException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
