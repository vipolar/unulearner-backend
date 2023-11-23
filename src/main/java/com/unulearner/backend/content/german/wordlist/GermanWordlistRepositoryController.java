package com.unulearner.backend.content.german.wordlist;

//import java.sql.Timestamp;
//import java.util.concurrent.TimeUnit;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping(path="/content/german/wordlist")
public class GermanWordlistRepositoryController {
    @Autowired
    private GermanWordlistRepository germanWordlistRepository;

    @PostMapping(path="/add")
    public ResponseEntity<GermanWordlist> addNewWord (@RequestBody GermanWordlist word) {
        
        if (word.getId() != null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        if (word.getWord() == null || word.getWord() == "") {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        GermanWordlist newWord = germanWordlistRepository.save(word);
        return new ResponseEntity<>(newWord, HttpStatus.OK);
    }

    @PutMapping(path="/update/{wordId}")
    public ResponseEntity<GermanWordlist> updateWordById(@PathVariable Long wordId, @RequestBody GermanWordlist updatedWord) {
        Optional<GermanWordlist> optionalWord = germanWordlistRepository.findById(wordId);

        if (optionalWord.isPresent()) {
            GermanWordlist word = optionalWord.get();

            //do the updating here!!!
            return new ResponseEntity<>(word, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(path="/get/{wordId}")
    public ResponseEntity<GermanWordlist> getWordById(@PathVariable Long wordId) {
        Optional<GermanWordlist> optionalWord = germanWordlistRepository.findById(wordId);
    
        if (optionalWord.isPresent()) {
            GermanWordlist word = optionalWord.get();

            return new ResponseEntity<>(word, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(path="/get/all")
    public ResponseEntity<Iterable<GermanWordlist>> getAllWords(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "15") Integer size
        ) {

        Pageable paging = PageRequest.of(page, size);
        Iterable<GermanWordlist> wordlist = germanWordlistRepository.findAll(paging);

        return new ResponseEntity<>(wordlist, HttpStatus.OK);
    }

    @DeleteMapping(path="/delete/{wordId}")
    public ResponseEntity<GermanWordlist> deleteRoleById(@PathVariable Long wordId) {
        Optional<GermanWordlist> optionalWord = germanWordlistRepository.findById(wordId);

        if (optionalWord.isPresent()) {
            germanWordlistRepository.deleteById(wordId);
            
            return new ResponseEntity<>(HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
