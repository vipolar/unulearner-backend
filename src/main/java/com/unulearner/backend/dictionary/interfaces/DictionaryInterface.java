package com.unulearner.backend.dictionary.interfaces;

import java.util.Optional;
import java.util.List;
import java.util.Map;

import com.unulearner.backend.dictionary.models.extenders.WordList;
import com.unulearner.backend.dictionary.models.Language;
import com.unulearner.backend.dictionary.models.Word;
import com.unulearner.backend.storage.models.Entry;

public interface DictionaryInterface {
    public List<Map<String, String>> getInitiallyAvailableWordLists(String languageCode);
    public Map<String, Integer> importWordsFromWordList(WordList wordList);
    public Optional<WordList> searchWordListByEntry(Entry entry);
    public Map<String, String> getInitiallyAvailableLanguages();
    public Optional<Language> getLanguageByCode(String code);
    public WordList updateWordList(WordList wordList);
    public WordList addWordList(WordList wordList);
    public Language addLanguage(Language language);
    public Word addWord(Word word);
}
