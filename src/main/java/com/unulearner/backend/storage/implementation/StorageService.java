package com.unulearner.backend.storage.implementation;

import java.util.Comparator;
import java.util.ArrayList;
import java.nio.file.Files;
import java.util.Optional;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import com.unulearner.backend.storage.repository.Repository;
import com.unulearner.backend.storage.specification.StorageInterface;
import com.unulearner.backend.storage.config.StorageProperties;
import com.unulearner.backend.storage.formalization.EntryPath;
import com.unulearner.backend.storage.model.Entry;

@Service
public class StorageService implements StorageInterface {

    private final StorageProperties storageProperties;
    private final Repository storageRepository;

    public StorageService(StorageProperties storageProperties, Repository storageRepository) {
        this.storageProperties = storageProperties;
        this.storageRepository = storageRepository;
    }

    @Override
    public Entry createNewEntry(Entry parent, List<Entry> children, EntryPath entryPath, UUID user, UUID group, String permissions) {
        if (permissions == null) {
            Integer defaultPermissions = Integer.parseInt(children != null ? storageProperties.getDefaultNewDirectoryPermissionFlags() : storageProperties.getDefaultNewFilePermissionFlags(), 8);
            Integer defaultUmask = Integer.parseInt(storageProperties.getDefaultPermissionFlagsUmask(), 8);
            Integer integerPermissions = defaultPermissions & ~defaultUmask;
            
            permissions = String.format("%o", integerPermissions);
        }

        final Entry newStorageNode = new Entry()
            .setParent(parent)
            .setChildren(children)
            .setEntryPath(entryPath)
            .setPermissions(permissions)
            .setUser(user != null ? user : storageProperties.getDefaultUserUUID())
            .setGroup(group != null ? group : storageProperties.getDefaultGroupUUID())
            .setDescription(children != null ? storageProperties.getRecoveredDirectoryDescription() : storageProperties.getRecoveredFileDescription());

        return newStorageNode;
    }

    @Override
    public Entry createRootEntry(EntryPath entryPath) {
        Integer defaultPermissions = Integer.parseInt(storageProperties.getDefaultNewDirectoryPermissionFlags(), 8);
        Integer defaultUmask = Integer.parseInt(storageProperties.getDefaultPermissionFlagsUmask(), 8);
        Integer integerPermissions = defaultPermissions & ~defaultUmask;
        String permissions = "%o".formatted(integerPermissions);

        final Entry newStorageNode = new Entry()
            .setParent(null)
            .setEntryPath(entryPath)
            .setPermissions(permissions)
            .setChildren(new ArrayList<>())
            .setUser(storageProperties.getRootUserUUID())
            .setGroup(storageProperties.getRootUserUUID())
            .setDescription(storageProperties.getRootDirectoryDescription());

        return newStorageNode;
    }

    @Override
    public Optional<Entry> searchEntryByURL(String relativePath) {
        return this.storageRepository.findByUrl(relativePath);
    }

    @Override
    public List<Entry> retrieveChildEntries(Entry storageNode) {
        return storageRepository.findAllByParent(storageNode);
    }

    @Override
    @Transactional
    public Entry persistEntry(Entry storageNode) {
        return storageRepository.save(storageNode).setEntryPath(storageNode.getEntryPath()).setChildren(storageNode.getChildren()).setParent(storageNode.getParent());
    }

    @Override
    public void deleteEntry(Entry storageNode) {
        /* TODO: rethink this... should we actually delete it from the database? */
        storageRepository.delete(storageNode);
    }

    @Override
    public Comparator<Entry> getStorageComparator() {
        return Comparator.comparing((Entry iNode) -> iNode.getIsDirectory() ? 0 : 1).thenComparing(Entry::getName);
    }

    @Override
    public EntryPath getRootDirectoryPath() throws Exception {
        final String rootDirectoryStringPath = this.storageProperties.getRootDirectoryUrl();
        final EntryPath rootDirectoryNodePath = new EntryPath(Files.createDirectories(Path.of(rootDirectoryStringPath)));

        if (!rootDirectoryNodePath.isValidDirectory(false)) {
            throw new RuntimeException("Root '%s' directory is inaccessible or it doesn't exist.".formatted(rootDirectoryStringPath));
        }

        return rootDirectoryNodePath;
    }
}