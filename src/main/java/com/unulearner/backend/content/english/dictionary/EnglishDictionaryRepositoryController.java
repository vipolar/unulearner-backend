package com.unulearner.backend.content.english.dictionary;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping(path="/content/english/dictionary")
public class EnglishDictionaryRepositoryController {
    @Autowired
    private EnglishDictionaryRepository englishDictionaryRepository;
    
    @PostMapping(path="/add")
    public ResponseEntity<EnglishDictionary> addNewWord (@RequestBody EnglishDictionary word) {

        if (word.getWord() == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        EnglishDictionary newWord = englishDictionaryRepository.save(word);
        return new ResponseEntity<>(newWord, HttpStatus.OK);
    }

    @GetMapping(path="/get/{wordId}")
    public ResponseEntity<Iterable<EnglishDictionary>> getWordByRootId(@PathVariable Long wordId) {

        Iterable<EnglishDictionary> dictionary = englishDictionaryRepository.findAllByWordId(wordId);

        if (dictionary != null) {
            return new ResponseEntity<>(dictionary, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
