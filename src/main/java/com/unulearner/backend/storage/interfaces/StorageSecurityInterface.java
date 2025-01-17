package com.unulearner.backend.storage.interfaces;

import java.util.UUID;

import com.unulearner.backend.storage.entities.StorageNode;

public interface StorageSecurityInterface {
    public Boolean userHasRequiredPermissions(StorageNode targetStorageNode, boolean read, boolean write, boolean execute);
    public Boolean userBelongsToTheOwningGroupOfTheNode(StorageNode targetStorageNode);
    public Boolean userIsTheOwnerOfTheNode(StorageNode targetStorageNode);
    public Boolean userCanInteractWithTheTargetUser(UUID userUUID);
    public Boolean userBelongsToTheTargetGroup(UUID groupUUID);
    public Boolean userHasRootPrivilages();
}
