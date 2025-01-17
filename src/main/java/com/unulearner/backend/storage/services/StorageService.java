package com.unulearner.backend.storage.services;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Comparator;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.unulearner.backend.storage.interfaces.StorageServiceInterface;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import com.unulearner.backend.storage.extensions.NodePath;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.properties.StorageProperties;
import com.unulearner.backend.storage.repository.StorageRepository;

import java.util.NoSuchElementException;

@Service
public class StorageService implements StorageServiceInterface {

    private final StorageProperties storageProperties;
    private final StorageRepository storageRepository;

    public StorageService(StorageProperties storageProperties, StorageRepository storageRepository) {
        this.storageProperties = storageProperties;
        this.storageRepository = storageRepository;
    }

    @Override
    public StorageNode createNewStorageNode(StorageNode parent, List<StorageNode> children, NodePath storageNodePath, UUID user, UUID group, String stringPermissions) {
        if (stringPermissions == null) {
            Integer defaultPermissions = Integer.parseInt(children != null ? storageProperties.getDefaultNewDirectoryPermissionFlags() : storageProperties.getDefaultNewFilePermissionFlags(), 8);
            Integer defaultUmask = Integer.parseInt(storageProperties.getDefaultPermissionFlagsUmask(), 8);
            Integer integerPermissions = defaultPermissions & ~defaultUmask;
            
            stringPermissions = String.format("%o", integerPermissions);
        }

        final StorageNode newStorageNode = new StorageNode()
            .setParent(parent)
            .setChildren(children)
            .setNodePath(storageNodePath)
            .setPermissions(stringPermissions)
            .setUser(user != null ? user : storageProperties.getDefaultUserUUID())
            .setGroup(group != null ? group : storageProperties.getDefaultGroupUUID())
            .setDescription(children != null ? storageProperties.getRecoveredDirectoryDescription() : storageProperties.getRecoveredFileDescription());

        return newStorageNode;
    }

    @Override
    public StorageNode createRootStorageNode(List<StorageNode> children, NodePath storageNodePath) {
        Integer defaultPermissions = Integer.parseInt(storageProperties.getDefaultNewDirectoryPermissionFlags(), 8);
        Integer defaultUmask = Integer.parseInt(storageProperties.getDefaultPermissionFlagsUmask(), 8);
        Integer integerPermissions = defaultPermissions & ~defaultUmask;
        String stringPermissions = "%o".formatted(integerPermissions);

        final StorageNode newStorageNode = new StorageNode()
            .setChildren(children)
            .setParent(null)
            .setNodePath(storageNodePath)
            .setPermissions(stringPermissions)
            .setUser(storageProperties.getRootUserUUID())
            .setGroup(storageProperties.getRootUserUUID())
            .setDescription(storageProperties.getRootDirectoryDescription());

        return newStorageNode;
    }

    @Override
    public StorageNode retrieveStorageNodeByURL(String url) throws NoSuchElementException {
        final Optional<StorageNode> nullableStorageNode = this.storageRepository.findByUrl(url);

        if (nullableStorageNode.isPresent()) {
            return nullableStorageNode.get();
        }

        throw new NoSuchElementException("Matching record for the provided URL '%s' couldn't be found in the database.".formatted(url));
    }

    @Override
    public List<StorageNode> retrieveChildrenStorageNodes(StorageNode storageNode) {
        return storageRepository.findAllByParent(storageNode);
    }

    @Override
    @Transactional
    public StorageNode saveStorageNode(StorageNode storageNode) {
        return storageRepository.save(storageNode).setNodePath(storageNode.getNodePath()).setChildren(storageNode.getChildren()).setParent(storageNode.getParent());
    }

    @Override
    public void deleteStorageNode(StorageNode storageNode) {
        /* TODO: rethink this... should we actually delete it from the database? */
        storageRepository.delete(storageNode);
    }

    @Override
    public Comparator<StorageNode> getStorageComparator() {
        return Comparator.comparing((StorageNode iNode) -> iNode.getIsDirectory() ? 0 : 1).thenComparing(StorageNode::getName);
    }

    @Override
    public NodePath getRootDirectory() throws Exception {
        final String rootDirectoryStringPath = this.storageProperties.getRootDirectory();
        final NodePath rootDirectoryNodePath = new NodePath(Files.createDirectories(Path.of(rootDirectoryStringPath)));

        if (!rootDirectoryNodePath.isValidDirectory(false)) {
            throw new RuntimeException("Root '%s' directory is inaccessible or it doesn't exist.".formatted(rootDirectoryStringPath));
        }

        return rootDirectoryNodePath;
    }
}