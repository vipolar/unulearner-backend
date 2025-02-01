package com.unulearner.backend.dictionary.models;

import com.unulearner.backend.dictionary.models.utility.PartOfSpeech;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.EnumType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
@Table(name = "meanings")
public class Meaning {
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

    @ManyToOne
    @JoinColumn(name = "word_id")
    private Word word;

    public Word getWord() {
        return this.word;
    }

    public void setWord(Word word) {
        this.word = word;
    }

    @Column(name = "part_of_speech", nullable = false)
    @Enumerated(EnumType.STRING)
    private PartOfSpeech partOfSpeech;

    public PartOfSpeech getPartOfSpeech() {
        return this.partOfSpeech;
    }

    public void setPartOfSpeech(PartOfSpeech partOfSpeech) {
        this.partOfSpeech = partOfSpeech;
    }

    @Column(name = "meaning", columnDefinition = "TEXT", nullable = false)
    private String meaning;

    public String getMeaning() {
        return this.meaning;
    }

    public void setMeaning(String meaning) {
        this.meaning = meaning;
    }

    @Column(name = "context", columnDefinition = "TEXT", nullable = false)
    private String context;

    public String getContext() {
        return this.context;
    }

    public void setContext(String context) {
        this.context = context;
    }
}
