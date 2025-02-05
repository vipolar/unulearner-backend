package com.unulearner.backend.dictionary.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.unulearner.backend.dictionary.models.Language;
import com.unulearner.backend.dictionary.models.Word;

import java.util.UUID;
import java.util.List;

public interface WordRepository extends JpaRepository<Word, UUID> {
    @Query(value = "SELECT * FROM words ew " +
               "JOIN language l ON ew.language_id = l.id " + "WHERE l.code = :languageCode " +
               "AND SIMILARITY(SUBSTRING(ew.word FROM 1 FOR LENGTH(:partialWord)), :partialWord) > 0.3 " +
               "ORDER BY SIMILARITY(SUBSTRING(ew.word FROM 1 FOR LENGTH(:partialWord)), :partialWord) DESC, " +
               "ABS(LENGTH(ew.word) - LENGTH(:partialWord)) " + "LIMIT 12", nativeQuery = true)
    Iterable<Word> findAllMatching(@Param("partialWord") String partialWord, @Param("languageCode") String languageCode);

//    @Query("SELECT w FROM Word w " + "JOIN w.definitions m " + "JOIN m.words mw " + "WHERE w.language.code = :langCode AND w.word = :word")
//    List<Word> findTranslations(@Param("langCode") String langCode, @Param("word") String word);

    List<Word> findByLanguage(Language language);
}
