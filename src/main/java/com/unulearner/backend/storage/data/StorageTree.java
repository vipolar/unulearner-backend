package com.unulearner.backend.storage.data;

import java.util.UUID;
import java.util.Deque;
import java.util.HashMap;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ArrayDeque;
import java.util.Collections;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import com.unulearner.backend.storage.properties.StorageProperties;
import com.unulearner.backend.storage.repository.StorageRepository;
import com.unulearner.backend.storage.miscellaneous.Holder;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.extensions.NodePath;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;

import com.unulearner.backend.storage.exceptions.FileToParentRelationsException;
import com.unulearner.backend.storage.exceptions.FileTypeNotSupportedException;
import com.unulearner.backend.storage.exceptions.FileIsInaccessibleException;
import com.unulearner.backend.storage.exceptions.StorageServiceException;
import java.nio.file.FileAlreadyExistsException;
import java.security.InvalidParameterException;
import java.io.IOException;

@Service
public class StorageTree {
    private final HashMap<UUID, StorageNode> storageHashMap;
    private final StorageProperties storageTreeProperties;
    private final StorageRepository storageRepository;
    private final NodePath storageRootDirectoryPath;
    private final StorageNode storageRootNode;

    public StorageTree(StorageProperties storageProperties, StorageRepository storageRepository) {
        this.storageHashMap = new HashMap<UUID, StorageNode>();
        this.storageTreeProperties = storageProperties;
        this.storageRepository = storageRepository;

        try {
            this.storageRootDirectoryPath = new NodePath(Files.createDirectories(Paths.get(this.storageTreeProperties.getRootDirectory())));
            if (!this.storageRootDirectoryPath.isValidDirectory(false)) {
                throw new RuntimeException("Root '%s' directory is inaccessible or nonexistent.".formatted(this.storageTreeProperties.getRootDirectory()));
            }
        } catch (Exception exception) {
            /* If it got here then we have no choice but to crash it! */
            throw new RuntimeException("Fatal error: %s\nFailed to build storage tree.\nExiting the application...".formatted(exception.getMessage()));
        }

        /* TODO: better exception handling and by that I mean actually doing something when a non-fatal exception is caught */
        try {
            final NodePath rootDirectoryPath = this.storageRootDirectoryPath;
            final HashMap<UUID, StorageNode> storageHashMap = this.storageHashMap;
            final Holder<StorageNode> rootStorageTreeNodePlaceHolder = new Holder<>();
            final Deque<StorageNode> rootDirectoryNodeDeque = new ArrayDeque<StorageNode>();

            Files.walkFileTree(rootDirectoryPath.getPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directoryPath, BasicFileAttributes attrs) {
                    try {
                        final NodePath directoryNodePath = rootDirectoryPath.resolveFromRoot(directoryPath);

                        if (!directoryNodePath.isValidDirectory()) {
                            throw new FileIsInaccessibleException("Directory is inaccessible.".formatted());
                        }

                        final StorageNode targetStorageTreeNode;
                        final StorageNode parentStorageTreeNode = rootDirectoryNodeDeque.peekLast();
                        final Optional<StorageNode> nullableStorageTreeNode = storageRepository.findByOnDiskURL(directoryNodePath.getRelativePath().toString());
                        if (nullableStorageTreeNode.isPresent()) {
                            targetStorageTreeNode = nullableStorageTreeNode.get();
                            targetStorageTreeNode.setNodePath(directoryNodePath);

                            if (parentStorageTreeNode != null && targetStorageTreeNode.getParent() != null && !parentStorageTreeNode.getId().equals(targetStorageTreeNode.getParent().getId())) {
                                throw new FileToParentRelationsException("Directory is supposedly a child of directory '%s' but the relationship is not mirrored on the persistent level".formatted(parentStorageTreeNode.getOnDiskURL()));
                            }

                            targetStorageTreeNode.setChildren(storageRepository.findAllByParent(targetStorageTreeNode));

                            if (!targetStorageTreeNode.getChildren().isEmpty()) { /* Sorting at this stage and then inserting accordingly seems like a better idea than throwing it all in together and sorting on postDirectoryVisit */
                                final Comparator<StorageNode> storageTreeNodeComparator = Comparator.comparing((StorageNode iNode) -> iNode.isDirectory() ? 0 : 1).thenComparing(StorageNode::getOnDiskName);
                                Collections.sort(targetStorageTreeNode.getChildren(), storageTreeNodeComparator);
                            }
                        } else {
                            if (parentStorageTreeNode == null) { /* null parent is only allowed in the case of the root node. No other node will go past the pre-commit check with its parent set to null */
                                targetStorageTreeNode = storageRepository.save(new StorageNode(parentStorageTreeNode, new ArrayList<>(), directoryNodePath, storageTreeProperties.getRootDirectoryDescription()));
                            } else {
                                targetStorageTreeNode = storageRepository.save(new StorageNode(parentStorageTreeNode, new ArrayList<>(), directoryNodePath, storageTreeProperties.getRecoveredDirectoryDescription()));
                            }
                        }
                        
                        if (parentStorageTreeNode != null) { /* null parent is only allowed in the case of the root node. No other node will go past the pre-commit check with its parent set to null */
                            final Comparator<StorageNode> storageTreeNodeComparator = Comparator.comparing((StorageNode iNode) -> iNode.isDirectory() ? 0 : 1).thenComparing(StorageNode::getOnDiskName);
                            final Integer iNode = Collections.binarySearch(parentStorageTreeNode.getChildren(), targetStorageTreeNode, storageTreeNodeComparator);
                            if (iNode >= 0) {
                                parentStorageTreeNode.getChildren().set(iNode, targetStorageTreeNode);
                            } else {
                                parentStorageTreeNode.getChildren().add((-iNode - 1), targetStorageTreeNode);
                            }
                        }

                        if (storageHashMap.put(targetStorageTreeNode.getId(), targetStorageTreeNode) != null) {
                            throw new FileAlreadyExistsException("Directory node already exists on the storage tree hashmap!".formatted(directoryNodePath.getRelativePath().toString()));
                        }

                        targetStorageTreeNode.setIsAccessible(true);
                        rootDirectoryNodeDeque.offer(targetStorageTreeNode);
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

                        if (!fileNodePath.isValidFile()) {
                            throw new FileIsInaccessibleException("File is inaccessible.".formatted());
                        }

                        final StorageNode targetStorageTreeNode;
                        final StorageNode parentStorageTreeNode = rootDirectoryNodeDeque.peekLast();
                        final Optional<StorageNode> nullableStorageTreeNode = storageRepository.findByOnDiskURL(fileNodePath.getRelativePath().toString());
                        if (nullableStorageTreeNode.isPresent()) {
                            targetStorageTreeNode = nullableStorageTreeNode.get();
                            targetStorageTreeNode.setNodePath(fileNodePath);

                            if (!parentStorageTreeNode.getId().equals(targetStorageTreeNode.getParent().getId())) {
                                throw new FileToParentRelationsException("File is supposedly a child of directory '%s' but the relationship is not mirrored on the persistent level".formatted(parentStorageTreeNode.getOnDiskURL()));
                            }
                        } else {
                            targetStorageTreeNode = storageRepository.save(new StorageNode(parentStorageTreeNode, null, fileNodePath, storageTreeProperties.getRecoveredFileDescription()));
                        }           
                        
                        final Comparator<StorageNode> storageTreeNodeComparator = Comparator.comparing((StorageNode iNode) -> iNode.isDirectory() ? 0 : 1).thenComparing(StorageNode::getOnDiskName);
                        final Integer iNode = Collections.binarySearch(parentStorageTreeNode.getChildren(), targetStorageTreeNode, storageTreeNodeComparator);
                        if (iNode >= 0) {
                            parentStorageTreeNode.getChildren().set(iNode, targetStorageTreeNode);
                        } else {
                            parentStorageTreeNode.getChildren().add((-iNode - 1), targetStorageTreeNode);
                        }

                        if (storageHashMap.put(targetStorageTreeNode.getId(), targetStorageTreeNode) != null) {
                            throw new FileAlreadyExistsException("File node already exists on the storage tree hashmap.".formatted());
                        }

                        targetStorageTreeNode.setIsAccessible(true);
                    } catch (Exception exception) {
                        System.out.println("Failed to add file '%s' to the storage tree: %s".formatted(filePath.toString(), exception.getMessage()));
                        exception.printStackTrace();
                        return FileVisitResult.CONTINUE;
                    }

                    System.out.println("File '%s' was successfully added to the storage tree.".formatted(filePath.toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path filePath, IOException exc) {
                    if (exc != null) {
                        exc.printStackTrace();
                        System.out.println("Failed to visit file '%s': %s".formatted(filePath.toString(), exc.getMessage()));
                        return FileVisitResult.CONTINUE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directoryPath, IOException exc) {
                    if (exc != null) {
                        exc.printStackTrace();
                        System.out.println("Failed to visit directory '%s': %s".formatted(directoryPath.toString(), exc.getMessage()));
                        return FileVisitResult.CONTINUE;
                    }

                    final StorageNode directoryStorageTreeNode = rootDirectoryNodeDeque.pollLast();
                    long fileCount = 0, directoryCount = 0, visitedCount = 0, confirmedCount = 0, accessibleCount = 0;
                    for (StorageNode node : directoryStorageTreeNode.getChildren()) {
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

                    if (directoryStorageTreeNode.getParent() == null) {
                        rootStorageTreeNodePlaceHolder.setValue(directoryStorageTreeNode);
                    }

                    System.out.println("Directory '%s' visited successfully. Total nodes: %d. Directory nodes: %d. File Nodes: %d. Accessible nodes: %d. Confirmed Nodes: %d.".formatted(directoryPath.toString(), visitedCount, directoryCount, fileCount, accessibleCount, confirmedCount));
                    return FileVisitResult.CONTINUE;
                }
            });

            if ((this.storageRootNode = rootStorageTreeNodePlaceHolder.getValue()) == null) {
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
     * @param targetStorageTreeNode node to be published or updated
     * @return published (or updated) node
     * @throws InvalidParameterException if any of the provided parameters are invalid
     * @throws FileAlreadyExistsException oh boy this is a doozy one...
     * @throws Exception if anything not covered above was thrown
     */
    public StorageNode publishStorageTreeNode(StorageNode targetStorageTreeNode) throws Exception {
        if (targetStorageTreeNode == null || targetStorageTreeNode.getNodePath() == null) {
            throw new InvalidParameterException("Target node is invalid.".formatted());
        } else if (targetStorageTreeNode.getParent() == null) {
            throw new InvalidParameterException("Root node is not targetable.".formatted());
        }

        final StorageNode savedStorageTreeNode = storageRepository.save(targetStorageTreeNode);
        final StorageNode parentStorageTreeNode = savedStorageTreeNode.getParent();

        /* If search by ID turns up with anything then this is an update job */
        for (int iNode = 0; iNode < parentStorageTreeNode.getChildren().size(); iNode++) {
            if (parentStorageTreeNode.getChildren().get(iNode).getId().equals(savedStorageTreeNode.getId())) {
                parentStorageTreeNode.getChildren().remove(iNode); /* Remove and replace later */
            }
        }

        /* If search by name turns up with anything then we have a big problem */
        final Comparator<StorageNode> storageTreeNodeComparator = Comparator.comparing((StorageNode node) -> node.isDirectory() ? 0 : 1).thenComparing(StorageNode::getOnDiskName);
        final Integer iNode = Collections.binarySearch(parentStorageTreeNode.getChildren(), savedStorageTreeNode, storageTreeNodeComparator);
        if (iNode >= 0) { /* If ID matches then update is permissible... although it should never come to this... */
            if (!savedStorageTreeNode.getId().equals(parentStorageTreeNode.getChildren().get(iNode).getId())) {
                throw new FileAlreadyExistsException("WHAT THE FUCK?!!!".formatted()); /* TODO: ??? */
            }

            parentStorageTreeNode.getChildren().set(iNode, savedStorageTreeNode);
        } else {            
            parentStorageTreeNode.getChildren().add((-iNode - 1), savedStorageTreeNode);
        }
        
        this.storageHashMap.put(savedStorageTreeNode.getId(), savedStorageTreeNode);
        savedStorageTreeNode.setIsAccessible(true);

        return savedStorageTreeNode;
    }

    /**
     * <p>Find an existing node by name, whether it exists as a full-blown node on the tree or just as a physical node in the drive and recover it</p>
     * @param targetNodeName name of the node to be searched for in the parent node directory
     * @param destinationStorageTreeNode existing parent node to which the new node will be attached
     * @return a newly created node with a path to the file that was previously uncatalogued
     * @throws InvalidParameterException if any of the provided parameters are invalid
     * @throws FileIsInaccessibleException if file is inaccessible or nonexistent
     * @throws FileTypeNotSupportedException if file type is not supported
     * @throws Exception if anything not covered above was thrown
     */
    public StorageNode recoverStorageTreeNode(String targetNodeName, StorageNode destinationStorageTreeNode) throws Exception {
        if (targetNodeName == null || targetNodeName.isBlank()) {
            throw new InvalidParameterException("Target node name cannot be blank.".formatted());
        }

        if (destinationStorageTreeNode == null || destinationStorageTreeNode.getId() == null || destinationStorageTreeNode.getNodePath() == null) {
            throw new InvalidParameterException("Destination node is invalid.".formatted());
        } else if (!destinationStorageTreeNode.isDirectory() || !destinationStorageTreeNode.getNodePath().isValidDirectory()) {
            throw new InvalidParameterException("Destination node is not a directory.".formatted());
        }

        /* If node is already there (whether the file is actually accessible or not) */
        for (int iNode = 0; iNode < destinationStorageTreeNode.getChildren().size(); iNode++) {
            if (destinationStorageTreeNode.getChildren().get(iNode).getOnDiskName().equals(targetNodeName)) {
                return destinationStorageTreeNode.getChildren().get(iNode);
            }
        }
        
        /* If node is not there... */
        final StorageNode newStorageTreeNode;
        final NodePath targetPath = destinationStorageTreeNode.getNodePath().resolve(targetNodeName);
        if (targetPath.isValidDirectory()) {
            newStorageTreeNode = new StorageNode(destinationStorageTreeNode, new ArrayList<>(), targetPath, this.storageTreeProperties.getRecoveredDirectoryDescription());
        } else if (targetPath.isValidFile()) {
            newStorageTreeNode = new StorageNode(destinationStorageTreeNode, null, targetPath, this.storageTreeProperties.getRecoveredFileDescription());
        } else if (targetPath.isValidNode()) {
            throw new FileTypeNotSupportedException("Node '%s' is of an unsupported file type.".formatted(targetPath.getPath().toString()));
        } else {
            throw new FileIsInaccessibleException("Node '%s' is inaccessible or nonexistent".formatted(targetPath.getPath().toString()));
        }

        newStorageTreeNode.setIsAccessible(true);

        return newStorageTreeNode;
    }

    /**
     * <p>Create a new node with a valid physical path attached to it</p>
     * @param newFile file to be written to disk (applicable only to file nodes)
     * @param newStorageTreeNode an incomplete, unpublished node that is to be created
     * @param destinationStorageTreeNode destination node where the new node is to be created
     * @return the completed node with a valid path to its physical part (unpublished)
     * @throws IOException if the node creation attempt fails for any I/O reason
     * @throws InvalidParameterException if any of the provided parameters are invalid
     * @throws FileAlreadyExistsException if a node under the same name already exists under this destination 
     * @throws Exception if anything not covered above was thrown
     */
    public StorageNode createStorageTreeNode(MultipartFile newFile, StorageNode newStorageTreeNode, StorageNode destinationStorageTreeNode) throws Exception {
        if (newStorageTreeNode == null || newStorageTreeNode.getId() != null || newStorageTreeNode.getNodePath() != null) {
            throw new InvalidParameterException("New node is invalid.".formatted());
        }

        if (destinationStorageTreeNode == null || destinationStorageTreeNode.getId() == null || destinationStorageTreeNode.getNodePath() == null) {
            throw new InvalidParameterException("Destination node is invalid.".formatted());
        } else if (!destinationStorageTreeNode.isDirectory() || !destinationStorageTreeNode.getNodePath().isValidDirectory()) {
            throw new InvalidParameterException("Destination node is not a directory.".formatted());
        }

        for (int iNode = 0; iNode < destinationStorageTreeNode.getChildren().size(); iNode++) {
            if (destinationStorageTreeNode.getChildren().get(iNode).getOnDiskName().equals(newStorageTreeNode.getOnDiskName())) {
                throw new FileAlreadyExistsException("Node '%s' already exists in '%s' directory.".formatted(newStorageTreeNode.getOnDiskName(), destinationStorageTreeNode.getOnDiskURL()));
            }
        }

        final NodePath finalNodePath = destinationStorageTreeNode.getNodePath().resolve(newStorageTreeNode.getOnDiskName());

        if (newFile != null) {
            if (Files.copy(newFile.getInputStream(), finalNodePath.getPath()) < 0) {
                throw new StorageServiceException("Input stream couldn't be copied to '%s' file".formatted(finalNodePath));
            }

            /* TODO: Handle input streams better... */
            newFile.getInputStream().close();
        } else {
            if (Files.createDirectory(finalNodePath.getPath()) == null) {
                throw new StorageServiceException("Directory '%s' couldn't be created".formatted(finalNodePath));
            }
        }

        newStorageTreeNode.setParent(destinationStorageTreeNode);
        newStorageTreeNode.setIsAccessible(true);
        newStorageTreeNode.setNodePath(finalNodePath);

        return newStorageTreeNode;
    }

    /**
     * <p>Transfer a node to a new destination (copy/move)</p>
     * @param newStorageTreeNode an incomplete, unpublished node that is to be created
     * @param targetStorageTreeNode target node that is to be transfered to a new destination
     * @param destinationStorageTreeNode destination node that the target is to be transfered into
     * @param move if true the node will be moved instead of being copied (default: false)
     * @param replace if true the node will replace the conflicting node (default: false)
     * @return the completed node with a valid path to its physical part (unpublished)
     * @throws IOException if the node transfer attempt fails for any I/O reason
     * @throws InvalidParameterException if any of the provided parameters are invalid
     * @throws FileAlreadyExistsException if a node under the same name already exists under this destination 
     * @throws Exception if anything not covered above was thrown
     */
    public StorageNode transferStorageTreeNode(StorageNode newStorageTreeNode, StorageNode targetStorageTreeNode, StorageNode destinationStorageTreeNode, Boolean move, Boolean replace) throws Exception {
        if (newStorageTreeNode == null || newStorageTreeNode.getId() != null || newStorageTreeNode.getNodePath() != null) {
            throw new InvalidParameterException("New node is invalid".formatted());
        }

        if (targetStorageTreeNode == null || targetStorageTreeNode.getId() == null || targetStorageTreeNode.getNodePath() == null) {
            throw new InvalidParameterException("Target node is invalid.".formatted());
        } else if (targetStorageTreeNode.getParent() == null) {
            throw new InvalidParameterException("Root node is not targetable.".formatted());
        }

        if (destinationStorageTreeNode == null || destinationStorageTreeNode.getId() == null || destinationStorageTreeNode.getNodePath() == null) {
            throw new InvalidParameterException("Destination node is invalid.".formatted());
        } else if (!destinationStorageTreeNode.isDirectory() || !destinationStorageTreeNode.getNodePath().isValidDirectory()) {
            throw new InvalidParameterException("Destination node is not a directory.".formatted());
        }

        for (int iNode = 0; iNode < destinationStorageTreeNode.getChildren().size(); iNode++) {
            if (destinationStorageTreeNode.getChildren().get(iNode).getOnDiskName().equals(newStorageTreeNode.getOnDiskName())) {
                throw new FileAlreadyExistsException("Node '%s' already exists in '%s' directory.".formatted(newStorageTreeNode.getOnDiskName(), destinationStorageTreeNode.getOnDiskURL()));
            }
        }

        final NodePath targetNodePath = destinationStorageTreeNode.getNodePath().resolve(newStorageTreeNode.getOnDiskName());
        final NodePath currentNodePath = targetStorageTreeNode.getNodePath();
        final Boolean replaceExisting = replace == null ? false : replace;
        final Boolean moveNode = move == null ? false : move;
        final NodePath finalNodePath;

        if (moveNode && !targetStorageTreeNode.isDirectory()) {
            if (replaceExisting) {
                finalNodePath = destinationStorageTreeNode.getNodePath().resolveFromRoot(Files.move(currentNodePath.getPath(), targetNodePath.getPath(), StandardCopyOption.REPLACE_EXISTING));
            } else {
                finalNodePath = destinationStorageTreeNode.getNodePath().resolveFromRoot(Files.move(currentNodePath.getPath(), targetNodePath.getPath()));
            }
        } else {
            if (replaceExisting) {
                finalNodePath = destinationStorageTreeNode.getNodePath().resolveFromRoot(Files.copy(currentNodePath.getPath(), targetNodePath.getPath(), StandardCopyOption.REPLACE_EXISTING));
            } else {
                finalNodePath = destinationStorageTreeNode.getNodePath().resolveFromRoot(Files.copy(currentNodePath.getPath(), targetNodePath.getPath()));
            }
        }

        newStorageTreeNode.setParent(destinationStorageTreeNode);
        newStorageTreeNode.setIsAccessible(true);
        newStorageTreeNode.setNodePath(finalNodePath);

        return newStorageTreeNode;
    }

    /**
     * <p>Rename a node (move around in the same destination)</p>
     * @param newStorageTreeNode an incomplete, unpublished node that is to be created
     * @param targetStorageTreeNode target node that is to be renamed
     * @return the completed node with a valid path to its physical part (unpublished)
     * @throws IOException if the node rename attempt fails for any I/O reason
     * @throws InvalidParameterException if any of the provided parameters are invalid
     * @throws FileAlreadyExistsException if a node under the same name already exists under this destination 
     * @throws Exception if anything not covered above was thrown
     */
    public StorageNode renameStorageTreeNode(StorageNode newStorageTreeNode, StorageNode targetStorageTreeNode) throws Exception {
        if (newStorageTreeNode == null || newStorageTreeNode.getId() != null || newStorageTreeNode.getNodePath() != null) {
            throw new InvalidParameterException("New node is invalid".formatted());
        }

        if (targetStorageTreeNode == null || targetStorageTreeNode.getId() == null || targetStorageTreeNode.getNodePath() == null) {
            throw new InvalidParameterException("Target node is invalid.".formatted());
        } else if (targetStorageTreeNode.getParent() == null) {
            throw new InvalidParameterException("Root node is not targetable.".formatted());
        }

        final NodePath finalNodePath = targetStorageTreeNode.getNodePath().resolveFromRoot(Files.move(targetStorageTreeNode.getNodePath().getPath(), targetStorageTreeNode.getNodePath().getPath().resolveSibling(newStorageTreeNode.getOnDiskName())));

        newStorageTreeNode.setParent(targetStorageTreeNode.getParent());
        newStorageTreeNode.setIsAccessible(true);
        newStorageTreeNode.setNodePath(finalNodePath);
        return newStorageTreeNode;
    }


    /**
     * @param targetStorageTreeNode target node that is to be deleted
     * @return the target node for the purpose of confirming deletion
     * @throws IOException if the node removal attempt fails for any I/O reason
     * @throws InvalidParameterException if any of the provided parameters are invalid
     * @throws DirectoryNotEmptyException if deletion fails due to the directory being occupied
     * @throws Exception if anything not covered above was thrown
     */
    public StorageNode deleteStorageTreeNode(StorageNode targetStorageTreeNode) throws Exception {
        if (targetStorageTreeNode == null) {
            throw new InvalidParameterException("Target node is invalid.".formatted());
        } else if (targetStorageTreeNode.getParent() == null) {
            throw new InvalidParameterException("Root node is not targetable.".formatted());
        }

        if (targetStorageTreeNode.getId() != null) {
            /* TODO: rethink this... should we actually delete it from the database? */
            storageRepository.delete(targetStorageTreeNode);
        }
        
        targetStorageTreeNode.getParent().getChildren().remove(targetStorageTreeNode);
        this.storageHashMap.remove(targetStorageTreeNode.getId());

        Files.deleteIfExists(targetStorageTreeNode.getNodePath().getPath());
        
        /* this is just a formality, GC will take care of this node by */
        targetStorageTreeNode.setIsAccessible(false); 
        targetStorageTreeNode.setIsConfirmed(false);
        targetStorageTreeNode.setNodePath(null);
        targetStorageTreeNode.setId(null);

        return targetStorageTreeNode;
    }

    /**
     * <p>Will query the quick access hash map and retrieve a matching node if it exists</p>
     * @param targetID UUID to query the hash map for
     * @return a matching node or a null in case of a no match
     */
    public StorageNode retrieveStorageTreeNode(UUID targetID) {
        return this.storageHashMap.get(targetID);
    }

    /**
     * @return the root node of the storage tree
     */
    public StorageNode retrievestorageRootNode() {
        return this.storageRootNode;
    }
}
