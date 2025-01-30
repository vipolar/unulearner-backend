package com.unulearner.backend.storage.exceptions.entry;

import com.unulearner.backend.storage.exceptions.StorageEntryException;

public class EntryNameValidationException extends StorageEntryException {
    public EntryNameValidationException(String exceptionMessage) {
        super(exceptionMessage);
    }
}
