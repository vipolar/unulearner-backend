package com.unulearner.backend.storage.exceptions.entry;

import com.unulearner.backend.storage.exceptions.StorageEntryException;

public class EntryNotFoundException extends StorageEntryException {
    public EntryNotFoundException(String exceptionMessage) {
        super(exceptionMessage);
    }
}
