package com.unulearner.backend.dictionary.exceptions.language;

import com.unulearner.backend.dictionary.exceptions.DictionaryUtilityException;

public class LanguageNotFoundException extends DictionaryUtilityException {
    public LanguageNotFoundException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public LanguageNotFoundException(String exceptionMessage, Throwable exceptionCause) {
        super(exceptionMessage, exceptionCause);
    }
}
