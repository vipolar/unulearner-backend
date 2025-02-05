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
@Table(name = "definition")
public class Definition {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "UUID", unique = true, nullable = false, updatable = false)
    private UUID id;

    public UUID getId() {
        return this.id;
    }

    public Definition setId(UUID id) {
        this.id = id;
        return this;
    }

    @ManyToOne
    @JoinColumn(name = "word_id")
    private Word word;

    public Word getWord() {
        return this.word;
    }

    public Definition setWord(Word word) {
        this.word = word;
        return this;
    }

    @Column(name = "part_of_speech", nullable = false)
    @Enumerated(EnumType.STRING)
    private PartOfSpeech partOfSpeech;

    public PartOfSpeech getPartOfSpeech() {
        return this.partOfSpeech;
    }

    public Definition setPartOfSpeech(PartOfSpeech partOfSpeech) {
        this.partOfSpeech = partOfSpeech;
        return this;
    }

    @Column(name = "definition", columnDefinition = "TEXT", nullable = false)
    private String definition;

    public String getDefinition() {
        return this.definition;
    }

    public Definition setDefinition(String definition) {
        this.definition = definition;
        return this;
    }

    @Column(name = "context", columnDefinition = "TEXT", nullable = false)
    private String context;

    public String getContext() {
        return this.context;
    }

    public Definition setContext(String context) {
        this.context = context;
        return this;
    }
}
