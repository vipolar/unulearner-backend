package com.unulearner.backend.services.files.storage;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Id;
import jakarta.persistence.Table;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@Table(name = "storage_node")
public class FilesStorageNode {
    @Id // Auto-generated ID
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID", unique = true, nullable = false)
    private UUID id;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @ManyToOne // Represent a tree structure using a self-referential relationship within a single entity.
    @JsonBackReference
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "parent_id", columnDefinition = "UUID", nullable = true)
    private FilesStorageNode parent;

    public FilesStorageNode getParent() {
        return parent;
    }

    public void setParent(FilesStorageNode parent) {
        this.parent = parent;
    }

    @Column(name = "url", columnDefinition = "VARCHAR(2048)", nullable = false)
    private String url;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Column(name = "name", columnDefinition = "VARCHAR(255)", nullable = false)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @CreationTimestamp
    @Column(name = "created", columnDefinition = "TIMESTAMP", nullable = false, updatable = false)
    private Date created;

    public Date getCreated() {
        return created;
    }

    @UpdateTimestamp
    @Column(name = "updated", columnDefinition = "TIMESTAMP", nullable = false)
    private Date updated;

    public Date getUpdated() {
        return updated;
    }

    @Column(name = "description", columnDefinition = "VARCHAR(512)", nullable = false)
    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Column(name = "directory", nullable = false)
    private Boolean isDirectory;

    public Boolean getIsDirectory() {
        return isDirectory;
    }

    public void setIsDirectory(Boolean isDirectory) {
        this.isDirectory = isDirectory;
    }

    @Transient
    @JsonManagedReference
    @OneToMany(mappedBy = "parent")
    private List<FilesStorageNode> childNodes;

    public List<FilesStorageNode> getChildNodes() {
        return childNodes;
    }

    public void setChildNodes(List<FilesStorageNode> childNodes) {
        this.childNodes = childNodes;
    }

    @Column(name = "confirmed", nullable = false)
    private Boolean confirmed = true;

    public Boolean getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(Boolean confirmed) {
        this.confirmed = confirmed;
    }

    @Column(name = "readable", nullable = false)
    private Boolean readable = true;

    public Boolean getReadable() {
        return readable;
    }

    public void setReadable(Boolean readable) {
        this.readable = readable;
    }

    @Column(name = "physical", nullable = false)
    private Boolean physical = true;

    public Boolean getPhysical() {
        return physical;
    }

    public void setPhysical(Boolean physical) {
        this.physical = physical;
    }

    @Column(name = "malignant", nullable = false)
    private Boolean malignant = false;

    public Boolean getMalignant() {
        return malignant;
    }

    public void setMalignant(Boolean malignant) {
        this.malignant = malignant;
    }

    @Column(name = "safe", nullable = false)
    private Boolean safe = false;

    public Boolean getSafe() {
        return safe;
    }

    public void setSafe(Boolean safe) {
        this.safe = safe;
    }

    @PreUpdate
    @PrePersist
    public void preDatabaseCommit() {
        if (this.confirmed && this.readable && this.physical && !this.malignant) {
            this.setSafe(true);
            return;
        }

        this.setSafe(false);
    }
}