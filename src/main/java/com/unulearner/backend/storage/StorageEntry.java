package com.unulearner.backend.storage;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;

import com.unulearner.backend.storage.entities.StorageNode;

@Entity
@DiscriminatorValue("StorageEntry")
public class StorageEntry extends StorageNode {
    /**
     * This property serves no purpose in file system management.
     * This property is purely for the human eyes or the search bots.
     */
    @Column(name = "description", columnDefinition = "TEXT", nullable = true)
    private String description;

    public StorageEntry() {
        super();
    }

    public String getDescription() {
        return this.description;
    }

    public StorageEntry setDescription(String description) {
        this.description = description;

        return this;
    }

    @Override
    public String toString() {
        return "StorageEntry{" +
            "id=" + this.getId() +
            ", url='" + this.getUrl() + '\'' +
            ", name='" + this.getName() + '\'' +
            ", user=" + this.getUser() +
            ", group=" + this.getGroup() +
            ", created=" + this.getCreated() +
            ", updated=" + this.getUpdated() +
            ", isDirectory=" + this.isDirectory() +
            ", description=" + this.getDescription() +
            ", isConfirmed=" + this.getIsConfirmed() +
            ", isAccessible=" + this.getIsAccessible() +
            '}';
    }
}
