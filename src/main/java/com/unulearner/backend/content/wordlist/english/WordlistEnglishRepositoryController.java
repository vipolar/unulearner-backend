package com.unulearner.backend.content.wordlist.english;

//import java.sql.Timestamp;
//import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.unulearner.backend.content.BackendResponseEntity;

import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping(path="/content/wordlist/english")
public class WordlistEnglishRepositoryController {
    @Autowired
    private WordlistEnglishRepository wordlistEnglishRepository;

    @PostMapping(path="/add")
    public ResponseEntity<BackendResponseEntity<WordlistEnglish>> addNewRole (
            @RequestParam String wordString
        ) {
        WordlistEnglish word = new WordlistEnglish();

        word.setWordString(wordString);

        WordlistEnglish newWord = wordlistEnglishRepository.save(word);
        BackendResponseEntity<WordlistEnglish> response = new BackendResponseEntity<>("New user added successfully!", HttpStatus.OK, newWord);

        return response.createResponseEntity();
    }

    @GetMapping(path="/get/all")
    public ResponseEntity<BackendResponseEntity<Iterable<WordlistEnglish>>> getAllWordlistEnglish() {
        Iterable<WordlistEnglish> allEnglishWords = wordlistEnglishRepository.findAll();

        BackendResponseEntity<Iterable<WordlistEnglish>> response = new BackendResponseEntity<>("All user roles retrieved successfully", HttpStatus.OK, allEnglishWords);
        return response.createResponseEntity();
    }
}
