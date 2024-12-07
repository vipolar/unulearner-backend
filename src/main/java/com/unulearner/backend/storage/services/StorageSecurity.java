package com.unulearner.backend.storage.services;

import java.util.Collection;
import java.util.UUID;

import com.unulearner.backend.security.user.Credentials;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.interfaces.StorageSecurityInterface;

import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Service
//@ConditionalOnMissingBean(StorageSecurityInterface.class)
public class StorageSecurity implements StorageSecurityInterface {
    private final Credentials credentials;

    public StorageSecurity(Credentials credentials) {
        this.credentials = credentials;
    }

    public Boolean userHasRequiredPermissions(StorageNode targetStorageNode, Boolean read, Boolean write, Boolean execute) {
        final UUID user = this.credentials.getUser();
        final Short fullPermissions = targetStorageNode.getPermissions();
        final Collection<UUID> userGroups = this.credentials.getUserGroups();

        final int userPermission = fullPermissions / 100;
        final int groupPermission = (fullPermissions / 10) % 10;
        final int otherPermission = fullPermissions % 10;

        Boolean readConfirmed = (otherPermission & 4) != 0;
        Boolean writeConfirmed = (otherPermission & 2) != 0;
        Boolean executeConfirmed = (otherPermission & 1) != 0;

        if (!readConfirmed || !writeConfirmed || !executeConfirmed) {
            if (userGroups.contains(targetStorageNode.getGroup())) {
                if (!readConfirmed) {
                    readConfirmed = (groupPermission & 4) != 0;
                }
                if (!writeConfirmed) {
                    writeConfirmed = (groupPermission & 2) != 0;
                }
                if (!executeConfirmed) {
                    executeConfirmed = (groupPermission & 1) != 0;
                }
            }
        }

        if (!readConfirmed || !writeConfirmed || !executeConfirmed) {
            if (user.equals(targetStorageNode.getUser())) {
                if (!readConfirmed) {
                    readConfirmed = (userPermission & 4) != 0;
                }
                if (!writeConfirmed) {
                    writeConfirmed = (userPermission & 2) != 0;
                }
                if (!executeConfirmed) {
                    executeConfirmed = (userPermission & 1) != 0;
                }
            }
        }

        if ((read != null && read && !readConfirmed) ||
            (write != null && write && !writeConfirmed) ||
            (execute != null && execute && !executeConfirmed)) {
            return false;
        }
    
        return true;
    }    
}
