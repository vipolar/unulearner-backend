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
import java.util.List;
import java.util.Deque;
import java.util.UUID;
import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.unulearner.backend.storage.interfaces.StorageSecurityInterface;
import com.unulearner.backend.storage.interfaces.StorageServiceInterface;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.miscellaneous.Holder;
import com.unulearner.backend.storage.extensions.NodePath;

import com.unulearner.backend.storage.exceptions.NodeInsufficientPermissionsException;
import com.unulearner.backend.storage.exceptions.NodeTypeInDatabaseMismatchException;
import com.unulearner.backend.storage.exceptions.NodeToParentRelationsException;
import com.unulearner.backend.storage.exceptions.NodeTypeNotSupportedException;
import com.unulearner.backend.storage.exceptions.NodeIsInaccessibleException;
import com.unulearner.backend.storage.exceptions.NodePublishingRaceException;
import com.unulearner.backend.storage.exceptions.StorageServiceException;

import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.security.InvalidParameterException;
import java.util.NoSuchElementException;
import java.io.IOException;

@Service
public class Storage {
    private static final Logger storageLogger = LoggerFactory.getLogger(Storage.class);
    private final StorageSecurityInterface storageSecurityInterface;
    private final StorageServiceInterface storageServiceInterface;
    private final HashMap<UUID, StorageNode> storageHashMap;
    private final NodePath storageRootDirectoryPath;
    private final StorageNode storageRootNode;

