package com.unulearner.backend.storage;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.StandardCopyOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Collections;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.unulearner.backend.storage.model.Entry;
import com.unulearner.backend.storage.formalization.EntryPath;
import com.unulearner.backend.storage.specification.StorageInterface;
import com.unulearner.backend.storage.specification.SecurityInterface;

import com.unulearner.backend.storage.exceptions.StorageEntryException;
import com.unulearner.backend.storage.exceptions.entry.EntryNotFoundException;
import com.unulearner.backend.storage.exceptions.entry.EntryInaccessibleException;
import com.unulearner.backend.storage.exceptions.entry.EntryPublishingRaceException;
import com.unulearner.backend.storage.exceptions.entry.EntryToParentRelationException;
import com.unulearner.backend.storage.exceptions.entry.EntryPhysicalCreationException;
import com.unulearner.backend.storage.exceptions.entry.EntryTypeNotSupportedException;
import com.unulearner.backend.storage.exceptions.entry.EntryTypeInDatabaseMismatchException;
import com.unulearner.backend.storage.exceptions.entry.EntryInsufficientPermissionsException;

import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.security.InvalidParameterException;
import java.io.IOException;

@Service
public class Storage {
    private final Logger logger = LoggerFactory.getLogger(Storage.class);
    private final SecurityInterface securityInterface;
    private final StorageInterface storageInterface;
    private final HashMap<UUID, Entry> entryCache;
    private final EntryPath rootEntryPath;
    private final Entry rootEntry;

