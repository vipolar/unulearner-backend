package com.unulearner.backend.dictionary.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.unulearner.backend.dictionary.repositories.LanguageRepository;
import com.unulearner.backend.dictionary.repositories.MeaningRepository;
import com.unulearner.backend.dictionary.repositories.WordRepository;
import com.unulearner.backend.dictionary.Dictionary;
import com.unulearner.backend.dictionary.models.Language;
import com.unulearner.backend.dictionary.models.Definition;
import com.unulearner.backend.dictionary.models.Word;

@RestController
@RequestMapping(path="/dictionary/{languageCode}")
public class DictionaryController {
    private final LanguageRepository languageRepository;
    private final MeaningRepository meaningRepository;
    private final WordRepository wordRepository;
    private final Dictionary dictionary;

    public DictionaryController(Dictionary dictionary, LanguageRepository languageRepository, MeaningRepository meaningRepository, WordRepository wordRepository) {
        this.languageRepository = languageRepository;
        this.meaningRepository = meaningRepository;
        this.wordRepository = wordRepository;
        this.dictionary = dictionary;
    }

    @GetMapping
    public List<Word> getWordsByLanguage(@PathVariable String languageCode) {
        Language language = languageRepository.findByCode(languageCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Language not found"));
        return wordRepository.findByLanguage(language);
    }

    @PostMapping
    public ResponseEntity<Word> addWord(@PathVariable String languageCode, @RequestBody Word word) {
        Language language = languageRepository.findByCode(languageCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid language"));
        word.setLanguage(language);
        return ResponseEntity.ok(wordRepository.save(word));
    }

    @GetMapping(path="/get/all")
    public ResponseEntity<Iterable<Word>> getAllWords(
            @PathVariable(required = true) String languageCode,
            @RequestParam(defaultValue = "0", required = false) Integer page,
            @RequestParam(defaultValue = "20", required = false) Integer size
        ) {

        Pageable paging = PageRequest.of(page, size);
        Iterable<Word> wordlist = wordRepository.findAll(paging);

        return new ResponseEntity<>(wordlist, HttpStatus.OK);
    }

    @GetMapping(path="/get/word")
    public ResponseEntity<Iterable<Word>> getWordsFuzzy(
            @PathVariable(required = true) String languageCode,
            @RequestParam(name="query", required = true) String partialWord){

        Iterable<Word> wordlist = wordRepository.findAllMatching(partialWord, languageCode);

        return new ResponseEntity<>(wordlist, HttpStatus.OK);
    }

    @GetMapping(path="/get/word/{wordUUID}/meanings")
    public ResponseEntity<Iterable<Definition>> getAllMeanings(
            @PathVariable(required = true) UUID wordUUID,
            @PathVariable(required = true) String languageCode,
            @RequestParam(defaultValue = "0", required = false) Integer page,
            @RequestParam(defaultValue = "20", required = false) Integer size
        ) {

        Iterable<Definition> wordlist = meaningRepository.findAllByWordId(wordUUID);

        return new ResponseEntity<>(wordlist, HttpStatus.OK);
    }
}
