package com.unulearner.backend.content.german.wordlist;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;

// import jakarta.persistence.JoinColumn;
// import jakarta.persistence.ManyToOne;

// import jakarta.persistence.Temporal;
// import jakarta.persistence.TemporalType;

@Entity
@Table(name = "german_wordlist")
public class GermanWordlist {
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

	@Column(name = "word", columnDefinition = "VARCHAR(255)", unique = true, nullable = false)
    private String word;

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }
}