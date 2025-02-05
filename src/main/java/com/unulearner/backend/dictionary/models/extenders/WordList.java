package com.unulearner.backend.dictionary.models.extenders;

import com.unulearner.backend.dictionary.models.Language;
import com.unulearner.backend.storage.models.Entry;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

import java.util.UUID;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "word_list")
public class WordList {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "UUID", unique = true, nullable = false, updatable = false)
    private UUID id;

    public UUID getId() {
        return this.id;
    }

    public WordList setId(UUID id) {
        this.id = id;
        return this;
    }

    @OneToOne
    @JoinColumn(name = "entry_id", nullable = false)
    private Entry entry;

    public Entry getEntry() {
        return this.entry;
    }

    public WordList setEntry(Entry entry) {
        this.entry = entry;
        return this;
    }

    @OneToOne
    @JoinColumn(name = "language_id", nullable = false)
    private Language language;

    public Language getLanguage() {
        return this.language;
    }

    public WordList setLanguage(Language language) {
        this.language = language;
        return this;
    }

    @Column(name = "imported", columnDefinition = "BOOLEAN", nullable = false)
    private Boolean imported = false;

    public Boolean getImported() {
        return this.imported;
    }

    public WordList setImported(Boolean imported) {
        this.imported = imported;
        return this;
    }

    @Column(name = "comment_character", columnDefinition = "CHAR(1)", nullable = true)
    private Character commentCharacter = '#';
    
    public Character getCommentCharacter() {
        return this.commentCharacter;
    }

    public WordList setCommentCharacter(Character commentChar) {
        this.commentCharacter = commentChar;
        return this;
    }

    @Column(name = "lines_sum", columnDefinition = "INT", nullable = false)
    private Integer linesSum = 0;

    public Integer getLinesSum() {
        return this.linesSum;
    }

    public WordList setLinesSum(Integer linesSum) {
        this.linesSum = linesSum;
        return this;
    }

    @Column(name = "lines_accepted", columnDefinition = "INT", nullable = false)
    private Integer linesAccepted = 0;

    public Integer getLinesAccepted() {
        return this.linesAccepted;
    }

    public WordList setLinesAccepted(Integer linesAccepted) {
        this.linesAccepted = linesAccepted;
        return this;
    }

    @Column(name = "lines_persisted", columnDefinition = "INT", nullable = false)
    private Integer linesPersisted = 0;

    public Integer getLinesPersisted() {
        return this.linesPersisted;
    }

    public WordList setLinesPersisted(Integer linesPersisted) {
        this.linesPersisted = linesPersisted;
        return this;
    }

    @Column(name = "lines_ignored", columnDefinition = "INT", nullable = false)
    private Integer linesIgnored = 0;

    public Integer getLinesIgnored() {
        return this.linesIgnored;
    }

    public WordList setLinesIgnored(Integer linesIgnored) {
        this.linesIgnored = linesIgnored;
        return this;
    }

    @Column(name = "lines_commented", columnDefinition = "INT", nullable = false)
    private Integer linesCommented = 0;

    public Integer getLinesCommented() {
        return this.linesCommented;
    }

    public WordList setLinesCommented(Integer linesCommented) {
        this.linesCommented = linesCommented;
        return this;
    }

    @Column(name = "lines_blank", columnDefinition = "INT", nullable = false)
    private Integer linesBlank = 0;

    public Integer getLinesBlank() {
        return this.linesBlank;
    }

    public WordList setLinesBlank(Integer linesBlank) {
        this.linesBlank = linesBlank;
        return this;
    }

    public WordList setLinesMetadata(Map<String, Integer> linesMetadata) throws NullPointerException {
        this.linesCommented = Objects.requireNonNull(linesMetadata.get("linesCommented"), "linesCommented cannot be null");
        this.linesPersisted = Objects.requireNonNull(linesMetadata.get("linesPersisted"), "linesPersisted cannot be null");
        this.linesAccepted = Objects.requireNonNull(linesMetadata.get("linesAccepted"), "linesAccepted cannot be null");
        this.linesIgnored = Objects.requireNonNull(linesMetadata.get("linesIgnored"), "linesIgnored cannot be null");
        this.linesBlank = Objects.requireNonNull(linesMetadata.get("linesBlank"), "linesBlank cannot be null");
        this.linesSum = Objects.requireNonNull(linesMetadata.get("linesSum"), "linesSum cannot be null");

        return this;
    }

    @Column(name = "description", columnDefinition = "VARCHAR(512)")
    private String description = null;

    public String getDescription() {
        return this.description;
    }

    public WordList setDescription(String description) {
        this.description = description;
        return this;
    }
}
