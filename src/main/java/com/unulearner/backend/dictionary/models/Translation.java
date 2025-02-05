package com.unulearner.backend.dictionary.models;

import java.util.UUID;

import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.unulearner.backend.dictionary.models.utility.TranslationType;

@Entity
@Table(name = "translations")
public class Translation {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "UUID", unique = true, nullable = false, updatable = false)
    private UUID id;

    public UUID getId() {
        return this.id;
    }

    public Translation setId(UUID id) {
        this.id = id;
        return this;
    }

    @ManyToOne
    @JoinColumn(name = "word_from_id")
    private Word wordFrom;

    public Word getWordFrom() {
        return this.wordFrom;
    }

    public Translation setWordFrom(Word wordFrom) {
        this.wordFrom = wordFrom;
        return this;
    }

    @ManyToOne
    @JoinColumn(name = "word_to_id")
    private Word wordTo;

    public Word getWordTo() {
        return this.wordTo;
    }

    public Translation setWordTo(Word wordTo) {
        this.wordTo = wordTo;
        return this;
    }

    private boolean preferred;

    public boolean isPreferred() {
        return this.preferred;
    }

    public Translation setPreferred(boolean preferred) {
        this.preferred = preferred;
        return this;
    }

    @Enumerated(EnumType.STRING)
    private TranslationType type;

    public TranslationType getType() {
        return this.type;
    }

    public Translation setType(TranslationType type) {
        this.type = type;
        return this;
    }
}