    public Storage(SecurityInterface securityInterface, StorageInterface storageInterface) {
        final Deque<Entry> directoryStackDeque = new ArrayDeque<Entry>();

        this.entryCache = new HashMap<UUID, Entry>();
        this.securityInterface = securityInterface;
        this.storageInterface = storageInterface;

        try {
            this.rootEntryPath = this.storageInterface.getRootDirectoryPath();
            Files.walkFileTree(rootEntryPath.getPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dirPath, BasicFileAttributes attrs) {
                    try {
                        final EntryPath dirEntryPath = rootEntryPath.resolveFromRoot(dirPath);
                        final Entry stackLastEntry = directoryStackDeque.peekLast();
                        /* Fuck Java, this is final! */ Entry entry = null;

                        if (!dirEntryPath.isValidDirectory()) {
                            throw new EntryInaccessibleException("Directory '%s' is inaccessible".formatted(dirEntryPath.getRelativePath().toString()));
                        }

                        if (stackLastEntry == null) { /* null parent is only allowed in the case of the root */
                            if (!entryCache.isEmpty()) {
                                throw new RuntimeException("Directory '%s' cannot be root as the root already exists".formatted(dirEntryPath.getPath().toString()));
                            }

                            if (!rootEntryPath.equals(dirEntryPath)) {
                                throw new RuntimeException("Directory '%s' cannot be root as it doesn't match the provided root path".formatted(dirEntryPath.getPath().toString()));
                            }

                            try {
                                entry = storageInterface.searchEntryByURL(dirEntryPath.getRelativePath().toString()).orElseThrow(() -> new EntryNotFoundException("Root entry not found".formatted(dirPath.toString()))).setEntryPath(dirEntryPath);
                            } catch (EntryNotFoundException exception) {
                                logger.info("Database entry for the root directory not found. Creating root directory database entry...".formatted());
                                entry = storageInterface.persistEntry(storageInterface.createRootEntry(rootEntryPath));
                            } catch (Exception exception) {
                                throw new RuntimeException(exception.getMessage(), exception.getCause());
                            }

                            entryCache.put(entry.getId(), entry);
                            directoryStackDeque.offer(entry);

                            logger.info("Storage root has been successfully initialized at %s".formatted(dirPath.toString()));
                            return FileVisitResult.CONTINUE;
                        }

                        try {
                            entry = storageInterface.searchEntryByURL(dirEntryPath.getRelativePath().toString()).orElseThrow(() -> new EntryNotFoundException("Database entry for directory '%s' not found".formatted(dirPath.toString()))).setEntryPath(dirEntryPath);

                            if (!stackLastEntry.getId().equals(entry.getParent().getId())) {
                                throw new EntryToParentRelationException("Directory is supposedly a child of directory '%s' but the relationship is not mirrored on the persistent level".formatted(stackLastEntry.getUrl()));
                            }

                            if (entry.getIsDirectory() == null || entry.getIsDirectory() != true) {
                                throw new EntryTypeInDatabaseMismatchException("Directory '%s' type doesn't match the type persisted to the database".formatted(entry.getUrl()));
                            }

                            entry.setChildren(storageInterface.retrieveChildEntries(entry));
                            if (!entry.getChildren().isEmpty()) { /* Sorting at this stage and then inserting accordingly seems like a better idea than throwing it all in together and sorting on postDirectoryVisit */
                                Collections.sort(entry.getChildren(), storageInterface.getStorageComparator());
                            }
                        } catch (StorageEntryException exception) {
                            if (entry != null && exception instanceof EntryTypeInDatabaseMismatchException) {
                                //TODO: do something with the old entry...
                            }

                            logger.info("%s: %s. Creating new database entry...".formatted(exception.getClass().getSimpleName(), exception.getMessage()));
                            entry = storageInterface.persistEntry(storageInterface.createNewEntry(stackLastEntry, new ArrayList<>(), dirEntryPath, null, null, null));
                        } catch (Exception exception) {
                            throw new Exception(exception.getMessage(), exception.getCause());
                        }

                        final Integer iEntry = Collections.binarySearch(stackLastEntry.getChildren(), entry, storageInterface.getStorageComparator());
                        if (iEntry >= 0) {
                            stackLastEntry.getChildren().set(iEntry, entry);
                        } else {
                            stackLastEntry.getChildren().add((-iEntry - 1), entry);
                        }


                        if (entryCache.put(entry.getId(), entry) != null) {
                            throw new RuntimeException("Directory '%s' already exists in the storage hashmap!".formatted(dirEntryPath.getRelativePath().toString()));
                        }

                        directoryStackDeque.offer(entry);
                    } catch (Exception exception) {
                        if (exception instanceof RuntimeException) {
                            throw new RuntimeException(exception.getMessage(), exception.getCause());
                        }

                        logger.warn("Failed to add directory '%s' to the storage tree: %s".formatted(dirPath.toString(), exception.getMessage()));
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    logger.info("Directory '%s' was successfully added to the storage tree".formatted(dirPath.toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) {
                    try {
                        final EntryPath fileEntryPath = rootEntryPath.resolveFromRoot(filePath);
                        final Entry stackLastEntry = directoryStackDeque.peekLast();
                        /* Fuck Java, this is final! */ Entry entry = null;

                        if (!fileEntryPath.isValidFile()) {
                            throw new EntryInaccessibleException("File '%s' is inaccessible".formatted(fileEntryPath.getRelativePath().toString()));
                        }

                        try {
                            entry = storageInterface.searchEntryByURL(fileEntryPath.getRelativePath().toString()).orElseThrow(() -> new EntryNotFoundException("Database entry for file '%s' not found".formatted(filePath.toString()))).setEntryPath(fileEntryPath);

                            if (!stackLastEntry.getId().equals(entry.getParent().getId())) {
                                throw new EntryToParentRelationException("File is supposedly a child of directory '%s' but the relationship is not mirrored on the persistent level".formatted(stackLastEntry.getUrl()));
                            }

                            if (entry.getIsDirectory() == null || entry.getIsDirectory() != false) {
                                throw new EntryTypeInDatabaseMismatchException("File '%s' type doesn't match the type persisted to the database".formatted(entry.getUrl()));
                            }
                        } catch (StorageEntryException exception) {
                            if (entry != null && exception instanceof EntryTypeInDatabaseMismatchException) {
                                //TODO: do something with the old entry...
                            }

                            logger.info("%s: %s. Creating new database entry...".formatted(exception.getClass().getSimpleName(), exception.getMessage()));
                            entry = storageInterface.persistEntry(storageInterface.createNewEntry(stackLastEntry, null, fileEntryPath, null, null, null));
                        } catch (Exception exception) {
                            throw new Exception(exception.getMessage(), exception.getCause());
                        }     
                        
                        final Integer iEntry = Collections.binarySearch(stackLastEntry.getChildren(), entry, storageInterface.getStorageComparator());
                        if (iEntry >= 0) {
                            stackLastEntry.getChildren().set(iEntry, entry);
                        } else {
                            stackLastEntry.getChildren().add((-iEntry - 1), entry);
                        }

                        if (entryCache.put(entry.getId(), entry) != null) {
                            throw new RuntimeException("File '%s' already exists in the storage hashmap".formatted(fileEntryPath.getRelativePath().toString()));
                        }
                    } catch (Exception exception) {
                        if (exception instanceof RuntimeException) {
                            throw new RuntimeException(exception.getMessage(), exception.getCause());
                        }

                        logger.warn("Failed to add file '%s' to the storage tree: %s".formatted(filePath.toString(), exception.getMessage()));
                        return FileVisitResult.CONTINUE;
                    }

                    logger.info("File '%s' was successfully added to the storage tree".formatted(filePath.toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path filePath, IOException exception) {
                    if (exception != null) {
                        logger.warn("Failed to visit file '%s': %s".formatted(filePath.toString(), exception.getMessage()));
                        return FileVisitResult.CONTINUE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dirPath, IOException exception) {
                    if (exception != null) {
                        logger.warn("Failed to traverse the '%s' directory: %s".formatted(dirPath.toString(), exception.getMessage()));
                        return FileVisitResult.CONTINUE;
                    }

                    final Entry dirEntry = directoryStackDeque.peekLast();
                    long fileCount = 0, directoryCount = 0, visitedCount = 0, accessibleCount = 0;
                    for (Entry entry : dirEntry.getChildren()) {
                        visitedCount++;

                        if (entry.getIsAccessible()) {
                            accessibleCount++;
                        }

                        if (entry.getIsDirectory()) {
                            directoryCount++;
                        } else {
                            fileCount++;
                        }
                    }

                    if (directoryStackDeque.size() > 1) {
                        directoryStackDeque.removeLast();
                    }

                    logger.info("Directory '%s' visited successfully. Total entries: %d. Directory entries: %d. File entries: %d. Accessible entries: %d".formatted(dirPath.toString(), visitedCount, directoryCount, fileCount, accessibleCount));
                    return FileVisitResult.CONTINUE;
                }
            });

            if ((this.rootEntry = directoryStackDeque.pollLast()) == null) {
                throw new RuntimeException("No discernable root entry was detected".formatted());
            }

            if (!directoryStackDeque.isEmpty()) {
                throw new RuntimeException("%d unhandled directories left in the deque".formatted(directoryStackDeque.size()));
            }
        } catch (Exception exception) {
            /* If it got to here then we've got no choice but to crash it! */
            logger.error("Fatal error: %s".formatted(exception.getMessage()));
            logger.error("Failed to build the storage tree".formatted());
            logger.error("Crashing the application...".formatted());

            throw new RuntimeException(exception.getMessage(), exception.getCause());
        }
    }

    /**
     * Retrieves the root entry of the storage tree.
     *
     * The root entry serves as the top-level directory and cannot be deleted or modified 
     * in the same way as other entries.
     *
     * @return The root {@code Entry} of the storage tree.
     */
    public Entry getRootEntry() {
        return this.rootEntry;
    }

    /**
     * Retrieves an entry from the quick-access cache by its UUID.
     *
     * This method checks the in-memory cache for a matching entry. If no match is found,
     * it returns {@code null} without querying persistent storage.
     *
     * @param targetEntryUUID The UUID of the entry to retrieve.
     * @return The matching {@code Entry} if found, or {@code null} if no match exists.
     */
    public Entry retrieveEntry(UUID targetEntryUUID) {
        return this.entryCache.get(targetEntryUUID);
    }

    /**
     * Recovers an existing entry by name, whether it exists as a full-blown entry 
     * in the tree or as a physical file on the drive.
     * 
     * If the entry is already catalogued in the parent entry, it is returned.
     * If it exists physically but is not catalogued, a new entry is created and linked.
     *
     * @param targetEntryName The name of the entry to search for within the parent directory.
     * @param destinationEntry The existing parent entry to which the recovered entry will be attached.
     * @return A newly created entry representing the recovered file or directory.
     * @throws InvalidParameterException If the target name is blank, or the destination entry is invalid or not a directory.
     * @throws EntryInaccessibleException If the file is inaccessible or does not exist.
     * @throws EntryTypeNotSupportedException If the file type is unsupported.
     * @throws Exception If an unexpected error occurs during recovery.
     */
    public Entry recoverEntry(String targetEntryName, Entry destinationEntry) throws Exception {
        if (targetEntryName == null || targetEntryName.isBlank()) {
            throw new InvalidParameterException("Target entry name cannot be blank".formatted());
        }

        if (destinationEntry == null || destinationEntry.getId() == null || destinationEntry.getEntryPath() == null) {
            throw new InvalidParameterException("Destination entry is invalid".formatted());
        } else if (!destinationEntry.getIsDirectory() || !destinationEntry.getEntryPath().isValidDirectory()) {
            throw new InvalidParameterException("Destination entry is not a directory".formatted());
        }

        /* If entry is already there (whether the file is actually accessible or not) */
        for (int iEntry = 0; iEntry < destinationEntry.getChildren().size(); iEntry++) {
            if (destinationEntry.getChildren().get(iEntry).getName().equals(targetEntryName)) {
                return destinationEntry.getChildren().get(iEntry);
            }
        }

        final Entry newEntry; /* If entry is not there but the file supposedly is... */
        final EntryPath targetPath = destinationEntry.getEntryPath().resolve(targetEntryName);
        if (targetPath.isValidDirectory()) {
            newEntry = this.storageInterface.createNewEntry(destinationEntry, new ArrayList<>(), targetPath, null, null, null);
        } else if (targetPath.isValidFile()) {
            newEntry = this.storageInterface.createNewEntry(destinationEntry, null, targetPath, null, null, null);
        } else if (targetPath.isValid()) {
            throw new EntryTypeNotSupportedException("Entry '%s' is of unsupported file type".formatted(targetPath.getPath().toString()));
        } else {
            throw new EntryInaccessibleException("Entry '%s' is inaccessible or nonexistent".formatted(targetPath.getPath().toString()));
        }

        /* TODO: bring this entry to the attention of the admin! */
        logger.warn("%s '%s' has been recovered and requires attention".formatted(newEntry.getIsDirectory() ? "Directory" : "File", newEntry.getUrl(), newEntry.getIsDirectory() ? "created" : "uploaded"));
        return newEntry;
    }

    /**
     * Creates a new entry with a valid physical path attached to it.
     * 
     * If a file is provided, it is moved to the target location. If no file is provided, a new directory is created.
     * The resulting entry remains unpublished.
     * 
     * @param destinationEntry The parent directory where the new entry will be created.
     * @param newEntryName The name of the new entry (file or directory).
     * @param newEntryFile The file to be written to disk (only applicable for file entries, null for directories).
     * @return The newly created entry with a valid path to its physical counterpart (unpublished).
     * @throws IOException If the entry creation attempt fails due to an I/O issue.
     * @throws InvalidParameterException If any provided parameters are invalid.
     * @throws FileAlreadyExistsException If an entry with the same name already exists in the destination.
     * @throws EntryInsufficientPermissionsException If the user lacks the necessary permissions.
     * @throws EntryPhysicalCreationException If the file cannot be moved or the directory cannot be created.
     * @throws Exception If an unexpected error occurs.
     */
    public Entry createEntry(Entry destinationEntry, String newEntryName, File newEntryFile) throws Exception {
        if (!this.securityInterface.userHasRootPrivilages()) {
            if (!this.securityInterface.userHasRequiredPermissions(destinationEntry, false, true, true)) {
                throw new EntryInsufficientPermissionsException("Cannot %s '%s' %s '%s' directory due to insufficient permissions".formatted(newEntryFile == null ? "create directory" : "upload file", newEntryName, newEntryFile == null ? "in" : "to", destinationEntry.getUrl()));
            }
        }

        for (int iEntry = 0; iEntry < destinationEntry.getChildren().size(); iEntry++) {
            if (destinationEntry.getChildren().get(iEntry).getName().equals(newEntryName)) {
                throw new FileAlreadyExistsException("Entry '%s' already exists in '%s' directory".formatted(newEntryName, destinationEntry.getUrl()));
            }
        }

        final EntryPath entryPath = destinationEntry.getEntryPath().resolve(newEntryName);
        if (newEntryFile != null) {
            try {
                Files.move(Path.of(newEntryFile.getPath()), entryPath.getPath());
            } catch (Exception exception) {
                throw new EntryPhysicalCreationException("File content couldn't be written to '%s' file: %s".formatted(entryPath.getRelativePath().toString(), exception.getMessage()));
            }
        } else {
            try {
                Files.createDirectory(entryPath.getPath());
            } catch (Exception exception) {
                throw new EntryPhysicalCreationException("Directory '%s' couldn't be created".formatted(entryPath.getRelativePath().toString(), exception.getMessage()));
            }
        }

        final Entry newEntry = this.storageInterface.createNewEntry(destinationEntry, newEntryFile == null ? new ArrayList<>() : null, entryPath, null, null, null);
        logger.info("%s '%s' has been %s successfully".formatted(newEntry.getIsDirectory() ? "Directory" : "File", newEntry.getUrl(), newEntry.getIsDirectory() ? "created" : "uploaded"));
        return newEntry;
    }

    /**
     * Publishes a entry in the working tree and commits it to the database.
     * If the entry already exists, it will be updated.
     *
     * @param targetEntry The entry to be published or updated.
     * @return The published or updated entry.
     * @throws InvalidParameterException If the target entry is null or has invalid path.
     * @throws EntryPublishingRaceException If a race condition is detected.
     * @throws Exception If an unexpected error occurs during publishing.
     */
    public Entry publishEntry(Entry targetEntry) throws Exception {
        if (targetEntry == null || targetEntry.getEntryPath() == null) {
            throw new InvalidParameterException("Target entry is invalid".formatted());
        } else if (targetEntry.getParent() == null) {
            throw new InvalidParameterException("Root entry is not targetable".formatted());
        }

        logger.debug("Attempting to publish '%s' ".formatted(targetEntry.getUrl(), targetEntry.getIsDirectory() ? "directory" : "file"));
        final Entry persistedEntry = this.storageInterface.persistEntry(targetEntry);
        final Entry parentEntry = persistedEntry.getParent();

        /* If search by ID turns up with anything then this is an update job */
        for (int iEntry = 0; iEntry < parentEntry.getChildren().size(); iEntry++) {
            if (parentEntry.getChildren().get(iEntry).getId().equals(persistedEntry.getId())) {
                parentEntry.getChildren().remove(iEntry); /* Remove now and replace later */
            }
        }

        /* If search by name turns up with anything then we have a big problem */
        final Integer iEntry = Collections.binarySearch(parentEntry.getChildren(), persistedEntry, this.storageInterface.getStorageComparator());
        if (iEntry >= 0) { /* If the ID matches then update is permissible... although it should never come to this. */
            if (!persistedEntry.getId().equals(parentEntry.getChildren().get(iEntry).getId())) {
                /* If the ID doesn't match then we have a race condition. */
                throw new EntryPublishingRaceException("It's a race!".formatted());
            }

            parentEntry.getChildren().set(iEntry, persistedEntry);
        } else {            
            parentEntry.getChildren().add((-iEntry - 1), persistedEntry);
        }
        
        this.entryCache.put(persistedEntry.getId(), persistedEntry);

        logger.info("%s '%s' has been published successfully".formatted(persistedEntry.getIsDirectory() ? "Directory" : "File", persistedEntry.getUrl()));
        return persistedEntry;
    }

    /**
     * Transfers an entry to a new destination, either by copying or moving it.
     * 
     * If {@code persistOriginal} is {@code true}, the operation performs a copy; otherwise, it performs a move.
     * If {@code replaceExisting} is {@code true}, an existing entry with the same name in the destination will be replaced.
     * 
     * @param targetEntry The entry to be transferred.
     * @param destinationEntry The destination directory where the entry will be transferred.
     * @param newName The new name for the transferred entry (if {@code null}, the original name is retained).
     * @param persistOriginal If {@code true}, the original entry is retained (copy); otherwise, it is moved (default: {@code true}).
     * @param replaceExisting If {@code true}, an existing entry with the same name in the destination will be replaced (default: {@code false}).
     * @return The newly created or moved entry with an updated path (unpublished).
     * @throws InvalidParameterException If any of the provided parameters are invalid.
     * @throws FileAlreadyExistsException If a entry with the same name already exists in the destination and {@code replaceExisting} is {@code false}.
     * @throws EntryInsufficientPermissionsException If the user lacks the necessary permissions to perform the operation.
     * @throws EntryTypeNotSupportedException If attempting to replace a directory entry.
     * @throws IOException If the transfer fails due to an I/O issue.
     * @throws Exception If an unexpected error occurs.
     */
    public Entry transferEntry(Entry targetEntry, Entry destinationEntry, String newName, Boolean persistOriginal, Boolean replaceExisting) throws Exception {
        if (targetEntry == null || targetEntry.getId() == null || targetEntry.getEntryPath() == null) {
            throw new InvalidParameterException("Target entry is invalid".formatted());
        } else if (targetEntry.getParent() == null) {
            throw new InvalidParameterException("Root entry is not targetable".formatted());
        }

        if (destinationEntry == null || destinationEntry.getId() == null || destinationEntry.getEntryPath() == null) {
            throw new InvalidParameterException("Destination entry is invalid".formatted());
        } else if (!destinationEntry.getIsDirectory() || !destinationEntry.getEntryPath().isValidDirectory()) {
            throw new InvalidParameterException("Destination entry is not a directory".formatted());
        }

        final String newEntryName = newName != null ? newName : targetEntry.getName();
        final Boolean replaceExistingEntry = replaceExisting == null ? false : replaceExisting;
        final Boolean persistOriginalEntry = persistOriginal == null ? true : persistOriginal;
        for (int iEntry = 0; iEntry < destinationEntry.getChildren().size(); iEntry++) {
            if (destinationEntry.getChildren().get(iEntry).getName().equals(newEntryName)) {
                throw new FileAlreadyExistsException("Entry '%s' already exists in '%s' directory".formatted(newEntryName, destinationEntry.getUrl()));
            }
        }

        if (!this.securityInterface.userHasRootPrivilages()) {
            if (persistOriginalEntry) {
                if (!this.securityInterface.userHasRequiredPermissions(targetEntry, true, false, false) || !this.securityInterface.userHasRequiredPermissions(destinationEntry, false, true, true)) {
                    throw new EntryInsufficientPermissionsException("Cannot copy %s '%s' to directory '%s' due to insufficient permissions".formatted(targetEntry.getIsDirectory() ? "directory" : "file", targetEntry.getUrl(), destinationEntry.getUrl()));
                }
            } else {
                if (!this.securityInterface.userHasRequiredPermissions(targetEntry.getParent(), false, true, true) || !this.securityInterface.userHasRequiredPermissions(destinationEntry, false, true, true)) {
                    throw new EntryInsufficientPermissionsException("Cannot move %s '%s' to directory '%s' due to insufficient permissions".formatted(targetEntry.getIsDirectory() ? "directory" : "file", targetEntry.getUrl(), destinationEntry.getUrl()));
                }

                if (targetEntry.getParent().stickyBitIsSet() && !this.securityInterface.userIsTheOwnerOfTheEntry(targetEntry) && !this.securityInterface.userIsTheOwnerOfTheEntry(targetEntry.getParent())) {
                    throw new EntryInsufficientPermissionsException("Cannot move %s '%s' out of a shared directory due to insufficient permissions".formatted(targetEntry.getIsDirectory() ? "directory" : "file", targetEntry.getUrl()));
                }
            }
        }

        final EntryPath targetEntryPath = destinationEntry.getEntryPath().resolve(newEntryName);
        final EntryPath currentEntryPath = targetEntry.getEntryPath();
        final EntryPath afterTransferEntryPath;

        if (persistOriginalEntry) {
            if (replaceExistingEntry) {
                afterTransferEntryPath = destinationEntry.getEntryPath().resolveFromRoot(Files.copy(currentEntryPath.getPath(), targetEntryPath.getPath(), StandardCopyOption.REPLACE_EXISTING));
            } else {
                afterTransferEntryPath = destinationEntry.getEntryPath().resolveFromRoot(Files.copy(currentEntryPath.getPath(), targetEntryPath.getPath()));
            }
        } else {
            if (targetEntry.getIsDirectory()) {
                if (replaceExistingEntry) {
                    throw new EntryTypeNotSupportedException("Cannot replace existing '%s' directory".formatted(targetEntry.getUrl()));
                }

                afterTransferEntryPath = destinationEntry.getEntryPath().resolveFromRoot(Files.createDirectory(targetEntryPath.getPath()));
            } else {
                if (replaceExistingEntry) {
                    afterTransferEntryPath = destinationEntry.getEntryPath().resolveFromRoot(Files.move(currentEntryPath.getPath(), targetEntryPath.getPath(), StandardCopyOption.REPLACE_EXISTING));
                } else {
                    afterTransferEntryPath = destinationEntry.getEntryPath().resolveFromRoot(Files.move(currentEntryPath.getPath(), targetEntryPath.getPath()));
                }
            }
        }

        final Entry newEntry;
        if (persistOriginalEntry) { /* Copied entries acquire new attributes (defaults) */
            newEntry = this.storageInterface.createNewEntry(destinationEntry, targetEntry.getIsDirectory() ? new ArrayList<>() : null, afterTransferEntryPath, null, null, null);
        } else { /* Moved entries retain attributes of the original entry */
            newEntry = this.storageInterface.createNewEntry(destinationEntry, targetEntry.getIsDirectory() ? new ArrayList<>() : null, afterTransferEntryPath, targetEntry.getUser(), targetEntry.getGroup(), targetEntry.getPermissions());
        }

        logger.info("%s '%s' has been %s to '%s' directory successfully".formatted(targetEntry.getIsDirectory() ? "Directory" : "File", targetEntry.getUrl(), persistOriginalEntry ? "copied" : "moved", destinationEntry.getUrl()));
        return newEntry;
    }

    public Entry modifyEntry(Entry targetEntry) throws Exception {
        /* TODO: update storage entry! */
        return targetEntry;
    }

    /**
     * Modifies the permission flags of the specified entry.
     *
     * This method supports both octal (e.g., "755", "0644") and symbolic (e.g., "u+rwx", "g-w", "o+t") 
     * permission notations. If the user lacks sufficient permissions, the operation will fail.
     *
     * @param targetEntry The entry whose permission flags are to be updated or set.
     * @param permissionFlags The new permission flags, either as an octal value or symbolic notation.
     * @return The updated entry with the new permission flags (unpublished).
     * @throws EntryInsufficientPermissionsException If the user does not have sufficient permissions to modify the entry.
     * @throws InvalidParameterException If the provided entry is {@code null} or is the root entry.
     * @throws IllegalArgumentException If the permission flags are malformed or contain invalid scopes.
     * @throws Exception If an unexpected error occurs.
     */
    public Entry modifyEntryPermissions(Entry targetEntry, String permissionFlags) throws Exception {
        if (targetEntry == null) {
            throw new InvalidParameterException("Target entry is invalid".formatted());
        } else if (targetEntry.getParent() == null) {
            throw new InvalidParameterException("Root entry is not targetable".formatted());
        }

        if (!this.securityInterface.userHasRootPrivilages()) {
            if (!this.securityInterface.userIsTheOwnerOfTheEntry(targetEntry)) {
                throw new EntryInsufficientPermissionsException("Cannot modify %s '%s' permission flags due to insufficient permissions".formatted(targetEntry.getIsDirectory() ? "directory" : "file", targetEntry.getUrl()));
            }
        }

        if (permissionFlags.matches("^[0-7]{3,4}$") != true) {
            String[] operationDescriptors = permissionFlags.contains(",") ? permissionFlags.split(",") : new String[]{permissionFlags};
            Pattern pattern = Pattern.compile("^((?:[ugo]{1,3}|a)?)([+=-])([rwxst]{0,5})$");
            ArrayList<String[]> operationsMatrix = new ArrayList<>();

            Integer integerPermissions = Integer.parseInt(targetEntry.getPermissions(), 8);
            Integer flagMask, specialMask, permissionMask;
            String scope, operator, permissions;

            for (String operation : operationDescriptors) {
                Matcher matcher = pattern.matcher(operation);

                if (matcher.matches()) {
                    scope = matcher.group(1);
                    operator = matcher.group(2);
                    permissions = matcher.group(3);

                    if (permissions.contains("t") && !(scope.isEmpty() || scope.equals("a") || scope.contains("o"))) {
                        throw new IllegalArgumentException("Invalid scope '%s' for sticky bit".formatted(scope));
                    }
    
                    if (permissions.contains("s") && !(scope.isEmpty() || scope.equals("a") || scope.contains("u") || scope.contains("g"))) {
                        throw new IllegalArgumentException("Invalid scope '%s' for setuid/setgid bit(s)".formatted(scope));
                    }

                    operationsMatrix.add(new String[]{scope, operator, permissions});
                } else {
                    throw new IllegalArgumentException("Invalid permission flags: %s".formatted(operation));
                }
            }

            for (String[] operation : operationsMatrix) {
                flagMask = 0;
                specialMask = 0;
                permissionMask = 0;
                scope = operation[0];
                operator = operation[1];
                permissions = operation[2];

                for (char permission : permissions.toCharArray()) {
                    switch (permission) {
                        case 'r': permissionMask |= 04; break;
                        case 'w': permissionMask |= 02; break;
                        case 'x': permissionMask |= 01; break;
                    }
                }

                if (permissions.contains("s") && (scope.contains("u") || scope.equals("a") || scope.isEmpty())) specialMask |= 04;
                if (permissions.contains("s") && (scope.contains("g") || scope.equals("a") || scope.isEmpty())) specialMask |= 02;
                if (permissions.contains("t") && (scope.contains("o") || scope.equals("a") || scope.isEmpty())) specialMask |= 01;

                if (scope.contains("u") || scope.equals("a") || scope.isEmpty()) flagMask |= permissionMask << 6;
                if (scope.contains("g") || scope.equals("a") || scope.isEmpty()) flagMask |= permissionMask << 3;
                if (scope.contains("o") || scope.equals("a") || scope.isEmpty()) flagMask |= permissionMask;
                if (specialMask != 0) flagMask |= specialMask << 9; /* setuid, setgid, sticky bits */
                
                switch (operator) {
                    case "+":
                        integerPermissions |= flagMask;
                        break;
                    case "-":
                        integerPermissions &= ~flagMask;
                        break;
                    case "=":
                        if (scope.equals("a") || scope.isEmpty()) {
                            integerPermissions &= ~07777;
                            integerPermissions |= flagMask;
                        } else {
                            if (scope.contains("u")) {
                                integerPermissions = (integerPermissions & ~0700) | (flagMask & 0700);

                                if (permissions.contains("s")) {
                                    integerPermissions |= 04000;
                                } else {
                                    integerPermissions &= ~04000;
                                }
                            }

                            if (scope.contains("g")) {
                                integerPermissions = (integerPermissions & ~0070) | (flagMask & 0070);

                                if (permissions.contains("s")) {
                                    integerPermissions |= 02000;
                                } else {
                                    integerPermissions &= ~02000;
                                }
                            }

                            if (scope.contains("o")) {
                                integerPermissions = (integerPermissions & ~0007) | (flagMask & 0007);

                                if (permissions.contains("t")) {
                                    integerPermissions |= 01000;
                                } else {
                                    integerPermissions &= ~01000;
                                }
                            }
                        }

                        break;
                    default:
                        throw new IllegalArgumentException("Invalid operator: %s".formatted(operator));
                }
            }

            logger.info("%s '%s' permission flags have been updated to %o successfully".formatted(targetEntry.getIsDirectory() ? "Directory" : "File", targetEntry.getUrl(), integerPermissions));
            return targetEntry.setPermissions(String.format("%o", integerPermissions));
        }

        logger.info("%s '%s' permission flags have been set to %s successfully".formatted(targetEntry.getIsDirectory() ? "Directory" : "File", targetEntry.getUrl(), permissionFlags));
        return targetEntry.setPermissions(permissionFlags);
    }

    /**
     * Modifies the ownership of the specified entry.
     *
     * The new owner and group must be specified in the format {@code userUUID:groupUUID}, 
     * where either value may be omitted (e.g., {@code userUUID} to change only the user, 
     * or {@code :groupUUID} to change only the group).
     *
     * @param targetEntry The entry whose ownership is to be updated.
     * @param pairedOwners A string containing the new owner and group UUIDs, separated by a colon (":").
     *                     Example: {@code "550e8400-e29b-41d4-a716-446655440000:9f3c9e3e-7b7d-42e2-bf99-dc62b5f4d423"}.
     * @return The updated entry with the new ownership values (unpublished).
     * @throws EntryInsufficientPermissionsException If the user lacks the necessary permissions to change ownership.
     * @throws InvalidParameterException If the provided entry is {@code null} or is the root entry.
     * @throws IllegalArgumentException If the ownership string is incorrectly formatted.
     * @throws Exception If an unexpected error occurs.
     */
    public Entry modifyEntryOwnership(Entry targetEntry, String pairedOwners) throws Exception {
        if (targetEntry == null) {
            throw new InvalidParameterException("Target entry is invalid".formatted());
        } else if (targetEntry.getParent() == null) {
            throw new InvalidParameterException("Root entry is not targetable".formatted());
        }

        final String[] splitOwners = pairedOwners.split(":");
        final UUID user = splitOwners.length > 0 ? UUID.fromString(splitOwners[0]) : null;
        final UUID group = splitOwners.length > 1 ? UUID.fromString(splitOwners[1]) : null;

        if (user == null && group == null) {
            throw new IllegalArgumentException("Ownership string '%s' is invalid".formatted(pairedOwners));
        }

        if (!this.securityInterface.userHasRootPrivilages()) {
            if (!this.securityInterface.userHasRequiredPermissions(targetEntry.getParent(), false, true, true) || !this.securityInterface.userIsTheOwnerOfTheEntry(targetEntry)) {
                throw new EntryInsufficientPermissionsException("Cannot modify %s '%s' ownership due to insufficient permissions".formatted(targetEntry.getIsDirectory() ? "directory" : "file", targetEntry.getUrl()));
            }

            if (user != null && !this.securityInterface.userCanInteractWithTheUser(user)) {
                throw new EntryInsufficientPermissionsException("Cannot modify %s '%s' ownership because the user cannot interact with the target user".formatted(targetEntry.getIsDirectory() ? "directory" : "file", targetEntry.getUrl()));
            }

            if (group != null && !this.securityInterface.userBelongsToTheGroup(group)) {
                throw new EntryInsufficientPermissionsException("Cannot modify %s '%s' ownership because the user doesn't belong to the target group".formatted(targetEntry.getIsDirectory() ? "directory" : "file", targetEntry.getUrl()));
            }
        }

        /* Done separately from the permission check to ensure that the change is not just partially permitted */
        if (user != null) targetEntry.setUser(user);
            
        /* Done separately from the permission check to ensure that the change is not just partially permitted */
        if (group != null) targetEntry.setGroup(group);

        logger.info("%s '%s' ownership has been transfered to %s:%s successfully".formatted(targetEntry.getIsDirectory() ? "Directory" : "File", targetEntry.getUrl(), user != null ? user.toString() : "", group != null ? group.toString() : ""));
        return targetEntry;
    }

    /**
     * Deletes the specified entry from storage and removes its physical representation.
     *
     * If the entry exists in the database, it will be removed from storage, 
     * its cached reference will be deleted, and its physical file or directory 
     * will be deleted from disk.
     * 
     * @param targetEntry The entry to be deleted.
     * @return The deleted entry with its UUID and path set to {@code null}, confirming deletion.
     * @throws InvalidParameterException If the provided entry is {@code null} or is the root entry.
     * @throws EntryInsufficientPermissionsException If the user lacks the necessary permissions to delete the entry.
     * @throws DirectoryNotEmptyException If attempting to delete a non-empty directory.
     * @throws IOException If the entry removal attempt fails due to an I/O issue.
     * @throws Exception If an unexpected error occurs.
     */
    public Entry deleteEntry(Entry targetEntry) throws Exception {
        if (targetEntry == null) {
            throw new InvalidParameterException("Target entry is invalid".formatted());
        } else if (targetEntry.getParent() == null) {
            throw new InvalidParameterException("Root entry is not targetable".formatted());
        }

        if (!this.securityInterface.userHasRootPrivilages()) {
            if (!this.securityInterface.userHasRequiredPermissions(targetEntry.getParent(), false, true, true)) {
                throw new EntryInsufficientPermissionsException("Cannot remove %s '%s' due to insufficient permissions".formatted(targetEntry.getIsDirectory() ? "directory" : "file", targetEntry.getUrl()));
            }

            if (targetEntry.getParent().stickyBitIsSet() && !this.securityInterface.userIsTheOwnerOfTheEntry(targetEntry) && !this.securityInterface.userIsTheOwnerOfTheEntry(targetEntry.getParent())) {
                throw new EntryInsufficientPermissionsException("Cannot remove %s '%s' from a shared directory due to insufficient permissions".formatted(targetEntry.getIsDirectory() ? "directory" : "file", targetEntry.getUrl()));
            }
        }

        if (targetEntry.getId() != null) {
            this.storageInterface.deleteEntry(targetEntry);
            this.entryCache.remove(targetEntry.getId());
        }

        targetEntry.getParent().getChildren().remove(targetEntry);
        Files.deleteIfExists(targetEntry.getEntryPath().getPath());

        logger.info("%s '%s' has been removed successfully".formatted(targetEntry.getIsDirectory() ? "Directory" : "File", targetEntry.getUrl()));
        return targetEntry.setEntryPath(null).setId(null);
    }

    //**********************************************************//
    //*                                                        *//
    //*                     Additional tools                   *//
    //*                                                        *//
    //**********************************************************//

    /**
     * Creates a dummy entry under the specified destination entry.
     *
     * This method wraps around {@code storageInterface.createNewEntry} to generate a new 
     * entry with a given name but without a specific user, group, permissions, or most importantly path.
     *
     * @param destinationEntry The parent entry where the dummy entry will be created.
     * @param children A list of child entries to associate with the new dummy entry.
     * @param entryName The name of the new dummy entry.
     * @return The newly created dummy entry with the specified name (unpublished).
     */
    public Entry createDummyEntry(Entry destinationEntry, List<Entry> children, String entryName) {
        return this.storageInterface.createNewEntry(destinationEntry, children, null, null, null, null).setName(entryName);
    }
}
