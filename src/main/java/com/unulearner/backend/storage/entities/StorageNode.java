package com.unulearner.backend.storage.entities;

import java.util.UUID;
import java.util.Date;
import java.util.List;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import jakarta.persistence.PreUpdate;
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
import com.fasterxml.jackson.annotation.JsonProperty;
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
        return this;
    }

    /**
     * This property serves no purpose in file system management.
     * This property is purely for the human eyes or the search bots.
     */
    @Column(name = "description", columnDefinition = "TEXT", nullable = true)
    private String description;

    public String getDescription() {
        return this.description;
    }

    public StorageNode setDescription(String description) {
        this.description = description;
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
        return this;
    }

    /**
     * Unix-like file/directory permissions presented as a 4 digit integer (not applied to physical files/directories - only the nodes associated to them.)
     */
    @Column(name = "permission_flags", columnDefinition = "VARCHAR(4)", unique = false, nullable = false, updatable = true)
    private String permissions;
    
    public String getPermissions() {
        return this.permissions;
    }

    public StorageNode setPermissions(String permissions) {
        this.permissions = permissions;
        return this;
    }

    public Boolean setuidBitIsSet() {
        return ((this.permissions.length() == 4 ? (Integer.parseInt(this.permissions, 8) / 1000) % 10 : 0) & 4) != 0;
    }

    public Boolean setgidBitIsSet() {
        return ((this.permissions.length() == 4 ? (Integer.parseInt(this.permissions, 8) / 1000) % 10 : 0) & 2) != 0;
    }

    public Boolean stickyBitIsSet() {
        return ((this.permissions.length() == 4 ? (Integer.parseInt(this.permissions, 8) / 1000) % 10 : 0) & 1) != 0;
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
        if (this.nodePath != null) {
            this.setName(this.nodePath.getFileName().toString());
            this.setUrl(this.nodePath.getRelativePath().toString());
        }

        return this;
    }

    /**
     * This property ensures that the file/directory is actually accessible (can be read/written to) on disk.
     */
    private Boolean isAccessible = false;

    @JsonProperty("isAccessible")
    public Boolean getIsAccessible() {
        return this.getNodePath() != null && this.getNodePath().isValidNode();
    }

    /**
     * This property is set to true only if the node has been committed/updated by user.
     */
    private Boolean isDirectory = null;

    @JsonProperty("isDirectory")
    public Boolean getIsDirectory() {
        return this.isDirectory != null ? this.isDirectory : this.children != null;
    }

    /**
     * @throws StorageServiceException if the node doesn't have a parent (root node being an exception!)
     */
    @PreUpdate
    @PrePersist
    public void preCommitChecks() throws StorageServiceException {
        this.isAccessible = this.getIsAccessible();
        this.isDirectory = this.getIsDirectory();

        if (this.isAccessible != true) {
            throw new StorageServiceException("Inaccessible node cannot be persisted to the database.".formatted());
        }

        if (this.getParent() == null && !this.getNodePath().getRelativePath().toString().isBlank()) {
            throw new StorageServiceException("Inaccessible node cannot be persisted to the database.".formatted());
        }
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
            ", isDirectory=" + this.getIsDirectory() +
            ", isAccessible=" + this.getIsAccessible() +
            ", description=" + this.getDescription() +
            '}';
    }
}
