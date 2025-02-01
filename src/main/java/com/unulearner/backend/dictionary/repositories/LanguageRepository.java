package com.unulearner.backend.dictionary.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.unulearner.backend.dictionary.models.Language;

import java.util.Optional;
import java.util.UUID;

public interface LanguageRepository extends JpaRepository<Language, UUID> {
    Optional<Language> findByCode(String code);
}
