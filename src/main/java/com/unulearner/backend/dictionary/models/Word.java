package com.unulearner.backend.dictionary.models;

import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "words", uniqueConstraints = @UniqueConstraint(columnNames = {"word", "language_id"}))
public class Word {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "UUID", unique = true, nullable = false, updatable = false)
    private UUID id;

    public UUID getId() {
        return this.id;
    }

    public Word setId(UUID id) {
        this.id = id;
        return this;
    }

    @Column(name = "word", columnDefinition = "VARCHAR(255)", unique = false, nullable = false)
    private String word;

    public String getWord() {
        return this.word;
    }

    public Word setWord(String word) {
        this.word = word;
        return this;
    }

    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Definition> definitions;

    public Set<Definition> getDefinitions() {
        return this.definitions;
    }

    public Word setDefinitions(Set<Definition> definitions) {
        this.definitions = definitions;
        return this;
    }

    @ManyToOne
    @JoinColumn(name = "language_id", nullable = false)
    private Language language;

    public Language getLanguage() {
        return this.language;
    }

    public Word setLanguage(Language language) {
        this.language = language;
        return this;
    }
}
