package com.unulearner.backend.storage.exceptions.entry;

import com.unulearner.backend.storage.exceptions.StorageEntryException;

public class EntryNameGenerationException extends StorageEntryException {
    public EntryNameGenerationException(String exceptionMessage) {
        super(exceptionMessage);
    }
}