    public Storage(StorageSecurityInterface storageSecurityInterface, StorageServiceInterface storageServiceInterface) {
        this.storageSecurityInterface = storageSecurityInterface;
        this.storageServiceInterface = storageServiceInterface;
        this.storageHashMap = new HashMap<UUID, StorageNode>();

        try {
            this.storageRootDirectoryPath = this.storageServiceInterface.getRootDirectory();
        } catch (Exception exception) {
            /* If it got here then we have no choice but to crash it! */
            throw new RuntimeException("Fatal error: %s\nFailed to build storage tree.\nExiting the application...".formatted(exception.getMessage()));
        }

        try {
            final NodePath rootDirectoryPath = this.storageRootDirectoryPath;
            final HashMap<UUID, StorageNode> storageHashMap = this.storageHashMap;
            final Holder<StorageNode> rootStorageNodeHolder = new Holder<StorageNode>();
            final Deque<StorageNode> rootDirectoryNodeDeque = new ArrayDeque<StorageNode>();

            Files.walkFileTree(rootDirectoryPath.getPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directoryPath, BasicFileAttributes attrs) {
                    try {
                        final NodePath directoryNodePath = rootDirectoryPath.resolveFromRoot(directoryPath);
                        final StorageNode parentStorageNode = rootDirectoryNodeDeque.peekLast();
                        /* Fuck Java, this is final! */ StorageNode targetStorageNode;

                        if (!directoryNodePath.isValidDirectory()) {
                            throw new NodeIsInaccessibleException("Directory is inaccessible.".formatted());
                        }

                        try {
                            targetStorageNode = storageServiceInterface.retrieveStorageNodeByURL(directoryNodePath.getRelativePath().toString()).setNodePath(directoryNodePath);
                            if (parentStorageNode != null && targetStorageNode.getParent() != null && !parentStorageNode.getId().equals(targetStorageNode.getParent().getId())) {
                                throw new NodeToParentRelationsException("Directory is supposedly a child of directory '%s' but the relationship is not mirrored on the persistent level".formatted(parentStorageNode.getUrl()));
                            }

                            if (targetStorageNode.getIsDirectory() == null || targetStorageNode.getIsDirectory() != true) {
                                throw new NodeTypeInDatabaseMismatchException("Directory '%s' type doesn't match the type persisted to the database".formatted(targetStorageNode.getUrl()));
                            }

                            targetStorageNode.setChildren(storageServiceInterface.retrieveChildrenStorageNodes(targetStorageNode));
                            if (!targetStorageNode.getChildren().isEmpty()) { /* Sorting at this stage and then inserting accordingly seems like a better idea than throwing it all in together and sorting on postDirectoryVisit */
                                Collections.sort(targetStorageNode.getChildren(), storageServiceInterface.getStorageComparator());
                            }
                        } catch (NoSuchElementException exception) {
                            storageLogger.warn("Database entry for '%s' couldn't be found and has to be created on the spot".formatted(directoryNodePath.getRelativePath().toString()));
                            if (parentStorageNode != null) {
                                targetStorageNode = storageServiceInterface.saveStorageNode(storageServiceInterface.createNewStorageNode(parentStorageNode, new ArrayList<>(), directoryNodePath, null, null, null));
                            } else {
                                targetStorageNode = storageServiceInterface.saveStorageNode(storageServiceInterface.createRootStorageNode(new ArrayList<>(), rootDirectoryPath));
                            }
                        } catch (NodeTypeInDatabaseMismatchException exception) {
                            /* TODO: handle and log exception */
                            targetStorageNode = storageServiceInterface.saveStorageNode(storageServiceInterface.createNewStorageNode(parentStorageNode, new ArrayList<>(), directoryNodePath, null, null, null));
                        } catch (NodeToParentRelationsException exception) {
                            /* TODO: handle and log exception */
                            targetStorageNode = storageServiceInterface.saveStorageNode(storageServiceInterface.createNewStorageNode(parentStorageNode, new ArrayList<>(), directoryNodePath, null, null, null));
                        } catch (Exception exception) {
                            /* TODO: handle and log exception */
                            targetStorageNode = storageServiceInterface.saveStorageNode(storageServiceInterface.createNewStorageNode(parentStorageNode, new ArrayList<>(), directoryNodePath, null, null, null));
                        }

                        if (parentStorageNode != null) { /* null parent is only allowed in the case of the root node. No other node will go past the pre-commit check with its parent set to null */
                            final Integer iNode = Collections.binarySearch(parentStorageNode.getChildren(), targetStorageNode, storageServiceInterface.getStorageComparator());

                            if (iNode >= 0) {
                                parentStorageNode.getChildren().set(iNode, targetStorageNode);
                            } else {
                                parentStorageNode.getChildren().add((-iNode - 1), targetStorageNode);
                            }
                        }

                        if (storageHashMap.put(targetStorageNode.getId(), targetStorageNode) != null) {
                            throw new FileAlreadyExistsException("Directory node already exists on the storage tree hashmap!".formatted(directoryNodePath.getRelativePath().toString()));
                        }

                        rootDirectoryNodeDeque.offer(targetStorageNode);
                    } catch (Exception exception) {
                        storageLogger.warn("Failed to add directory '%s' to the storage tree: %s".formatted(directoryPath.toString(), exception.getMessage()));
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    storageLogger.info("Directory '%s' was successfully added to the storage tree".formatted(directoryPath.toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) {
                    try {
                        final NodePath fileNodePath = rootDirectoryPath.resolveFromRoot(filePath);
                        final StorageNode parentStorageNode = rootDirectoryNodeDeque.peekLast();
                        /* Fuck Java, this is final! */ StorageNode targetStorageNode;

                        if (!fileNodePath.isValidFile()) {
                            throw new NodeIsInaccessibleException("File is inaccessible.".formatted());
                        }

                        try {
                            targetStorageNode = storageServiceInterface.retrieveStorageNodeByURL(fileNodePath.getRelativePath().toString()).setNodePath(fileNodePath);
                            if (!parentStorageNode.getId().equals(targetStorageNode.getParent().getId())) {
                                throw new NodeToParentRelationsException("File is supposedly a child of directory '%s' but the relationship is not mirrored on the persistent level".formatted(parentStorageNode.getUrl()));
                            }

                            if (targetStorageNode.getIsDirectory() == null || targetStorageNode.getIsDirectory() != false) {
                                throw new NodeTypeInDatabaseMismatchException("File '%s' type doesn't match the type persisted to the database".formatted(targetStorageNode.getUrl()));
                            }
                        } catch (NoSuchElementException exception) {
                            storageLogger.warn("Database entry for '%s' couldn't be found and has to be created on the spot".formatted(fileNodePath.getRelativePath().toString()));
                            targetStorageNode = storageServiceInterface.saveStorageNode(storageServiceInterface.createNewStorageNode(parentStorageNode, null, fileNodePath, null, null, null));
                        } catch (NodeTypeInDatabaseMismatchException exception) {
                            /* TODO: handle and log exception */
                            targetStorageNode = storageServiceInterface.saveStorageNode(storageServiceInterface.createNewStorageNode(parentStorageNode, null, fileNodePath, null, null, null));
                        } catch (NodeToParentRelationsException exception) {
                            /* TODO: handle and log exception */
                            targetStorageNode = storageServiceInterface.saveStorageNode(storageServiceInterface.createNewStorageNode(parentStorageNode, null, fileNodePath, null, null, null));
                        } catch (Exception exception) {
                            /* TODO: handle and log exception */
                            targetStorageNode = storageServiceInterface.saveStorageNode(storageServiceInterface.createNewStorageNode(parentStorageNode, null, fileNodePath, null, null, null));
                        }     
                        
                        final Integer iNode = Collections.binarySearch(parentStorageNode.getChildren(), targetStorageNode, storageServiceInterface.getStorageComparator());
                        if (iNode >= 0) {
                            parentStorageNode.getChildren().set(iNode, targetStorageNode);
                        } else {
                            parentStorageNode.getChildren().add((-iNode - 1), targetStorageNode);
                        }

                        if (storageHashMap.put(targetStorageNode.getId(), targetStorageNode) != null) {
                            throw new FileAlreadyExistsException("File node already exists on the storage tree hashmap.".formatted());
                        }

                    } catch (Exception exception) {
                        storageLogger.warn("Failed to add file '%s' to the storage tree: %s".formatted(filePath.toString(), exception.getMessage()));
                        return FileVisitResult.CONTINUE;
                    }

                    storageLogger.info("File '%s' was successfully added to the storage tree.".formatted(filePath.toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path filePath, IOException exception) {
                    if (exception != null) {
                        storageLogger.warn("Failed to visit file '%s': %s".formatted(filePath.toString(), exception.getMessage()));
                        return FileVisitResult.CONTINUE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directoryPath, IOException exception) {
                    if (exception != null) {
                        storageLogger.warn("Failed to visit directory '%s': %s".formatted(directoryPath.toString(), exception.getMessage()));
                        return FileVisitResult.CONTINUE;
                    }

                    final StorageNode directoryStorageNode = rootDirectoryNodeDeque.pollLast();
                    long fileCount = 0, directoryCount = 0, visitedCount = 0, accessibleCount = 0;
                    for (StorageNode node : directoryStorageNode.getChildren()) {
                        visitedCount++;

                        if (node.getIsAccessible()) {
                            accessibleCount++;
                        }

                        if (node.getIsDirectory()) {
                            directoryCount++;
                        } else {
                            fileCount++;
                        }
                    }

                    if (directoryStorageNode.getParent() == null) {
                        rootStorageNodeHolder.setValue(directoryStorageNode);
                    }

                    storageLogger.info("Directory '%s' visited successfully. Total nodes: %d. Directory nodes: %d. File nodes: %d. Accessible nodes: %d.".formatted(directoryPath.toString(), visitedCount, directoryCount, fileCount, accessibleCount));
                    return FileVisitResult.CONTINUE;
                }
            });

            if ((this.storageRootNode = rootStorageNodeHolder.getValue()) == null) {
                throw new RuntimeException("No discernable root node could be found on the tree.".formatted());
            }

            if (!rootDirectoryNodeDeque.isEmpty()) {
                throw new RuntimeException("%d unhandled directories left in the deque.".formatted(rootDirectoryNodeDeque.size()));
            }
        } catch (Exception exception) {
            /* If it got here then we have no choice but to crash it! */
            throw new RuntimeException("Fatal error: %s\nFailed to build storage tree.\nExiting the application...".formatted(exception.getMessage()));
        }
    }

    /**
     * <p>Publish a node on the working tree and commit it to the database (update if it is already there)</p>
     * @param targetStorageNode node to be published or updated
     * @return published (or updated) node
     * @throws InvalidParameterException if any of the provided parameters are invalid
     * @throws FileAlreadyExistsException oh boy this is a doozy one...
     * @throws Exception if anything not covered above was thrown
     */
    public StorageNode publishStorageNode(StorageNode targetStorageNode) throws Exception {
        if (targetStorageNode == null || targetStorageNode.getNodePath() == null) {
            throw new InvalidParameterException("Target node is invalid.".formatted());
        } else if (targetStorageNode.getParent() == null) {
            throw new InvalidParameterException("Root node is not targetable.".formatted());
        }

        storageLogger.debug("Attempting to publish '%s' ".formatted(targetStorageNode.getUrl(), targetStorageNode.getIsDirectory() ? "directory" : "file"));
        final StorageNode savedStorageNode = this.storageServiceInterface.saveStorageNode(targetStorageNode);
        final StorageNode parentStorageNode = savedStorageNode.getParent();

        /* If search by ID turns up with anything then this is an update job */
        for (int iNode = 0; iNode < parentStorageNode.getChildren().size(); iNode++) {
            if (parentStorageNode.getChildren().get(iNode).getId().equals(savedStorageNode.getId())) {
                parentStorageNode.getChildren().remove(iNode); /* Remove now and replace later */
            }
        }

        /* If search by name turns up with anything then we have a big problem */
        final Integer iNode = Collections.binarySearch(parentStorageNode.getChildren(), savedStorageNode, this.storageServiceInterface.getStorageComparator());
        if (iNode >= 0) { /* If the ID matches then update is permissible... although it should never come to this. */
            if (!savedStorageNode.getId().equals(parentStorageNode.getChildren().get(iNode).getId())) {
                /* If the ID doesn't match then we have a race condition. */
                throw new NodePublishingRaceException("It's a race!".formatted());
            }

            parentStorageNode.getChildren().set(iNode, savedStorageNode);
        } else {            
            parentStorageNode.getChildren().add((-iNode - 1), savedStorageNode);
        }
        
        this.storageHashMap.put(savedStorageNode.getId(), savedStorageNode);

        storageLogger.info("%s '%s' has been published successfully".formatted(savedStorageNode.getIsDirectory() ? "Directory" : "File", savedStorageNode.getUrl()));
        return savedStorageNode;
    }

    /**
     * <p>Find an existing node by name, whether it exists as a full-blown node on the tree or just as a physical node in the drive and recover it</p>
     * @param targetNodeName name of the node to be searched for in the parent node directory
     * @param destinationStorageNode existing parent node to which the new node will be attached
     * @return a newly created node with a path to the file that was previously uncatalogued
     * @throws InvalidParameterException if any of the provided parameters are invalid
     * @throws NodeIsInaccessibleException if file is inaccessible or nonexistent
     * @throws NodeTypeNotSupportedException if file type is not supported
     * @throws Exception if anything not covered above was thrown
     */
    public StorageNode recoverStorageNode(String targetNodeName, StorageNode destinationStorageNode) throws Exception {
        if (targetNodeName == null || targetNodeName.isBlank()) {
            throw new InvalidParameterException("Target node name cannot be blank.".formatted());
        }

        if (destinationStorageNode == null || destinationStorageNode.getId() == null || destinationStorageNode.getNodePath() == null) {
            throw new InvalidParameterException("Destination node is invalid.".formatted());
        } else if (!destinationStorageNode.getIsDirectory() || !destinationStorageNode.getNodePath().isValidDirectory()) {
            throw new InvalidParameterException("Destination node is not a directory.".formatted());
        }

        /* If node is already there (whether the file is actually accessible or not) */
        for (int iNode = 0; iNode < destinationStorageNode.getChildren().size(); iNode++) {
            if (destinationStorageNode.getChildren().get(iNode).getName().equals(targetNodeName)) {
                return destinationStorageNode.getChildren().get(iNode);
            }
        }

        final StorageNode newStorageNode; /* If node is not there but the file supposedly is... */
        final NodePath targetPath = destinationStorageNode.getNodePath().resolve(targetNodeName);
        if (targetPath.isValidDirectory()) {
            newStorageNode = this.storageServiceInterface.createNewStorageNode(destinationStorageNode, new ArrayList<>(), targetPath, null, null, null);
        } else if (targetPath.isValidFile()) {
            newStorageNode = this.storageServiceInterface.createNewStorageNode(destinationStorageNode, null, targetPath, null, null, null);
        } else if (targetPath.isValidNode()) {
            throw new NodeTypeNotSupportedException("Node '%s' is of unsupported file type.".formatted(targetPath.getPath().toString()));
        } else {
            throw new NodeIsInaccessibleException("Node '%s' is inaccessible or nonexistent".formatted(targetPath.getPath().toString()));
        }

        /* TODO: bring this node to the attention of the admin! */
        storageLogger.warn("%s '%s' has been recovered and requires attention".formatted(newStorageNode.getIsDirectory() ? "Directory" : "File", newStorageNode.getUrl(), newStorageNode.getIsDirectory() ? "created" : "uploaded"));
        return newStorageNode;
    }

    /**
     * <p>Create a new node with a valid physical path attached to it</p>
     * @param newFile file to be written to disk (applicable only to file nodes)
     * @param newStorageNode an incomplete, unpublished node that is to be created
     * @param destinationStorageNode destination node where the new node is to be created
     * @return the completed node with a valid path to its physical part (unpublished)
     * @throws IOException if the node creation attempt fails for any I/O reason
     * @throws InvalidParameterException if any of the provided parameters are invalid
     * @throws FileAlreadyExistsException if a node under the same name already exists under this destination 
     * @throws Exception if anything not covered above was thrown
     */
    public StorageNode createStorageNode(StorageNode destinationStorageNode, String newStorageNodeName, File newStorageNodeFile) throws Exception {
        if (!this.storageSecurityInterface.userHasRootPrivilages()) {
            if (!this.storageSecurityInterface.userHasRequiredPermissions(destinationStorageNode, false, true, true)) {
                throw new NodeInsufficientPermissionsException("Cannot %s '%s' %s '%s' directory due to insufficient permissions".formatted(newStorageNodeFile == null ? "create directory" : "upload file", newStorageNodeName, newStorageNodeFile == null ? "in" : "to", destinationStorageNode.getUrl()));
            }
        }

        for (int iNode = 0; iNode < destinationStorageNode.getChildren().size(); iNode++) {
            if (destinationStorageNode.getChildren().get(iNode).getName().equals(newStorageNodeName)) {
                throw new FileAlreadyExistsException("Node '%s' already exists in '%s' directory.".formatted(newStorageNodeName, destinationStorageNode.getUrl()));
            }
        }

        final NodePath finalNodePath = destinationStorageNode.getNodePath().resolve(newStorageNodeName);
        if (newStorageNodeFile != null) {
            try {
                Files.move(Path.of(newStorageNodeFile.getPath()), finalNodePath.getPath());
            } catch (Exception exception) {
                throw new StorageServiceException("File content couldn't be written to '%s' file: %s".formatted(finalNodePath.getRelativePath().toString(), exception.getMessage()));
            }
        } else {
            try {
                Files.createDirectory(finalNodePath.getPath());
            } catch (Exception exception) {
                throw new StorageServiceException("Directory '%s' couldn't be created".formatted(finalNodePath.getRelativePath().toString(), exception.getMessage()));
            }
        }

        final StorageNode newStorageNode = this.storageServiceInterface.createNewStorageNode(destinationStorageNode, newStorageNodeFile == null ? new ArrayList<>() : null, finalNodePath, null, null, null);
        storageLogger.info("%s '%s' has been %s successfully".formatted(newStorageNode.getIsDirectory() ? "Directory" : "File", newStorageNode.getUrl(), newStorageNode.getIsDirectory() ? "created" : "uploaded"));
        return newStorageNode;
    }

    public StorageNode createDummyStorageNode(StorageNode destinationStorageNode, List<StorageNode> children, String nodeName) {
        return this.storageServiceInterface.createNewStorageNode(destinationStorageNode, children, null, null, null, null).setName(nodeName);
    }

    /**
     * <p>Transfer a node to a new destination (copy/move)</p>
     * @param newStorageNode an incomplete, unpublished node that is to be created
     * @param targetStorageNode target node that is to be transfered to a new destination
     * @param destinationStorageNode destination node that the target is to be transfered into
     * @param persistOriginal if true the original node will be persisted (copy) (default: true)
     * @param replace if true the node will replace the conflicting node (default: false)
     * @return the completed node with a valid path to its physical part (unpublished)
     * @throws InvalidParameterException if any of the provided parameters are invalid
     * @throws FileAlreadyExistsException if a node under the same name already exists under this destination 
     * @throws IOException if the node transfer attempt fails for any I/O reason
     * @throws Exception if anything not covered above was thrown
     */
    public StorageNode transferStorageNode(StorageNode targetStorageNode, StorageNode destinationStorageNode, String newName, Boolean persistOriginal, Boolean replaceExisting) throws Exception {
        if (targetStorageNode == null || targetStorageNode.getId() == null || targetStorageNode.getNodePath() == null) {
            throw new InvalidParameterException("Target node is invalid.".formatted());
        } else if (targetStorageNode.getParent() == null) {
            throw new InvalidParameterException("Root node is not targetable.".formatted());
        }

        if (destinationStorageNode == null || destinationStorageNode.getId() == null || destinationStorageNode.getNodePath() == null) {
            throw new InvalidParameterException("Destination node is invalid.".formatted());
        } else if (!destinationStorageNode.getIsDirectory() || !destinationStorageNode.getNodePath().isValidDirectory()) {
            throw new InvalidParameterException("Destination node is not a directory.".formatted());
        }

        final String newStorageNodeName = newName != null ? newName : targetStorageNode.getName();
        final Boolean replaceExistingNode = replaceExisting == null ? false : replaceExisting;
        final Boolean persistOriginalNode = persistOriginal == null ? true : persistOriginal;
        for (int iNode = 0; iNode < destinationStorageNode.getChildren().size(); iNode++) {
            if (destinationStorageNode.getChildren().get(iNode).getName().equals(newStorageNodeName)) {
                throw new FileAlreadyExistsException("Node '%s' already exists in '%s' directory.".formatted(newStorageNodeName, destinationStorageNode.getUrl()));
            }
        }

        if (!this.storageSecurityInterface.userHasRootPrivilages()) {
            if (persistOriginalNode) {
                if (!this.storageSecurityInterface.userHasRequiredPermissions(targetStorageNode, true, false, false) || !this.storageSecurityInterface.userHasRequiredPermissions(destinationStorageNode, false, true, true)) {
                    throw new NodeInsufficientPermissionsException("Cannot copy %s '%s' to directory '%s' due to insufficient permissions".formatted(targetStorageNode.getIsDirectory() ? "directory" : "file", targetStorageNode.getUrl(), destinationStorageNode.getUrl()));
                }
            } else {
                if (!this.storageSecurityInterface.userHasRequiredPermissions(targetStorageNode.getParent(), false, true, true) || !this.storageSecurityInterface.userHasRequiredPermissions(destinationStorageNode, false, true, true)) {
                    throw new NodeInsufficientPermissionsException("Cannot move %s '%s' to directory '%s' due to insufficient permissions".formatted(targetStorageNode.getIsDirectory() ? "directory" : "file", targetStorageNode.getUrl(), destinationStorageNode.getUrl()));
                }

                if (targetStorageNode.getParent().stickyBitIsSet() && !this.storageSecurityInterface.userIsTheOwnerOfTheNode(targetStorageNode) && !this.storageSecurityInterface.userIsTheOwnerOfTheNode(targetStorageNode.getParent())) {
                    throw new NodeInsufficientPermissionsException("Cannot move %s '%s' out of a shared directory due to insufficient permissions".formatted(targetStorageNode.getIsDirectory() ? "directory" : "file", targetStorageNode.getUrl()));
                }
            }
        }

        final NodePath targetNodePath = destinationStorageNode.getNodePath().resolve(newStorageNodeName);
        final NodePath currentNodePath = targetStorageNode.getNodePath();
        final NodePath finalNodePath;

        if (persistOriginalNode) {
            if (replaceExistingNode) {
                finalNodePath = destinationStorageNode.getNodePath().resolveFromRoot(Files.copy(currentNodePath.getPath(), targetNodePath.getPath(), StandardCopyOption.REPLACE_EXISTING));
            } else {
                finalNodePath = destinationStorageNode.getNodePath().resolveFromRoot(Files.copy(currentNodePath.getPath(), targetNodePath.getPath()));
            }
        } else {
            if (targetStorageNode.getIsDirectory()) {
                if (replaceExistingNode) {
                    throw new NodeTypeNotSupportedException("Cannot replace existing '%s' directory".formatted(targetStorageNode.getUrl()));
                }

                finalNodePath = destinationStorageNode.getNodePath().resolveFromRoot(Files.createDirectory(targetNodePath.getPath()));
            } else {
                if (replaceExistingNode) {
                    finalNodePath = destinationStorageNode.getNodePath().resolveFromRoot(Files.move(currentNodePath.getPath(), targetNodePath.getPath(), StandardCopyOption.REPLACE_EXISTING));
                } else {
                    finalNodePath = destinationStorageNode.getNodePath().resolveFromRoot(Files.move(currentNodePath.getPath(), targetNodePath.getPath()));
                }
            }
        }

        final StorageNode newStorageNode;
        if (persistOriginalNode) { /* Copied nodes acquire new attributes (defaults) */
            newStorageNode = this.storageServiceInterface.createNewStorageNode(destinationStorageNode, targetStorageNode.getIsDirectory() ? new ArrayList<>() : null, finalNodePath, null, null, null);
        } else { /* Moved nodes retain attributes of the original node */
            newStorageNode = this.storageServiceInterface.createNewStorageNode(destinationStorageNode, targetStorageNode.getIsDirectory() ? new ArrayList<>() : null, finalNodePath, targetStorageNode.getUser(), targetStorageNode.getGroup(), targetStorageNode.getPermissions());
        }

        storageLogger.info("%s '%s' has been %s to '%s' directory successfully".formatted(targetStorageNode.getIsDirectory() ? "Directory" : "File", targetStorageNode.getUrl(), persistOriginalNode ? "copied" : "moved", destinationStorageNode.getUrl()));
        return newStorageNode;
    }

    /**
     * @param targetStorageNode target node that is to be deleted
     * @return the target node for the purpose of confirming deletion
     * @throws IOException if the node removal attempt fails for any I/O reason
     * @throws InvalidParameterException if any of the provided parameters are invalid
     * @throws DirectoryNotEmptyException if deletion fails due to the directory being occupied
     * @throws Exception if anything not covered above was thrown
     */
    public StorageNode deleteStorageNode(StorageNode targetStorageNode) throws Exception {
        if (targetStorageNode == null) {
            throw new InvalidParameterException("Target node is invalid.".formatted());
        } else if (targetStorageNode.getParent() == null) {
            throw new InvalidParameterException("Root node is not targetable.".formatted());
        }

        if (!this.storageSecurityInterface.userHasRootPrivilages()) {
            if (!this.storageSecurityInterface.userHasRequiredPermissions(targetStorageNode.getParent(), false, true, true)) {
                throw new NodeInsufficientPermissionsException("Cannot remove %s '%s' due to insufficient permissions".formatted(targetStorageNode.getIsDirectory() ? "directory" : "file", targetStorageNode.getUrl()));
            }

            if (targetStorageNode.getParent().stickyBitIsSet() && !this.storageSecurityInterface.userIsTheOwnerOfTheNode(targetStorageNode) && !this.storageSecurityInterface.userIsTheOwnerOfTheNode(targetStorageNode.getParent())) {
                throw new NodeInsufficientPermissionsException("Cannot remove %s '%s' from a shared directory due to insufficient permissions".formatted(targetStorageNode.getIsDirectory() ? "directory" : "file", targetStorageNode.getUrl()));
            }
        }

        if (targetStorageNode.getId() != null) {
            this.storageServiceInterface.deleteStorageNode(targetStorageNode);
            this.storageHashMap.remove(targetStorageNode.getId());
        }

        targetStorageNode.getParent().getChildren().remove(targetStorageNode);
        Files.deleteIfExists(targetStorageNode.getNodePath().getPath());

        storageLogger.info("%s '%s' has been removed successfully".formatted(targetStorageNode.getIsDirectory() ? "Directory" : "File", targetStorageNode.getUrl()));
        return targetStorageNode.setNodePath(null).setId(null);
    }

    public StorageNode updateStorageNode(StorageNode targetStorageNode) throws Exception {
        /* TODO: update storage node! */
        return targetStorageNode;
    }

    /**
     * @param targetStorageNode target node permission flags of which are to be updated or set
     * @param permissionFlags target flags or operations on them described symbolically
     * @return The original storage node with new permission flags
     * @throws NodeInsufficientPermissionsException
     * @throws InvalidParameterException
     * @throws IllegalArgumentException
     */
    public StorageNode chmodStorageNode(StorageNode targetStorageNode, String permissionFlags) throws Exception {
        if (targetStorageNode == null) {
            throw new InvalidParameterException("Target node is invalid.".formatted());
        } else if (targetStorageNode.getParent() == null) {
            throw new InvalidParameterException("Root node is not targetable.".formatted());
        }

        if (!this.storageSecurityInterface.userHasRootPrivilages()) {
            if (!this.storageSecurityInterface.userIsTheOwnerOfTheNode(targetStorageNode)) {
                throw new NodeInsufficientPermissionsException("Cannot modify %s '%s' permission flags due to insufficient permissions".formatted(targetStorageNode.getIsDirectory() ? "directory" : "file", targetStorageNode.getUrl()));
            }
        }

        if (permissionFlags.matches("^[0-7]{3,4}$") != true) {
            String[] operationDescriptors = permissionFlags.contains(",") ? permissionFlags.split(",") : new String[]{permissionFlags};
            Pattern pattern = Pattern.compile("^((?:[ugo]{1,3}|a)?)([+=-])([rwxst]{0,5})$");
            ArrayList<String[]> operationsMatrix = new ArrayList<>();

            Integer integerPermissions = Integer.parseInt(targetStorageNode.getPermissions(), 8);
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

            storageLogger.info("%s '%s' permission flags have been updated to %o successfully".formatted(targetStorageNode.getIsDirectory() ? "Directory" : "File", targetStorageNode.getUrl(), integerPermissions));
            return targetStorageNode.setPermissions(String.format("%o", integerPermissions));
        }

        storageLogger.info("%s '%s' permission flags have been set to %s successfully".formatted(targetStorageNode.getIsDirectory() ? "Directory" : "File", targetStorageNode.getUrl(), permissionFlags));
        return targetStorageNode.setPermissions(permissionFlags);
    }

    /**
     * @param targetStorageNode
     * @param user
     * @param group
     * @return
     * @throws Exception
     */
    public StorageNode chownStorageNode(StorageNode targetStorageNode, String pairedOwners) throws Exception {
        if (targetStorageNode == null) {
            throw new InvalidParameterException("Target node is invalid.".formatted());
        } else if (targetStorageNode.getParent() == null) {
            throw new InvalidParameterException("Root node is not targetable.".formatted());
        }

        final String[] splitOwners = pairedOwners.split(":");
        final UUID user = splitOwners.length > 0 ? UUID.fromString(splitOwners[0]) : null;
        final UUID group = splitOwners.length > 1 ? UUID.fromString(splitOwners[1]) : null;

        if (!this.storageSecurityInterface.userHasRootPrivilages()) {
            if (!this.storageSecurityInterface.userHasRequiredPermissions(targetStorageNode.getParent(), false, true, true) || !this.storageSecurityInterface.userIsTheOwnerOfTheNode(targetStorageNode)) {
                throw new NodeInsufficientPermissionsException("Cannot modify %s '%s' ownership due to insufficient permissions".formatted(targetStorageNode.getIsDirectory() ? "directory" : "file", targetStorageNode.getUrl()));
            }

            if (user != null && !this.storageSecurityInterface.userCanInteractWithTheTargetUser(user)) {
                throw new NodeInsufficientPermissionsException("Cannot modify %s '%s' ownership because the user cannot interact with the target user".formatted(targetStorageNode.getIsDirectory() ? "directory" : "file", targetStorageNode.getUrl()));
            }

            if (group != null && !this.storageSecurityInterface.userBelongsToTheTargetGroup(group)) {
                throw new NodeInsufficientPermissionsException("Cannot modify %s '%s' ownership because the user doesn't belong to the target group".formatted(targetStorageNode.getIsDirectory() ? "directory" : "file", targetStorageNode.getUrl()));
            }
        }

        /* Done separately from the permission check to ensure that the change is not just partially permitted */
        if (user != null) targetStorageNode.setUser(user);
            
        /* Done separately from the permission check to ensure that the change is not just partially permitted */
        if (group != null) targetStorageNode.setGroup(group);

        storageLogger.info("%s '%s' ownership has been transfered to %s:%s successfully".formatted(targetStorageNode.getIsDirectory() ? "Directory" : "File", targetStorageNode.getUrl(), user != null ? user.toString() : "", group != null ? group.toString() : ""));
        return targetStorageNode;
    }

    /**
     * <p>Will query the quick access hash map and retrieve a matching node if it exists</p>
     * @param targetID UUID to query the hash map for
     * @return a matching node or a null in case of a no match
     */
    public StorageNode retrieveStorageNode(UUID targetID) {
        return this.storageHashMap.get(targetID);
    }

    /**
     * @return the root node of the storage tree
     */
    public StorageNode retrieveRootStorageNode() {
        return this.storageRootNode;
    }
}
