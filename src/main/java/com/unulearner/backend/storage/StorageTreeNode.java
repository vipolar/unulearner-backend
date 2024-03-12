package com.unulearner.backend.storage;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import java.nio.file.Path;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@Table(name = "storage_node")
public class StorageTreeNode {
    /* Default constructor */
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
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @ManyToOne
    @JsonBackReference
    @JoinColumn(name = "parent", columnDefinition = "UUID", unique = false, nullable = true)
    private StorageTreeNode parent;

    public StorageTreeNode getParent() {
        return parent;
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
        return onDiskURL;
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
        return onDiskName;
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
        return description;
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
        return created;
    }

    /**
     * Self-explanatory.
     * This property does not have a setter as it should never be set manually.
     */
    @UpdateTimestamp
    @Column(name = "updated", columnDefinition = "TIMESTAMP", nullable = false)
    private Date updated;

    public Date getUpdated() {
        return updated;
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
        return children;
    }

    public void setChildren(List<StorageTreeNode> children) {
        this.children = children;
    }

    /**
     *
     */
    @Transient
    @JsonIgnore
    private Path absolutePath;

    public Path getAbsolutePath() {
        return absolutePath;
    }

    public void setAbsolutePath(Path absolutePath) {
        this.absolutePath = absolutePath;
    }

    /**
     * This property ensures that the file/directory is actually accessible (can be read/written to) on disk.
     * This is a transient property set by the StorageTree constructor itself, meaning that it is prudent to re-check this on request.
     */
    @Transient
    private Boolean isAccessible;

    public Boolean getIsAccessible() {
        return isAccessible;
    }

    public void setIsAccessible(Boolean isAccessible) {
        this.isAccessible = isAccessible;
    }

    /**
     * @param name
     * @param description
     * @param parent
     * @param children
     * @param relativeURL
     * @param absolutePath
     */
    public StorageTreeNode(String name, String description, StorageTreeNode parent, List<StorageTreeNode> children, String relativeURL, Path absolutePath) {
        this.parent = parent;
        this.onDiskName = name;
        this.children = children;
        this.onDiskURL = relativeURL;
        this.description = description;
        this.absolutePath = absolutePath;
    }
}
