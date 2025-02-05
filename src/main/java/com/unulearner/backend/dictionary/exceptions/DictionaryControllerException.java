package com.unulearner.backend.dictionary.exceptions;

public class DictionaryControllerException extends Exception {
    public DictionaryControllerException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public DictionaryControllerException(String exceptionMessage, Throwable exceptionCause) {
        super(exceptionMessage, exceptionCause);
    }
}
