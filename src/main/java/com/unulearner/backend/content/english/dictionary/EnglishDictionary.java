package com.unulearner.backend.content.english.dictionary;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;

import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import com.fasterxml.jackson.annotation.JsonIgnore;

import com.unulearner.backend.content.english.wordlist.EnglishWordlist;
import com.unulearner.backend.content.english.dictionary.enums.PartOfSpeech;

@Entity
@Table(name = "english_dictionary")
public class EnglishDictionary {
    @JoinColumn(name = "word", nullable = false, referencedColumnName = "id")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonIgnore
    private EnglishWordlist word;

    public EnglishWordlist getWord() {
        return word;
    }

    public void setWord(EnglishWordlist word) {
        this.word = word;
    }

    @Id // Auto-generated ID
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT", unique = true, nullable = false)
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Column(name = "part_of_speech", nullable = false)
    @Enumerated(EnumType.STRING)
    private PartOfSpeech partOfSpeech;

    public PartOfSpeech getPartOfSpeech() {
        return partOfSpeech;
    }

    public void setPartOfSpeech(PartOfSpeech partOfSpeech) {
        this.partOfSpeech = partOfSpeech;
    }

    @Column(name = "definition", columnDefinition = "TEXT", nullable = false)
    private String definition;


    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    @Column(name = "context", columnDefinition = "TEXT", nullable = false)
    private String context;

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }
}
