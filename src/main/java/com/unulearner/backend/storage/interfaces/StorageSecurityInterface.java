package com.unulearner.backend.storage.interfaces;

import com.unulearner.backend.storage.entities.StorageNode;

public interface StorageSecurityInterface {
    public Boolean userHasRequiredPermissions(StorageNode targetStorageNode, Boolean read, Boolean write, Boolean execute);
}
