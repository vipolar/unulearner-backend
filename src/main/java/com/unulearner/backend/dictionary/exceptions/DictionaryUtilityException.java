package com.unulearner.backend.dictionary.exceptions;

public class DictionaryUtilityException extends Exception {
    public DictionaryUtilityException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public DictionaryUtilityException(String exceptionMessage, Throwable exceptionCause) {
        super(exceptionMessage, exceptionCause);
    }
}
