package com.unulearner.backend.storage;

import java.util.UUID;
import java.util.Date;
import java.util.List;

import java.nio.file.Path;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.unulearner.backend.storage.exceptions.StorageServiceException;

@Entity
@Table(name = "storage_node")
public class StorageTreeNode {
    /**
     * Default constructor. Never meant to be called manually!!!
     */
    public StorageTreeNode() {}

    /**
     * This property is a constant, therefore it is the perfect property for tracking the nodes.
     * URL, while unique, cannot work as an ID as it can change with the file/directory location.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID", unique = true, nullable = false, updatable = false)
    private UUID id;

    public UUID getId() {
        return this.id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Self-explanatory.
     * This is a JPA-persisted, JSON-ignored property, beneficial only in creating a node tree.
     */
    @ManyToOne
    @JsonBackReference
    @JoinColumn(name = "parent", columnDefinition = "UUID", unique = false, nullable = true)
    private StorageTreeNode parent;

    public StorageTreeNode getParent() {
        return this.parent;
    }

    public void setParent(StorageTreeNode parent) {
        this.parent = parent;
    }

    /**
     * This property is guaranteed to be unique with each node as it emulates unix-like file system urls.
     * From this property we can easily derive the full, physical path of a file/directory.
     * Due to its inherent uniqueness and parallel to physical paths "url" property is a great choice to serve as an ID!
     */
    @Column(name = "ondiskurl", columnDefinition = "TEXT COLLATE \"C\"", unique = true, nullable = false)
    private String onDiskURL;

    public String getOnDiskURL() {
        return this.onDiskURL;
    }

    public void setOnDiskURL(String onDiskURL) {
        this.onDiskURL = onDiskURL;
    }

    /**
     * This property is mostly for the benefit of the front-end, although it can still be useful in the back-end.
     */
    @Column(name = "ondiskname", columnDefinition = "TEXT COLLATE \"C\"", nullable = false)
    private String onDiskName;

    public String getOnDiskName() {
        return this.onDiskName;
    }

    public void setOnDiskName(String onDiskName) {
        this.onDiskName = onDiskName;
    }

    /**
     * This property serves no purpose in file system management.
     * This property is purely for the human eyes or the search bots.
     */
    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Self-explanatory.
     * This property does not have a setter as it should never be set manually.
     */
    @CreationTimestamp
    @Column(name = "created", columnDefinition = "TIMESTAMP", nullable = false, updatable = false)
    private Date created;

    public Date getCreated() {
        return this.created;
    }

    /**
     * Self-explanatory.
     * This property does not have a setter as it should never be set manually.
     */
    @UpdateTimestamp
    @Column(name = "updated", columnDefinition = "TIMESTAMP", nullable = false, updatable = true)
    private Date updated;

    public Date getUpdated() {
        return this.updated;
    }

    /**
     * Self-explanatory.
     * This is a transient property, beneficial only in creating a node tree.
     * There is no need for a separate column in the database as the parent-child relationship is described within the URL.
     */
    @Transient
    @JsonManagedReference
    @OneToMany(mappedBy = "parent")
    private List<StorageTreeNode> children;

    public List<StorageTreeNode> getChildren() {
        return this.children;
    }

    public void setChildren(List<StorageTreeNode> children) {
        this.children = children;
    }

    /**
     * For internal use only!
     * Full, on-disk path of the file/directory associated with the node.
     */
    @Transient
    @JsonIgnore
    private Path relativePath;

    public Path getRelativePath() {
        return this.relativePath;
    }

    public void setRelativePath(Path relativePath) {
        this.relativePath = relativePath;

        if (relativePath != null) {
            if (relativePath.isAbsolute()) {
                this.setOnDiskURL("");
                this.setOnDiskName("");
            } else {
                this.setOnDiskURL(relativePath.toString());
                this.setOnDiskName(relativePath.getFileName().toString());
            }
        }
    }

    /**
     * This property ensures that the file/directory is actually accessible (can be read/written to) on disk.
     * This is a transient property set by the StorageTree constructor itself, meaning that it is prudent to re-check this on request.
     */
    @Transient
    private Boolean isAccessible = false;

    public Boolean getIsAccessible() {
        return this.isAccessible;
    }

    public void setIsAccessible(Boolean isAccessible) {
        this.isAccessible = isAccessible;
    }

    /**
     * This property is set to true only if the node has been committed/updated by user.
     */
    private Boolean isConfirmed = false;

    public Boolean getIsConfirmed() {
        return this.isConfirmed;
    }

    public void setIsConfirmed(Boolean isConfirmed) {
        this.isConfirmed = isConfirmed;
    }

    public Boolean isDirectory() {
        return this.children != null;
    }

    /**
     * UUID of the task the node is busy with
     */
    @Transient
    @JsonIgnore
    private UUID busyWith;

    public UUID getBusyWith() {
        return busyWith;
    }

    public void setBusyWith(UUID busyWith) {
        this.busyWith = busyWith;
    }

    /**
     * @throws StorageServiceException if the node doesn't have a parent (root node being an exception!)
     */
    @PreUpdate
    @PrePersist
    public void preCommitChecks() throws StorageServiceException {
        if (this.parent == null && (!this.onDiskURL.isBlank() || !this.onDiskName.isBlank() || !this.relativePath.isAbsolute())) {
            throw new StorageServiceException("All nodes must be accompanied by a parent!");
        }
    }

    /**
     * @param parent Self-explanatory. This is a JPA-persisted, JSON-ignored property, beneficial only in creating a node tree.
     * @param children Self-explanatory. This is a transient property, beneficial only in creating a node tree (mirroring the parent relationship).
     * @param relativePath Properties derived from it are public, but the path itself is for internal use only! On-disk path of the file/directory associated with the node.
     * @param description This property serves no purpose in file system management. This property is purely for the human eyes or the search bots (although...)
     */
    public StorageTreeNode(StorageTreeNode parent, List<StorageTreeNode> children, Path relativePath, String description) {
        this.setParent(parent);
        this.setChildren(children);
        this.setDescription(description);
        this.setRelativePath(relativePath);
    }
}
