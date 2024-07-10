package com.unulearner.backend.storage.exceptions;

public class FileTypeNotSupportedException extends Exception {
    public FileTypeNotSupportedException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public FileTypeNotSupportedException(String exceptionMessage, Throwable exceptionRoot) {
        super(exceptionMessage, exceptionRoot);
    }
}
