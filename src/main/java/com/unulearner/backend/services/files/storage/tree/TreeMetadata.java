package com.unulearner.backend.services.files.storage.tree;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TreeMetadata {
    private String name;
    private String created;
    private String purpose;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getPurpose() {
        return purpose;
    }
    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getCreated() {
        return created;
    }
    public void setCreated(String created) {
        this.created = created;
    }

    public TreeMetadata() {
        this.name = "Generic directory";
        this.purpose = "No specific purpose was set";
        this.created = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    }
}