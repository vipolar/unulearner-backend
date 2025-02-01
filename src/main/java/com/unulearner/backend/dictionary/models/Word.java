package com.unulearner.backend.dictionary.models;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
@Table(name = "words")
public class Word {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID", unique = true, nullable = false, updatable = false)
    private UUID id;

    public UUID getId() {
        return this.id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Column(name = "word", columnDefinition = "VARCHAR(255)", unique = true, nullable = false)
    private String word;

    public String getWord() {
        return this.word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    @ManyToOne
    @JoinColumn(name = "language_id")
    private Language language;

    public Language getLanguage() {
        return this.language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }
}
