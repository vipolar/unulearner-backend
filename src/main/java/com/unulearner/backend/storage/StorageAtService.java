package com.unulearner.backend.storage;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.unulearner.backend.storage.extensions.NodePath;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.services.StorageService;
import com.unulearner.backend.storage.properties.StorageProperties;
import com.unulearner.backend.storage.repository.StorageRepository;

@Service
public class StorageAtService extends StorageService {

    private final StorageProperties storageProperties;
    private final StorageRepository storageRepository;

    public StorageAtService(StorageProperties storageProperties, StorageRepository storageRepository) {
        super(storageProperties, storageRepository);
        this.storageProperties = storageProperties;
        this.storageRepository = storageRepository;
    }

    @Override
    public StorageNode createNewStorageNode(StorageNode parent, List<StorageNode> children, NodePath storageNodePath, UUID user, UUID group, Short permissions) {
        Short defaultPermissions = (short) ((children != null ? storageProperties.getDefaultNewDirectoryPermissionFlags() : storageProperties.getDefaultNewFilePermissionFlags()) & ~storageProperties.getDefaultPermissionFlagsUmask());

        final StorageEntry newStorageNode = (StorageEntry) new StorageEntry()
            .setParent(parent)
            .setChildren(children)
            .setNodePath(storageNodePath)
            .setPermissions(defaultPermissions)
            .setUser(user != null ? user : storageProperties.getDefaultUserUUID())
            .setGroup(group != null ? group : storageProperties.getDefaultGroupUUID());

        if (children != null) {
            newStorageNode.setDescription(storageProperties.getRecoveredDirectoryDescription());
        } else {
            newStorageNode.setDescription(storageProperties.getRecoveredFileDescription());
        }

        return newStorageNode;
    }

    @Override
    public StorageNode createRootStorageNode(List<StorageNode> children, NodePath storageNodePath) {
        Short rootPermissions = (short) (storageProperties.getDefaultNewDirectoryPermissionFlags() & ~storageProperties.getDefaultPermissionFlagsUmask());

        final StorageEntry newStorageNode = (StorageEntry) new StorageEntry()
            .setChildren(children)
            .setParent(null)
            .setNodePath(storageNodePath)
            .setPermissions(rootPermissions)
            .setUser(storageProperties.getRootUserUUID())
            .setGroup(storageProperties.getRootUserUUID());

        newStorageNode.setDescription(storageProperties.getRootDirectoryDescription());

        return newStorageNode;
    }
}
