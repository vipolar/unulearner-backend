package com.unulearner.backend.dictionary.repositories.extenders;

import org.springframework.data.jpa.repository.JpaRepository;

import com.unulearner.backend.dictionary.models.extenders.WordList;
import com.unulearner.backend.storage.models.Entry;

import java.util.Optional;
import java.util.UUID;

public interface WordListRepository extends JpaRepository<WordList, UUID>  {
    Optional<WordList> findByEntry(Entry entry);}
