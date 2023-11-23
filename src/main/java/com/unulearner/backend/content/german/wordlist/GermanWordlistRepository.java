package com.unulearner.backend.content.german.wordlist;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GermanWordlistRepository extends JpaRepository<GermanWordlist, Long> {
    // This will be AUTO IMPLEMENTED by Spring into a Bean called GermanWordlistRepository
    // CRUD refers Create, Read, Update, Delete
}