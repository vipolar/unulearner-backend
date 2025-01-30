package com.unulearner.backend.storage.implementation;

import java.util.Collection;
import java.util.UUID;

import com.unulearner.backend.storage.specification.SecurityInterface;
import com.unulearner.backend.security.user.Credentials;
import com.unulearner.backend.storage.config.StorageProperties;
import com.unulearner.backend.storage.model.Entry;

import org.springframework.stereotype.Service;

@Service
public class SecurityService implements SecurityInterface {
    private final StorageProperties storageProperties;
    private final Credentials credentials;

    public SecurityService(StorageProperties storageProperties, Credentials credentials) {
        this.storageProperties = storageProperties;
        this.credentials = credentials;
    }

    @Override
    public Boolean userHasRequiredPermissions(Entry targetStorageNode, boolean readRequired, boolean writeRequired, boolean executeRequired) {
        final UUID userUUID = this.credentials.getUser();
        final String fullPermissions = targetStorageNode.getPermissions();
        final Integer intPermissions = Integer.parseInt(fullPermissions, 8);
        final Collection<UUID> userGroups = this.credentials.getUserGroups();

        if (userUUID.equals(this.storageProperties.getRootUserUUID())) {
            return true; /* root bypasses all! */
        }

        if (userUUID.equals(targetStorageNode.getUser())) {
            final Integer ownerPermissions = (intPermissions / 100) % 10;
            final Boolean readBitSet = (ownerPermissions & 4) != 0;
            final Boolean writeBitSet = (ownerPermissions & 2) != 0;
            final Boolean executeBitSet = (ownerPermissions & 1) != 0;

            if ((readRequired && !readBitSet) || (writeRequired && !writeBitSet) || (executeRequired && !executeBitSet)) {
                return false;
            }

            return true;
        } else if (userGroups.contains(targetStorageNode.getGroup())) {
            final Integer groupPermissions = (intPermissions / 10) % 10;
            final Boolean readBitSet = (groupPermissions & 4) != 0;
            final Boolean writeBitSet = (groupPermissions & 2) != 0;
            final Boolean executeBitSet = (groupPermissions & 1) != 0;

            if ((readRequired && !readBitSet) || (writeRequired && !writeBitSet) || (executeRequired && !executeBitSet)) {
                return false;
            }

            return true;
        } else {
            final Integer otherPermissions = intPermissions % 10;
            final Boolean readBitSet = (otherPermissions & 4) != 0;
            final Boolean writeBitSet = (otherPermissions & 2) != 0;
            final Boolean executeBitSet = (otherPermissions & 1) != 0;

            if ((readRequired && !readBitSet) || (writeRequired && !writeBitSet) || (executeRequired && !executeBitSet)) {
                return false;
            }

            return true;
        }
    }

    @Override
    public Boolean userBelongsToTheOwningGroupOfTheEntry(Entry targetStorageNode) {
        final Collection<UUID> userGroups = this.credentials.getUserGroups();

        if (userGroups.contains(targetStorageNode.getGroup())) {
            return true;
        }

        return false;
    }

    @Override
    public Boolean userIsTheOwnerOfTheEntry(Entry targetStorageNode) {
        final UUID user = this.credentials.getUser();

        if (user.equals(targetStorageNode.getUser())) {
            return true;
        }

        return false;
    }

    @Override
    public Boolean userCanInteractWithTheUser(UUID targetUUID) {
        return true;
    }

    @Override
    public Boolean userBelongsToTheGroup(UUID groupUUID) {
        final Collection<UUID> userGroups = this.credentials.getUserGroups();

        if (userGroups.contains(groupUUID)) {
            return true;
        }

        return false;
    }

    @Override
    public Boolean userHasRootPrivilages() {
        return this.credentials.getUser().equals(this.storageProperties.getRootUserUUID());
    }
}
