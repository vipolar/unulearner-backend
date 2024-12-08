package com.unulearner.backend.storage.data;

import java.util.UUID;
import java.util.Deque;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ArrayDeque;
import java.util.Collections;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import com.unulearner.backend.storage.extensions.NodePath;
import com.unulearner.backend.storage.miscellaneous.Holder;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.interfaces.StorageServiceInterface;
import com.unulearner.backend.storage.interfaces.StorageSecurityInterface;

import org.springframework.stereotype.Service;

import com.unulearner.backend.storage.exceptions.NodeToParentRelationsException;
import com.unulearner.backend.storage.exceptions.NodeTypeNotSupportedException;
import com.unulearner.backend.storage.exceptions.NodeInsufficientPermissions;
import com.unulearner.backend.storage.exceptions.NodeIsInaccessibleException;
import com.unulearner.backend.storage.exceptions.NodePublishingRaceException;
import com.unulearner.backend.storage.exceptions.StorageServiceException;

import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.security.InvalidParameterException;
import java.io.IOException;

@Service
public class StorageTree {
    private final StorageSecurityInterface storageSecurityInterface;
    private final StorageServiceInterface storageServiceInterface;
    private final HashMap<UUID, StorageNode> storageHashMap;
    private final NodePath storageRootDirectoryPath;
    private final StorageNode storageRootNode;

    public StorageTree(StorageSecurityInterface storageSecurityInterface, StorageServiceInterface storageServiceInterface) {
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

                            targetStorageNode.setChildren(storageServiceInterface.retrieveChildrenStorageNodes(targetStorageNode));
                            if (!targetStorageNode.getChildren().isEmpty()) { /* Sorting at this stage and then inserting accordingly seems like a better idea than throwing it all in together and sorting on postDirectoryVisit */
                                Collections.sort(targetStorageNode.getChildren(), storageServiceInterface.getStorageNodeSortingComparator());
                            }
                        } catch (Exception exception) {
                            final StorageNode newStorageNode;

                            if (parentStorageNode != null) {
                                newStorageNode = storageServiceInterface.createNewStorageNode(parentStorageNode, new ArrayList<>(), directoryNodePath, null, null, null);
                            } else {
                                newStorageNode = storageServiceInterface.createRootStorageNode(new ArrayList<>(), rootDirectoryPath);
                            }

                            //TODO: log exception
                            targetStorageNode = storageServiceInterface.saveStorageNode(newStorageNode);
                        }

                        if (parentStorageNode != null) { /* null parent is only allowed in the case of the root node. No other node will go past the pre-commit check with its parent set to null */
                            final Integer iNode = Collections.binarySearch(parentStorageNode.getChildren(), targetStorageNode, storageServiceInterface.getStorageNodeIndexComparator());

                            if (iNode >= 0) {
                                parentStorageNode.getChildren().set(iNode, targetStorageNode);
                            } else {
                                parentStorageNode.getChildren().add((-iNode - 1), targetStorageNode);
                            }
                        }

                        if (storageHashMap.put(targetStorageNode.getId(), targetStorageNode) != null) {
                            throw new FileAlreadyExistsException("Directory node already exists on the storage tree hashmap!".formatted(directoryNodePath.getRelativePath().toString()));
                        }

