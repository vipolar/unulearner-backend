package com.unulearner.backend.content.wordlist.english;

import org.springframework.data.repository.CrudRepository;

public interface WordlistEnglishRepository extends CrudRepository<WordlistEnglish, Integer> {
    // This will be AUTO IMPLEMENTED by Spring into a Bean called WordlistEnglishRepository
    // CRUD refers Create, Read, Update, Delete
}