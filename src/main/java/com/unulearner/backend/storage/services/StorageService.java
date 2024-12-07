package com.unulearner.backend.storage.services;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.extensions.NodePath;
import com.unulearner.backend.storage.interfaces.StorageServiceInterface;
import com.unulearner.backend.storage.properties.StorageProperties;
import com.unulearner.backend.storage.repository.StorageRepository;

@Service
@ConditionalOnMissingBean(StorageServiceInterface.class)
public class StorageService implements StorageServiceInterface {

    private final StorageProperties storageProperties;
    private final StorageRepository storageRepository;

    public StorageService(StorageProperties storageProperties, StorageRepository storageRepository) {
        this.storageProperties = storageProperties;
        this.storageRepository = storageRepository;
    }

    @Override
    public StorageNode createNewStorageNode(StorageNode parent, List<StorageNode> children, NodePath storageNodePath, UUID user, UUID group, Short permissions) {
        Short defaultPermissions = (short) ((children != null ? storageProperties.getDefaultNewDirectoryPermissionFlags() : storageProperties.getDefaultNewFilePermissionFlags()) & ~storageProperties.getDefaultPermissionFlagsUmask());

        final StorageNode newStorageNode = new StorageNode()
            .setParent(parent)
            .setChildren(children)
            .setNodePath(storageNodePath)
            .setPermissions(defaultPermissions)
            .setUser(user != null ? user : storageProperties.getDefaultUserUUID())
            .setGroup(group != null ? group : storageProperties.getDefaultGroupUUID());
            /* TODO: maybe add the ability to set permissions at creation? */
        return newStorageNode;
    }

    @Override
    public StorageNode createRootStorageNode(List<StorageNode> children, NodePath storageNodePath) {
        Short rootPermissions = (short) (storageProperties.getDefaultNewDirectoryPermissionFlags() & ~storageProperties.getDefaultPermissionFlagsUmask());

        final StorageNode newStorageNode = new StorageNode()
            .setChildren(children)
            .setParent(null)
            .setNodePath(storageNodePath)
            .setPermissions(rootPermissions)
            .setUser(storageProperties.getRootUserUUID())
            .setGroup(storageProperties.getRootUserUUID());

        return newStorageNode;
    }

    @Override
    public StorageNode retrieveStorageNodeByURL(String url) throws NullPointerException {
        final Optional<StorageNode> nullableStorageNode = this.storageRepository.findByUrl(url);

        if (nullableStorageNode.isPresent()) {
            return nullableStorageNode.get();
        }

        throw new NullPointerException("Matching record for the provided URL '%s' couldn't be found in the database.".formatted(url));
    }

    @Override
    public List<StorageNode> retrieveChildrenStorageNodes(StorageNode storageNode) {
        return storageRepository.findAllByParent(storageNode);
    }

    @Override
    public Comparator<StorageNode> getStorageNodeSortingComparator() {
        return Comparator.comparing((StorageNode iNode) -> iNode.isDirectory() ? 0 : 1).thenComparing(StorageNode::getName);
    }

    @Override
    public Comparator<StorageNode> getStorageNodeIndexComparator() {
        return Comparator.comparing(StorageNode::getName);
    }

    @Override
    @Transactional
    public StorageNode saveStorageNode(StorageNode storageNode) {
        StorageNode transientStorageNode = storageNode;

        if (storageNode.getId() != null) { 
            Optional<StorageNode> optionalManagedStorageNode = storageRepository.findById(storageNode.getId());
            
            if (optionalManagedStorageNode.isPresent() == false) {
                transientStorageNode = optionalManagedStorageNode.get().mergeNode(storageNode);
            }
        }

        return storageRepository.save(transientStorageNode).mergeNode(storageNode);
    }

    @Override
    public void deleteStorageNode(StorageNode storageNode) {
        storageRepository.delete(storageNode);
    }

    @Override
    public NodePath getRootDirectory() throws Exception {
        final String rootDirectoryStringPath = this.storageProperties.getRootDirectory();
        final NodePath rootDirectoryNodePath = new NodePath(Files.createDirectories(Paths.get(rootDirectoryStringPath)));

        if (!rootDirectoryNodePath.isValidDirectory(false)) {
            throw new RuntimeException("Root '%s' directory is inaccessible or it doesn't exist.".formatted(rootDirectoryStringPath));
        }

        return rootDirectoryNodePath;
    }
}