                        targetStorageNode.setIsAccessible(true);
                        rootDirectoryNodeDeque.offer(targetStorageNode);
                    } catch (Exception exception) {
                        System.out.println("Failed to add directory '%s' to the storage tree: %s".formatted(directoryPath.toString(), exception.getMessage()));
                        exception.printStackTrace();
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    System.out.println("Directory '%s' was successfully added to the storage tree.".formatted(directoryPath.toString()));
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
                        } catch (Exception exception) {
                            //TODO: log exception
                            targetStorageNode = storageServiceInterface.saveStorageNode(storageServiceInterface.createNewStorageNode(parentStorageNode, null, fileNodePath, null, null, null));
                        }       
                        
                        final Integer iNode = Collections.binarySearch(parentStorageNode.getChildren(), targetStorageNode, storageServiceInterface.getStorageNodeIndexComparator());
                        if (iNode >= 0) {
                            parentStorageNode.getChildren().set(iNode, targetStorageNode);
                        } else {
                            parentStorageNode.getChildren().add((-iNode - 1), targetStorageNode);
                        }

                        if (storageHashMap.put(targetStorageNode.getId(), targetStorageNode) != null) {
                            throw new FileAlreadyExistsException("File node already exists on the storage tree hashmap.".formatted());
                        }

                        targetStorageNode.setIsAccessible(true);
                    } catch (Exception exception) {
                        System.out.println("Failed to add file '%s' to the storage tree: %s".formatted(filePath.toString(), exception.getMessage()));
                        exception.printStackTrace();
                        return FileVisitResult.CONTINUE;
                    }

                    System.out.println("File '%s' was successfully added to the storage tree.".formatted(filePath.toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path filePath, IOException exception) {
                    if (exception != null) {
                        exception.printStackTrace();
                        System.out.println("Failed to visit file '%s': %s".formatted(filePath.toString(), exception.getMessage()));
                        return FileVisitResult.CONTINUE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directoryPath, IOException exception) {
                    if (exception != null) {
                        exception.printStackTrace();
                        System.out.println("Failed to visit directory '%s': %s".formatted(directoryPath.toString(), exception.getMessage()));
                        return FileVisitResult.CONTINUE;
                    }

                    final StorageNode directoryStorageNode = rootDirectoryNodeDeque.pollLast();
                    long fileCount = 0, directoryCount = 0, visitedCount = 0, confirmedCount = 0, accessibleCount = 0;
                    for (StorageNode node : directoryStorageNode.getChildren()) {
                        visitedCount++;

                        if (node.getIsAccessible()) {
                            accessibleCount++;
                        }

                        if (node.getIsConfirmed()) {
                            confirmedCount++;
                        }

                        if (node.isDirectory()) {
                            directoryCount++;
                        } else {
                            fileCount++;
                        }
                    }

                    if (directoryStorageNode.getParent() == null) {
                        rootStorageNodeHolder.setValue(directoryStorageNode);
                    }

                    System.out.println("Directory '%s' visited successfully. Total nodes: %d. Directory nodes: %d. File Nodes: %d. Accessible nodes: %d. Confirmed Nodes: %d.".formatted(directoryPath.toString(), visitedCount, directoryCount, fileCount, accessibleCount, confirmedCount));
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

        final StorageNode savedStorageNode = this.storageServiceInterface.saveStorageNode(targetStorageNode);
        final StorageNode parentStorageNode = savedStorageNode.getParent();

        /* If search by ID turns up with anything then this is an update job */
        for (int iNode = 0; iNode < parentStorageNode.getChildren().size(); iNode++) {
            if (parentStorageNode.getChildren().get(iNode).getId().equals(savedStorageNode.getId())) {
                parentStorageNode.getChildren().remove(iNode); /* Remove now and replace later */
            }
        }

        /* If search by name turns up with anything then we have a big problem */
        final Comparator<StorageNode> storageNodeComparator = Comparator.comparing((StorageNode node) -> node.isDirectory() ? 0 : 1).thenComparing(StorageNode::getName);
        final Integer iNode = Collections.binarySearch(parentStorageNode.getChildren(), savedStorageNode, storageNodeComparator);
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
        savedStorageNode.setIsAccessible(true);
        savedStorageNode.setIsConfirmed(true);

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
        } else if (!destinationStorageNode.isDirectory() || !destinationStorageNode.getNodePath().isValidDirectory()) {
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
        newStorageNode.setIsAccessible(true);
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
    public StorageNode createStorageNode(File newFile, StorageNode newStorageNode, StorageNode destinationStorageNode) throws Exception {
        if (newStorageNode == null || newStorageNode.getId() != null || newStorageNode.getNodePath() != null) {
            throw new InvalidParameterException("New node is invalid.".formatted());
        }

        if (destinationStorageNode == null || destinationStorageNode.getId() == null || destinationStorageNode.getNodePath() == null) {
            throw new InvalidParameterException("Destination node is invalid.".formatted());
        } else if (!destinationStorageNode.isDirectory() || !destinationStorageNode.getNodePath().isValidDirectory()) {
            throw new InvalidParameterException("Destination node is not a directory.".formatted());
        }

        for (int iNode = 0; iNode < destinationStorageNode.getChildren().size(); iNode++) {
            if (destinationStorageNode.getChildren().get(iNode).getName().equals(newStorageNode.getName())) {
                throw new FileAlreadyExistsException("Node '%s' already exists in '%s' directory.".formatted(newStorageNode.getName(), destinationStorageNode.getUrl()));
            }
        }

        final NodePath finalNodePath = destinationStorageNode.getNodePath().resolve(newStorageNode.getName());
        if (newFile != null) {
            try {
                Files.move(Path.of(newFile.getPath()), finalNodePath.getPath());
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

        newStorageNode.setParent(destinationStorageNode);
        newStorageNode.setIsAccessible(true);
        newStorageNode.setNodePath(finalNodePath);

        return newStorageNode;
    }

    /**
     * <p>Transfer a node to a new destination (copy/move)</p>
     * @param newStorageNode an incomplete, unpublished node that is to be created
     * @param targetStorageNode target node that is to be transfered to a new destination
     * @param destinationStorageNode destination node that the target is to be transfered into
     * @param move if true the node will be moved instead of being copied (default: false)
     * @param replace if true the node will replace the conflicting node (default: false)
     * @return the completed node with a valid path to its physical part (unpublished)
     * @throws IOException if the node transfer attempt fails for any I/O reason
     * @throws InvalidParameterException if any of the provided parameters are invalid
     * @throws FileAlreadyExistsException if a node under the same name already exists under this destination 
     * @throws Exception if anything not covered above was thrown
     */
    public StorageNode transferStorageNode(StorageNode targetStorageNode, StorageNode destinationStorageNode, String newName, Boolean move, Boolean replace) throws Exception {
        if (targetStorageNode == null || targetStorageNode.getId() == null || targetStorageNode.getNodePath() == null) {
            throw new InvalidParameterException("Target node is invalid.".formatted());
        } else if (targetStorageNode.getParent() == null) {
            throw new InvalidParameterException("Root node is not targetable.".formatted());
        }

        if (destinationStorageNode == null || destinationStorageNode.getId() == null || destinationStorageNode.getNodePath() == null) {
            throw new InvalidParameterException("Destination node is invalid.".formatted());
        } else if (!destinationStorageNode.isDirectory() || !destinationStorageNode.getNodePath().isValidDirectory()) {
            throw new InvalidParameterException("Destination node is not a directory.".formatted());
        }

        final String newStorageNodeName = newName != null ? newName : targetStorageNode.getName();
        /*for (int iNode = 0; iNode < destinationStorageNode.getChildren().size(); iNode++) {
            if (destinationStorageNode.getChildren().get(iNode).getName().equals(newStorageNodeName)) {
                throw new FileAlreadyExistsException("Node '%s' already exists in '%s' directory.".formatted(newStorageNodeName, destinationStorageNode.getUrl()));
            }
        } */

        final NodePath targetNodePath = destinationStorageNode.getNodePath().resolve(newStorageNodeName);
        final NodePath currentNodePath = targetStorageNode.getNodePath();

        final Boolean replaceExisting = replace == null ? false : replace;
        final Boolean moveNode = move == null ? false : move;
        final NodePath finalNodePath;

        if (moveNode) { /* throw errors! */ /* TODO: better errors? */
            if (!this.storageSecurityInterface.userHasRequiredPermissions(destinationStorageNode, null, true, true)) {
                throw new NodeInsufficientPermissions("Cannot move %s '%s' to directory '%s' due to insufficient permissions".formatted(targetStorageNode.isDirectory() ? "directory" : "file", targetStorageNode.getName(), destinationStorageNode.getUrl()));
            }

            if (!this.storageSecurityInterface.userHasRequiredPermissions(targetStorageNode.getParent(), null, true, true)) {
                throw new NodeInsufficientPermissions("Cannot move %s '%s' from directory '%s' due to insufficient permissions".formatted(targetStorageNode.isDirectory() ? "directory" : "file", targetStorageNode.getName(), targetStorageNode.getParent().getUrl()));
            }
        } else {
            if (!this.storageSecurityInterface.userHasRequiredPermissions(targetStorageNode, true, null, null)) {
                throw new NodeInsufficientPermissions("Cannot copy %s '%s' to directory '%s' due to insufficient permissions".formatted(targetStorageNode.isDirectory() ? "directory" : "file", targetStorageNode.getName(), destinationStorageNode.getUrl()));
            }
            if (!this.storageSecurityInterface.userHasRequiredPermissions(destinationStorageNode, null, true, true)) {
                throw new NodeInsufficientPermissions("Cannot copy %s '%s' to directory '%s' due to insufficient permissions".formatted(targetStorageNode.isDirectory() ? "directory" : "file", targetStorageNode.getName(), destinationStorageNode.getUrl()));
            }
        }

        if (moveNode) {
            if (targetStorageNode.isDirectory()) {
                if (replaceExisting) {
                    throw new NodeTypeNotSupportedException("Cannot replace existing '%s' directory".formatted(targetStorageNode.getUrl()));
                }

                finalNodePath = destinationStorageNode.getNodePath().resolveFromRoot(Files.createDirectory(targetNodePath.getPath()));
            } else {
                if (replaceExisting) {
                    finalNodePath = destinationStorageNode.getNodePath().resolveFromRoot(Files.move(currentNodePath.getPath(), targetNodePath.getPath(), StandardCopyOption.REPLACE_EXISTING));
                } else {
                    finalNodePath = destinationStorageNode.getNodePath().resolveFromRoot(Files.move(currentNodePath.getPath(), targetNodePath.getPath()));
                }
            }
        } else {
            if (replaceExisting) {
                finalNodePath = destinationStorageNode.getNodePath().resolveFromRoot(Files.copy(currentNodePath.getPath(), targetNodePath.getPath(), StandardCopyOption.REPLACE_EXISTING));
            } else {
                finalNodePath = destinationStorageNode.getNodePath().resolveFromRoot(Files.copy(currentNodePath.getPath(), targetNodePath.getPath()));
            }
        }

        final StorageNode newStorageNode;
        if (moveNode) { /* Moved nodes retain attributes of the original node */
            newStorageNode = this.storageServiceInterface.createNewStorageNode(destinationStorageNode, targetStorageNode.isDirectory() ? new ArrayList<>() : null, finalNodePath, targetStorageNode.getUser(), targetStorageNode.getGroup(), targetStorageNode.getPermissions());
        } else { /* Copied nodes acquire new attributes */
            newStorageNode = this.storageServiceInterface.createNewStorageNode(destinationStorageNode, targetStorageNode.isDirectory() ? new ArrayList<>() : null, finalNodePath, null, null, null);
        }

        return newStorageNode.setIsAccessible(true);
    }

    /**
     * <p>Rename a node (move around in the same destination)</p>
     * @param newStorageNode an incomplete, unpublished node that is to be created
     * @param targetStorageNode target node that is to be renamed
     * @return the completed node with a valid path to its physical part (unpublished)
     * @throws IOException if the node rename attempt fails for any I/O reason
     * @throws InvalidParameterException if any of the provided parameters are invalid
     * @throws FileAlreadyExistsException if a node under the same name already exists under this destination 
     * @throws Exception if anything not covered above was thrown
     */
    public StorageNode renameStorageNode(StorageNode newStorageNode, StorageNode targetStorageNode) throws Exception {
        if (newStorageNode == null || newStorageNode.getId() != null || newStorageNode.getNodePath() != null) {
            throw new InvalidParameterException("New node is invalid".formatted());
        }

        if (targetStorageNode == null || targetStorageNode.getId() == null || targetStorageNode.getNodePath() == null) {
            throw new InvalidParameterException("Target node is invalid.".formatted());
        } else if (targetStorageNode.getParent() == null) {
            throw new InvalidParameterException("Root node is not targetable.".formatted());
        }

        final NodePath finalNodePath = targetStorageNode.getNodePath().resolveFromRoot(Files.move(targetStorageNode.getNodePath().getPath(), targetStorageNode.getNodePath().getPath().resolveSibling(newStorageNode.getName())));

        newStorageNode.setParent(targetStorageNode.getParent());
        newStorageNode.setIsAccessible(true);
        newStorageNode.setNodePath(finalNodePath);
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

        if (targetStorageNode.getId() != null) {
            /* TODO: rethink this... should we actually delete it from the database? */
            this.storageServiceInterface.deleteStorageNode(targetStorageNode);
        }

        targetStorageNode.getParent().getChildren().remove(targetStorageNode);
        this.storageHashMap.remove(targetStorageNode.getId());

        Files.deleteIfExists(targetStorageNode.getNodePath().getPath());

        targetStorageNode.setIsAccessible(false); 
        targetStorageNode.setIsConfirmed(false);
        targetStorageNode.setNodePath(null);
        targetStorageNode.setId(null);

        return targetStorageNode;
    }

    public StorageNode updateStorageNode(StorageNode targetStorageNode) throws Exception {
        return targetStorageNode;
    }

    public StorageNode chmodStorageNode(StorageNode targetStorageNode, Short flags) throws Exception {
        return targetStorageNode;
    }

    public StorageNode chownStorageNode(StorageNode targetStorageNode, UUID user, UUID group) throws Exception {
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
