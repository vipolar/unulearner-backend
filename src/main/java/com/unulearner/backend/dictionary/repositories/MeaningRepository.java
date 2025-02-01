package com.unulearner.backend.dictionary.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.unulearner.backend.dictionary.models.Meaning;
import com.unulearner.backend.dictionary.models.Word;

import java.util.UUID;

public interface MeaningRepository extends JpaRepository<Meaning, UUID> {
    Iterable<Meaning> findAllByWord(Word word);
    Iterable<Meaning> findAllByWordId(UUID wordUUID);
}
