package com.unulearner.backend.services.files.storage;

import java.util.Date;
import java.util.List;

import jakarta.persistence.Id;
import jakarta.persistence.Table;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT", unique = true, nullable = false)
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @ManyToOne // Represent a tree structure using a self-referential relationship within a single entity.
    @JsonBackReference
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "parent_id", columnDefinition = "BIGINT", nullable = true)
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

    @Column(name = "exists", nullable = false)
    private Boolean existsOnDisk = true;

    public Boolean getExistsOnDisk() {
        return existsOnDisk;
    }

    public void setExistsOnDisk(Boolean existsOnDisk) {
        this.existsOnDisk = existsOnDisk;
    }

    @Column(name = "verified", nullable = false)
    private Boolean isVerified = true;

    public Boolean getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Boolean isVerified) {
        this.isVerified = isVerified;
    }

    @Column(name = "readable", nullable = false)
    private Boolean isReadable = true;

    public Boolean getIsReadable() {
        return isReadable;
    }

    public void setIsReadable(Boolean isReadable) {
        this.isReadable = isReadable;
    }

    @Column(name = "malignant", nullable = false)
    private Boolean isMalignant = false;

    public Boolean getIsMalignant() {
        return isMalignant;
    }

    public void setIsMalignant(Boolean isMalignant) {
        this.isMalignant = isMalignant;
    }
}