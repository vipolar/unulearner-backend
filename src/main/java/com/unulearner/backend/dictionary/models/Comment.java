package com.unulearner.backend.dictionary.models;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
@Table(name = "comments")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "UUID", unique = true, nullable = false, updatable = false)
    private UUID id;

    public UUID getId() {
        return this.id;
    }
    public Comment setId(UUID id) {
        this.id = id;
        return this;
    }

    private String name;

    public String getName() {
        return this.name;
    }

    public Comment setName(String name) {
        this.name = name;
        return this;
    }

    private String code;

    public String getCode() {
        return this.code;
    }

    public Comment setCode(String code) {
        this.code = code;
        return this;
    }
}

