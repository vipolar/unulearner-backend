package com.unulearner.backend.storage.entities;

import java.util.UUID;
import java.util.Date;
import java.util.List;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.PostLoad;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Inheritance;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.DiscriminatorColumn;

import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import com.unulearner.backend.storage.extensions.NodePath;

import com.unulearner.backend.storage.exceptions.StorageServiceException;

@Entity
@Table(name = "storage_node")
@DiscriminatorValue("StorageNode")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "entity_type", discriminatorType = DiscriminatorType.STRING)
public class StorageNode {
    /**
     * Default constructor. Never meant to be called manually!!!
     */
    public StorageNode() {}

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

    public StorageNode setId(UUID id) {
        this.id = id;

        this.hasBeenEdited = true;
        return this;
    }

    /**
     * This property is guaranteed to be unique with each node as it emulates unix-like file system urls.
     * From this property we can easily derive the full, physical path to a file/directory.
     */
    @Column(name = "url", columnDefinition = "TEXT COLLATE \"C\"", unique = true, nullable = false)
    private String url;

    public String getUrl() {
        if (this.url != null && !this.url.startsWith("/")) {
            return "/" + this.url;
        }
        
        return this.url;
    }

    public StorageNode setUrl(String url) {
        this.url = url.replace("\\", "/").trim();

        this.hasBeenEdited = true;
        return this;
    }

    /**
     * This property is mostly for the benefit of the front-end, although it can still be useful in the back-end.
     */
    @Column(name = "name", columnDefinition = "TEXT COLLATE \"C\"", nullable = false)
    private String name;

    public String getName() {
        return this.name;
    }

    public StorageNode setName(String name) {
        this.name = name;

        this.hasBeenEdited = true;
        return this;
    }

    /**
     * Owner (ownership is transfarable) of the node and the file/directory associated with it.
     */
    @Column(name = "owner_user", columnDefinition = "UUID", unique = false, nullable = false, updatable = true)
    private UUID user;

    public UUID getUser() {
        return this.user;
    }

    public StorageNode setUser(UUID user) {
        this.user = user;

        this.hasBeenEdited = true;
        return this;
    }

    /**
     * Authorized group that may have special privileges in accessing the node and the file/directory associated with it.
     */
    @Column(name = "owner_group", columnDefinition = "UUID", unique = false, nullable = false, updatable = true)
    private UUID group;

    public UUID getGroup() {
        return this.group;
    }

    public StorageNode setGroup(UUID group) {
        this.group = group;

        this.hasBeenEdited = true;
        return this;
    }

    /**
     * Unix-like file/directory permissions presented as a 3 digit integer (not applied to physical files/directories - only the nodes associated to them.)
     */
    @Column(name = "permissions", columnDefinition = "SMALLINT", unique = false, nullable = false, updatable = true)
    private Short permissions;
    
    public Short getPermissions() {
        return this.permissions;
    }

    public StorageNode setPermissions(Short permissions) {
        this.permissions = permissions;

        this.hasBeenEdited = true;
        return this;
    }

    /**
     * Self-explanatory.
     * This is a JPA-persisted, JSON-ignored property, beneficial only in creating a node tree.
     */
    @ManyToOne
    @JsonBackReference
    @JoinColumn(name = "parent", columnDefinition = "UUID", unique = false, nullable = true)
    private StorageNode parent;

    public StorageNode getParent() {
        return this.parent;
    }

    public StorageNode setParent(StorageNode parent) {
        this.parent = parent;

        this.hasBeenEdited = true;
        return this;
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
    private List<StorageNode> children;

    public List<StorageNode> getChildren() {
        return this.children;
    }

    public StorageNode setChildren(List<StorageNode> children) {
        this.children = children;

        this.hasBeenEdited = true;
        return this;
    }

    /**
     * For internal use only!
     * Full, on-disk path of the file/directory associated with the node.
     */
    @Transient
    @JsonIgnore
    private NodePath nodePath = null;

    public NodePath getNodePath() {
        return this.nodePath;
    }

    public StorageNode setNodePath(NodePath nodePath) {
        this.nodePath = nodePath;

        /* null path is allowed all the way up until the node is committed to the database */
        if (nodePath != null) {
            this.setName(nodePath.getFileName().toString());
            this.setUrl(nodePath.getRelativePath().toString());
        }

        return this;
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

    public StorageNode setIsAccessible(Boolean isAccessible) {
        this.isAccessible = isAccessible;

        return this;
    }

    /**
     * This property is set to true only if the node has been committed/updated by user.
     */
    private Boolean isConfirmed = false;

    public Boolean getIsConfirmed() {
        return this.isConfirmed;
    }

    public StorageNode setIsConfirmed(Boolean isConfirmed) {
        this.isConfirmed = isConfirmed;

        this.hasBeenEdited = true;
        return this;
    }

    @Transient
    @JsonIgnore
    public Boolean isDirectory() {
        return this.children != null;
    }

    @Transient
    @JsonIgnore
    private Boolean hasBeenEdited = false;

    public Boolean getHasBeenEdited() {
        return this.hasBeenEdited;
    }

    /**
     * @throws StorageServiceException if the node doesn't have a parent (root node being an exception!)
     */
    @PreUpdate
    @PrePersist
    public void preCommitChecks() throws StorageServiceException {
        if (this.nodePath == null || !this.nodePath.isValidNode()) {
            throw new StorageServiceException("All nodes must have an accessible physical address attached to them.".formatted());
        }

        if (this.parent == null && !this.nodePath.getRelativePath().toString().isBlank()) {
            throw new StorageServiceException("Only root node can have a null parent.".formatted());
        }
    }

    @PostLoad
    @PostUpdate
    @PostPersist
    private void resetHasBeenEdited() {
        this.hasBeenEdited = false;
    }

    public StorageNode mergeNode(StorageNode storageNode) {
        this.setIsAccessible(storageNode.getIsAccessible())
            .setNodePath(storageNode.getNodePath())
            .setChildren(storageNode.getChildren())
            .setParent(storageNode.getParent());

            return this;
    }

    @Override
    public String toString() {
        return "StorageNode{" +
            "id=" + this.getId() +
            ", url='" + this.getUrl() + '\'' +
            ", name='" + this.getName() + '\'' +
            ", user=" + this.getUser() +
            ", group=" + this.getGroup() +
            ", created=" + this.getCreated() +
            ", updated=" + this.getUpdated() +
            ", isDirectory=" + this.isDirectory() +
            ", isConfirmed=" + this.getIsConfirmed() +
            ", isAccessible=" + this.getIsAccessible() +
            '}';
    }
}
