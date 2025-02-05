package com.unulearner.backend.dictionary.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.unulearner.backend.dictionary.models.Definition;
import com.unulearner.backend.dictionary.models.Word;

import java.util.UUID;

public interface MeaningRepository extends JpaRepository<Definition, UUID> {
    Iterable<Definition> findAllByWord(Word word);
    Iterable<Definition> findAllByWordId(UUID wordUUID);
}
