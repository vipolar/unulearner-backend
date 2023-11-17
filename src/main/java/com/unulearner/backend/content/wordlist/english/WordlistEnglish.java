package com.unulearner.backend.content.wordlist.english;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
// import jakarta.persistence.JoinColumn;
// import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
// import jakarta.persistence.Temporal;
// import jakarta.persistence.TemporalType;

@Entity
@Table(name = "wordlist_english")
public class WordlistEnglish {
    // Auto-generated ID
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "id", columnDefinition = "INT", unique = true, nullable = false)
    private Integer id;

    public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

    // Account handle (could be used instead of the email to log in)
	@Column(name = "word", columnDefinition = "VARCHAR(255)", unique = true, nullable = false)
    private String wordString;

    public String getWordString() {
        return wordString;
    }

    public void setWordString(String wordString) {
        this.wordString = wordString;
    }
}