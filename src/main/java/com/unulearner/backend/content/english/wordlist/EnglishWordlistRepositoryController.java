package com.unulearner.backend.content.english.wordlist;

import java.nio.file.Files;
import java.nio.file.Path;

//import java.sql.Timestamp;
//import java.util.concurrent.TimeUnit;

import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RestController;
//import org.springframework.beans.factory.annotation.Autowired;

import com.unulearner.backend.storage.properties.StorageProperties;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping(path="/content/english/wordlist")
public class EnglishWordlistRepositoryController {

    private final EnglishWordlistRepository englishWordlistRepository;
    private final StorageProperties storageProperties;

    // Constructor to initialize dependencies
    //@Autowired
    public EnglishWordlistRepositoryController(
                EnglishWordlistRepository englishWordlistRepository,
                StorageProperties storageProperties
            ) {
        this.englishWordlistRepository = englishWordlistRepository;
        this.storageProperties = storageProperties;
    }

    @PostMapping(path="/add")
    public ResponseEntity<EnglishWordlist> addNewWord (@RequestBody EnglishWordlist word) {
        
        if (word.getId() != null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        if (word.getWord() == null || word.getWord() == "") {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        EnglishWordlist newWord = englishWordlistRepository.save(word);
        return new ResponseEntity<>(newWord, HttpStatus.OK);
    }

    @PostMapping(path="/import")
    public ResponseEntity<String> importWordsFromFile (@RequestBody String wordlist) {
        try {
            // Read the file from the specified path
            //TODO: this.storageProperties shouldn't be here!!! only the file URL!!!
            String wordliURI = this.storageProperties.getRootDirectory() + wordlist;
            Path file = Path.of(wordliURI);

            Iterable<String> words = Files.lines(file)
                                    .filter(line -> !line.trim().isEmpty()) // Filter out empty lines
                                    .collect(Collectors.toList());

            // Only handles 'newline'-separated list of words
            // Add words to the database
            for (String word : words) {
                EnglishWordlist newWord = new EnglishWordlist();
                newWord.setWord(word);

                englishWordlistRepository.save(newWord);
            }

            return new ResponseEntity<>("Words from the file added to the database", HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error reading the file or adding words to the database", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping(path="/update/{wordId}")
    public ResponseEntity<EnglishWordlist> updateWordById(@PathVariable Long wordId, @RequestBody EnglishWordlist updatedWord) {
        Optional<EnglishWordlist> optionalWord = englishWordlistRepository.findById(wordId);

        if (optionalWord.isPresent()) {
            EnglishWordlist word = optionalWord.get();

            //do the updating here!!!
            return new ResponseEntity<>(word, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(path="/get/{wordId}")
    public ResponseEntity<EnglishWordlist> getWordById(@PathVariable Long wordId) {
        Optional<EnglishWordlist> optionalWord = englishWordlistRepository.findById(wordId);
    
        if (optionalWord.isPresent()) {
            EnglishWordlist word = optionalWord.get();

            return new ResponseEntity<>(word, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(path="/get/word")
    public ResponseEntity<Iterable<EnglishWordlist>> getWordsFuzzy(
            @RequestParam(name="query", required = true) String queryString){

        Iterable<EnglishWordlist> EnglishWordlist = englishWordlistRepository.findAllMatching(queryString);

        return new ResponseEntity<>(EnglishWordlist, HttpStatus.OK);
    }

    @GetMapping(path="/get/all")
    public ResponseEntity<Iterable<EnglishWordlist>> getAllWords(
            @RequestParam(required = true) Integer page,
            @RequestParam(required = true) Integer size
        ) {

        Pageable paging = PageRequest.of(page, size);
        Iterable<EnglishWordlist> EnglishWordlist = englishWordlistRepository.findAll(paging);

        return new ResponseEntity<>(EnglishWordlist, HttpStatus.OK);
    }

    @DeleteMapping(path="/delete/{wordId}")
    public ResponseEntity<EnglishWordlist> deleteRoleById(@PathVariable Long wordId) {
        Optional<EnglishWordlist> optionalWord = englishWordlistRepository.findById(wordId);

        if (optionalWord.isPresent()) {
            englishWordlistRepository.deleteById(wordId);
            
            return new ResponseEntity<>(HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
