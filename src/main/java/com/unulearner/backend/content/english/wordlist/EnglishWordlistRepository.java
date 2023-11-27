package com.unulearner.backend.content.english.wordlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface EnglishWordlistRepository extends JpaRepository<EnglishWordlist, Long> {
    @Query(value = "SELECT * FROM english_wordlist ew WHERE SIMILARITY(SUBSTRING(ew.word FROM 1 FOR LENGTH(:partialWord)), :partialWord) > 0.3 ORDER BY SIMILARITY(SUBSTRING(ew.word FROM 1 FOR LENGTH(:partialWord)), :partialWord) DESC, ABS(LENGTH(ew.word) - LENGTH(:partialWord)) LIMIT 12", nativeQuery = true)
    Iterable<EnglishWordlist> findAllMatching(@Param("partialWord") String partialWord);
}