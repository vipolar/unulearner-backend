package com.unulearner.backend.dictionary.services;

import java.util.Optional;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.FileReader;
import java.util.List;
import java.util.Map;

import com.unulearner.backend.dictionary.properties.DictionaryProperties;
import com.unulearner.backend.dictionary.repositories.LanguageRepository;
import com.unulearner.backend.dictionary.repositories.WordRepository;
import com.unulearner.backend.dictionary.repositories.extenders.WordListRepository;
import com.unulearner.backend.storage.models.Entry;
import com.unulearner.backend.dictionary.interfaces.DictionaryInterface;
import com.unulearner.backend.dictionary.models.Language;
import com.unulearner.backend.dictionary.models.Word;
import com.unulearner.backend.dictionary.models.extenders.WordList;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;


@Service
public class DictionaryService implements DictionaryInterface {
    private final DictionaryProperties dictionaryProperties;
    private final LanguageRepository languageRepository;
    private final WordListRepository wordListRepository;
    private final WordRepository wordRepository;
    private final JdbcTemplate jdbcTemplate;

    public DictionaryService(LanguageRepository languageRepository, WordListRepository wordListRepository, WordRepository wordRepository, JdbcTemplate jdbcTemplate, DictionaryProperties dictionaryProperties) {
        this.dictionaryProperties = dictionaryProperties;
        this.languageRepository = languageRepository;
        this.wordListRepository = wordListRepository;
        this.wordRepository = wordRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Map<String, String>> getInitiallyAvailableWordLists(String languageCode) {
        List<Map<String, String>> availableWordLists = this.dictionaryProperties.getInitiallyAvailableWordLists(languageCode);

        return availableWordLists;
    }

    @Override
    public Map<String, String> getInitiallyAvailableLanguages() {        
        Map<String, String> availableLanguages = this.dictionaryProperties.getInitiallyAvailableLanguages();

        return availableLanguages;
    }

    public Optional<WordList> searchWordListByEntry(Entry entry) {
        return this.wordListRepository.findByEntry(entry);
    }

    @Override
    public Optional<Language> getLanguageByCode(String code) {
        return this.languageRepository.findByCode(code);
    }

    @Override
    public Language addLanguage(Language language) {
        return this.languageRepository.save(language);
    }

    @Override
    public Map<String, Integer> importWordsFromWordList(WordList wordList) {        
        String sql = "INSERT INTO words (word, language_id) VALUES (?, ?) ON CONFLICT (word, language_id) DO NOTHING";
        String wordListPath = wordList.getEntry().getEntryPath().getPath().toString();
        Map<String, Integer> results = new HashMap<String, Integer>();
        List<Object[]> batchArgs = new ArrayList<>();
        Integer linesCommented = 0;
        Integer linesPersisted = 0;
        Integer linesAccepted = 0;
        Integer linesIgnored = 0;
        Integer linesBlank = 0;
        Integer linesSum = 0;
        String line = null;
        
        try (BufferedReader br = new BufferedReader(new FileReader(wordListPath))) {
            while ((line = br.readLine()) != null || !batchArgs.isEmpty()) {
                if (line != null) {
                    line = line.trim();
                    linesSum++;
    
                    if (line.isEmpty()) {
                        linesBlank++;
                        continue;
                    }

                    if (line.charAt(0) == wordList.getCommentCharacter()) {
                        linesCommented++;
                        continue;
                    }
    
                    batchArgs.add(new Object[]{line, wordList.getLanguage().getId()});
                    linesAccepted++;
                }
    
                if (batchArgs.size() >= 500 || line == null) {
                    int[] affectedRows = jdbcTemplate.batchUpdate(sql, batchArgs);
    
                    for (int rows : affectedRows) {
                        if (rows > 0) {
                            linesPersisted++;
                        } else {
                            linesIgnored++;
                        }
                    }

                    batchArgs.clear();
                }
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    
        results.put("linesCommented", linesCommented);
        results.put("linesPersisted", linesPersisted);
        results.put("linesAccepted", linesAccepted);
        results.put("linesIgnored", linesIgnored);
        results.put("linesBlank", linesBlank);
        results.put("linesSum", linesSum);

        return results;
    }
    

    @Override
    public Word addWord(Word word) {
        return this.wordRepository.save(word);
    }

    @Override
    public WordList updateWordList(WordList wordList) {
        return this.wordListRepository.save(wordList);
    }

    @Override
    public WordList addWordList(WordList wordList) {
        return this.wordListRepository.save(wordList);
    }
}