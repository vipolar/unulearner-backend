package com.unulearner.backend.dictionary;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.UUID;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.unulearner.backend.dictionary.interfaces.DictionaryInterface;
import com.unulearner.backend.dictionary.models.extenders.WordList;
import com.unulearner.backend.dictionary.models.Language;
import com.unulearner.backend.storage.models.Entry;
import com.unulearner.backend.storage.Storage;

import com.unulearner.backend.dictionary.exceptions.DictionaryUtilityException;
import com.unulearner.backend.dictionary.exceptions.language.LanguageNotFoundException;

@Component
public class Dictionary {
    private final Logger logger = LoggerFactory.getLogger(Dictionary.class);
    private final DictionaryInterface dictionaryInterface;
    private final HashMap<UUID, Language> languageMap;
    private final Storage storage;

    public Dictionary(DictionaryInterface dictionaryInterface, Storage storage) {
        this.languageMap = new HashMap<UUID, Language>();
        this.dictionaryInterface = dictionaryInterface;
        this.storage = storage;
        
        try {
            this.dictionaryInterface.getInitiallyAvailableLanguages().forEach((languageCode, languageName) -> {
                Language language;

                try {
                    language = this.dictionaryInterface.getLanguageByCode(languageCode).orElseThrow(() -> new LanguageNotFoundException("Database entry for language '%s' not found".formatted(languageCode)));
                } catch (DictionaryUtilityException exception) {
                    logger.info("%s: %s. Creating new database entry...".formatted(exception.getClass().getSimpleName(), exception.getMessage()));
                    language = this.dictionaryInterface.addLanguage(new Language().setCode(languageCode).setName(languageName));
                }

                if (this.languageMap.put(language.getId(), language) != null) {
                    throw new RuntimeException("Language '%s' already exists in the languages hashmap!".formatted(language.getCode()));
                }      
            });

            this.languageMap.forEach((id, language) -> {
                this.dictionaryInterface.getInitiallyAvailableWordLists(language.getCode()).forEach(list -> {
                    String wordListDescription = list.get("description");
                    String wordListPath = list.get("path");
                    Map<String, Integer> lines = null;
                    WordList wordList = null;
                    Entry entry = null;
                    
                    if (wordListPath == null || wordListPath.isEmpty() || wordListPath.isBlank()) {
                        return;
                    }

                    try {
                        entry  = this.storage.whwh(wordListPath);
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }

                    try {
                        wordList = this.dictionaryInterface.searchWordListByEntry(entry).orElseThrow(() -> new DictionaryUtilityException("Word list '%s' not found in the database".formatted(wordListPath)));

                        if (wordList.getLanguage().getId() != language.getId()) {
                            throw new DictionaryUtilityException("Word list '%s' is not in the correct language".formatted(wordListPath));
                        }
                    } catch (DictionaryUtilityException exception) {
                        logger.info("%s: %s. Creating new database entry...".formatted(exception.getClass().getSimpleName(), exception.getMessage()));
                        wordList = new WordList().setEntry(entry).setLanguage(language).setDescription(wordListDescription).setImported(false);
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }

                    try {
                        if (wordList.getImported() == true) {
                            if (wordList.getLinesSum() != Files.lines(entry.getEntryPath().getPath()).count()) {
                                throw new DictionaryUtilityException("Word list '%s' has been modified since last import".formatted(wordListPath));
                            }
                        } else {
                            throw new DictionaryUtilityException("Word list '%s' has not been imported yet".formatted(wordListPath));
                        }
                    } catch (DictionaryUtilityException exception) {
                        logger.info("%s: %s. Creating new database entry...".formatted(exception.getClass().getSimpleName(), exception.getMessage()));
                        lines = this.dictionaryInterface.importWordsFromWordList(wordList);
                        wordList.setImported(true).setLinesMetadata(lines);
                        this.dictionaryInterface.updateWordList(wordList);

                        logger.info("Word list '%s' imported successfully. Total lines: %d. Lines commented out: %d. Lines accepted: %d. Lines persisted: %d. Lines ignored: %d. Blank lines: %d".formatted(wordListPath, wordList.getLinesSum(), wordList.getLinesCommented(), wordList.getLinesAccepted(), wordList.getLinesPersisted(), wordList.getLinesIgnored(), wordList.getLinesBlank()));
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
            });
            
            logger.info("Dictionary initialized with %d languages".formatted(this.languageMap.size()));
        } catch (Exception exception) {
            /* If it got to here then we've got no choice but to crash it! */
            logger.error("Fatal error: %s".formatted(exception.getMessage()));
            logger.error("Failed to build the dictionary".formatted());
            logger.error("Crashing the application...".formatted());

            throw new RuntimeException(exception.getMessage(), exception.getCause());
        }
    }

    public Language getLanguage(UUID id) throws LanguageNotFoundException {
        final Language language = this.languageMap.get(id);

        if (language == null) {
            throw new LanguageNotFoundException("Language '%s' is not valid".formatted(id.toString()));
        }

        return language;
    }

    public Language addLanguage(Language language) {
        return this.dictionaryInterface.addLanguage(language);
    }
}
