package com.unulearner.backend.content.english.dictionary;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EnglishDictionaryRepository extends JpaRepository<EnglishDictionary, Long> {
    Iterable<EnglishDictionary> findAllByWordId(Long id);
}