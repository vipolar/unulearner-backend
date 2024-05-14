package com.unulearner.backend.storage;

import java.util.List;
import java.util.UUID;
import java.util.Deque;
import java.util.HashMap;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ArrayDeque;
import java.util.Collections;

import java.io.IOException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.attribute.BasicFileAttributes;

import com.unulearner.backend.storage.exceptions.StorageServiceException;
import com.unulearner.backend.storage.properties.StorageProperties;
import com.unulearner.backend.storage.repository.StorageRepository;
import com.unulearner.backend.storage.statics.StoragePath;

import org.springframework.stereotype.Service;

@Service
public class StorageTree {
    private final HashMap<UUID, StorageTreeNode> storageHashMap;
    private final StorageProperties storageTreeProperties;
    private final StorageRepository storageRepository;
    private final StorageTreeNode storageRootNode;
    private final Path storageRootDirectoryPath;

    public StorageTree(StorageProperties storageTreeProperties, StorageRepository storageRepository) {
        this.storageHashMap = new HashMap<UUID, StorageTreeNode>();
        this.storageTreeProperties = storageTreeProperties;
        this.storageRepository = storageRepository;

        try {
            final String rootDirectoryPath = this.storageTreeProperties.getRootDirectory();
            final String rootDirectoryDescription = this.storageTreeProperties.getRootDirectoryDescription();

            this.storageRootDirectoryPath = (Files.createDirectories(Paths.get(rootDirectoryPath))).toAbsolutePath();
            if (!StoragePath.isValidDirectory(this.storageRootDirectoryPath, false)) {
                throw new StorageServiceException("Root (%s) directory could not be created!".formatted(rootDirectoryPath));
            }

            /* Empty URL is the best descriptor for the root node as URL is a unique (strictly enforced) property */
            final Optional<StorageTreeNode> nullableStorageTreeNode = this.storageRepository.findByOnDiskURL("");
            if (nullableStorageTreeNode.isPresent()) {
                this.storageRootNode = nullableStorageTreeNode.get();
                this.storageRootNode.setRelativePath(this.storageRootDirectoryPath);
                this.storageRootNode.setChildren(this.storageRepository.findAllByParent(this.storageRootNode));
            } else {
                this.storageRootNode = this.storageRepository.save(new StorageTreeNode(null, new ArrayList<StorageTreeNode>(), this.storageRootDirectoryPath, rootDirectoryDescription));
            }

            this.storageHashMap.put(this.storageRootNode.getId(), this.storageRootNode);
            this.storageRootNode.setIsAccessible(true);
        } catch (Exception exception) {
            throw new RuntimeException(exception.getMessage());
        }

        try {
            final StorageTreeNode rootStorageTreeNode = this.storageRootNode;
            final Path rootDirectoryPath = this.storageRootNode.getRelativePath();
            final HashMap<UUID, StorageTreeNode> storageHashMap = this.storageHashMap;
            final Deque<StorageTreeNode> rootDirectoryNodeDeque = new ArrayDeque<StorageTreeNode>();
            final String recoveredFileStorageTreeNodeDescription = this.storageTreeProperties.getRecoveredFileDescription();
            final String recoveredDirectoryStorageTreeNodeDescription = this.storageTreeProperties.getRecoveredDirectoryDescription();

            Files.walkFileTree(rootDirectoryPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) {
                    String exceptionMessage = null;

                    try {
                        if (!StoragePath.isValidFile(filePath)) {
                            exceptionMessage = "File '%s' cannot be accessed!".formatted(filePath.getFileName().toString());
                            throw new StorageServiceException(exceptionMessage);
                        }

                        final StorageTreeNode targetStorageTreeNode;
                        final String targetStorageTreeNodeURL = rootDirectoryPath.relativize(filePath).toString();
                        final Optional<StorageTreeNode> nullableStorageTreeNode = storageRepository.findByOnDiskURL(targetStorageTreeNodeURL);
                        if (nullableStorageTreeNode.isPresent()) {
                            targetStorageTreeNode = nullableStorageTreeNode.get();
                            targetStorageTreeNode.setRelativePath(filePath.toAbsolutePath());

                            if (!rootDirectoryNodeDeque.peekLast().getId().equals(targetStorageTreeNode.getParent().getId())) {
                                exceptionMessage = "File '%s' is a child of '%s' directory yet the relationship is not reciprocated on the persistent level!".formatted(targetStorageTreeNode.getOnDiskURL(), rootDirectoryNodeDeque.peekLast().getOnDiskURL());
                                throw new StorageServiceException(exceptionMessage);
                            }
                        } else {
                            targetStorageTreeNode = storageRepository.save(new StorageTreeNode(rootDirectoryNodeDeque.peekLast(), null, rootDirectoryPath.relativize(filePath), recoveredFileStorageTreeNodeDescription));
                        }

                        /* Here we match the possibly new node with the node pulled from the db if such a match can be found */
                        final List<StorageTreeNode> parentStorageTreeNodesChildren = rootDirectoryNodeDeque.peekLast().getChildren();
                        for (int iNode = 0; iNode < parentStorageTreeNodesChildren.size(); iNode++) {
                            if (targetStorageTreeNode.getId().equals(parentStorageTreeNodesChildren.get(iNode).getId())) {
                                parentStorageTreeNodesChildren.set(iNode, targetStorageTreeNode);
                                targetStorageTreeNode.setIsAccessible(true);
                            }
                        }

                        /* In case of no match we just append it to the children (will be sorted later) */
                        if (targetStorageTreeNode.getIsAccessible() == false) {
                            parentStorageTreeNodesChildren.add(targetStorageTreeNode);
                            targetStorageTreeNode.setIsAccessible(true);
                        }

                        if (storageHashMap.put(targetStorageTreeNode.getId(), targetStorageTreeNode) != null) {
                            exceptionMessage = "File node '%s' is already present on the hashmap!".formatted(targetStorageTreeNodeURL);
                            throw new StorageServiceException(exceptionMessage);
                        }
                    } catch (Exception exception) {
                        exceptionMessage = "Failed to add file '%s' to the storage tree! Error: %s".formatted(filePath.toString(), exception.getMessage());
                        //TODO: LOG(exceptionMessage);
                        exception.printStackTrace();
                        return FileVisitResult.CONTINUE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path directoryPath, BasicFileAttributes attrs) {
                    String exceptionMessage = null;

                    try {
                        if (!StoragePath.isValidDirectory(directoryPath)) {
                            exceptionMessage = "Directory '%s' cannot be accessed!".formatted(directoryPath.getFileName().toString());
                            throw new StorageServiceException(exceptionMessage);
                        }

                        if (rootDirectoryNodeDeque.peekLast() == null) {
                            if (rootDirectoryPath.equals(directoryPath)) {
                                rootDirectoryNodeDeque.offer(rootStorageTreeNode);
                                return FileVisitResult.CONTINUE;
                            }

                            exceptionMessage = "Directory '%s' is trying to pass as a root but is not root!".formatted(directoryPath);
                            throw new StorageServiceException(exceptionMessage);
                        }

                        final StorageTreeNode targetStorageTreeNode;
                        final String targetStorageTreeNodeURL = rootDirectoryPath.relativize(directoryPath).toString();
                        final Optional<StorageTreeNode> nullableStorageTreeNode = storageRepository.findByOnDiskURL(targetStorageTreeNodeURL);
                        if (nullableStorageTreeNode.isPresent()) {
                            targetStorageTreeNode = nullableStorageTreeNode.get();
                            targetStorageTreeNode.setRelativePath(directoryPath.toAbsolutePath());

                            if (!rootDirectoryNodeDeque.peekLast().getId().equals(targetStorageTreeNode.getParent().getId())) {
                                exceptionMessage = "Directory '%s' is a child of '%s' directory yet the relationship is not reciprocated on the persistent level!".formatted(targetStorageTreeNode.getOnDiskURL(), rootDirectoryNodeDeque.peekLast().getOnDiskURL());
                                throw new StorageServiceException(exceptionMessage);
                            }

                            /* This might pull some inaccessible nodes, that is why all of the children will be checked as this directory is crawled through! */
                            targetStorageTreeNode.setChildren(storageRepository.findAllByParent(targetStorageTreeNode));
                        } else {
                            targetStorageTreeNode = storageRepository.save(new StorageTreeNode(rootDirectoryNodeDeque.peekLast(), new ArrayList<StorageTreeNode>(), rootDirectoryPath.relativize(directoryPath), recoveredDirectoryStorageTreeNodeDescription));
                        }

                        /* Here we match the possibly new node with the node pulled from the db if such a match can be found */
                        final List<StorageTreeNode> parentStorageTreeNodesChildren = rootDirectoryNodeDeque.peekLast().getChildren();
                        for (int iNode = 0; iNode < parentStorageTreeNodesChildren.size(); iNode++) {
                            if (targetStorageTreeNode.getId().equals(parentStorageTreeNodesChildren.get(iNode).getId())) {
                                parentStorageTreeNodesChildren.set(iNode, targetStorageTreeNode);
                                targetStorageTreeNode.setIsAccessible(true);
                            }
                        }

                        /* In case of no match we just append it to the children (will be sorted later) */
                        if (targetStorageTreeNode.getIsAccessible() == false) {
                            parentStorageTreeNodesChildren.add(targetStorageTreeNode);
                            targetStorageTreeNode.setIsAccessible(true);
                        }

                        if (storageHashMap.put(targetStorageTreeNode.getId(), targetStorageTreeNode) != null) {
                            exceptionMessage = "Directory node '%s' is already present on the hashmap!".formatted(targetStorageTreeNodeURL);
                            throw new StorageServiceException(exceptionMessage);
                        }

                        rootDirectoryNodeDeque.offer(targetStorageTreeNode);
                    } catch (Exception exception) {
                        exceptionMessage = "Failed to add directory '%s' to the storage tree! Error: %s".formatted(directoryPath.toString(), exception.getMessage());
                        //TODO: LOG(exceptionMessage);
                        exception.printStackTrace();
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directoryPath, IOException preExistingException) {
                    String exceptionMessage = null;

                    if (preExistingException != null) {
                        exceptionMessage = "Directory '%s' threw an I/O Exception: %s".formatted(directoryPath, preExistingException.getMessage());
                        System.out.println(exceptionMessage);
                        preExistingException.printStackTrace();
                    }

                    try {
                        final Comparator<StorageTreeNode> storageTreeNodeComparator = Comparator.comparing((StorageTreeNode iNode) -> iNode.getChildren() != null ? 0 : 1).thenComparing(StorageTreeNode::getOnDiskName);
                        Collections.sort(rootDirectoryNodeDeque.peekLast().getChildren(), storageTreeNodeComparator);
                        rootDirectoryNodeDeque.removeLast();
                    } catch (Exception exception) {
                        exceptionMessage = "Failed to close directory '%s'! Error: %s".formatted(rootDirectoryPath.relativize(directoryPath).toString(), exception.getMessage());
                        //TODO: LOG(exceptionMessage);
                        exception.printStackTrace();
                        return FileVisitResult.CONTINUE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path filePath, IOException preExistingException) {
                    String exceptionMessage = null;

                    if (preExistingException != null) {
                        exceptionMessage = "File '%s' threw an I/O Exception: %s".formatted(filePath, preExistingException.getMessage());
                        System.out.println(exceptionMessage);
                        preExistingException.printStackTrace();
                    }

                    return FileVisitResult.CONTINUE;
                }
            });

            if (rootDirectoryNodeDeque.size() > 0) {
                throw new StorageServiceException("%d unhandled directories left in the deque!".formatted(rootDirectoryNodeDeque.size()));
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception.getMessage());
        }
    }

    public StorageTreeNode recoverStorageTreeNode(String targetNodeName, StorageTreeNode parentStorageTreeNode) throws Exception {
        final Path targetPath = parentStorageTreeNode.getRelativePath().resolve(targetNodeName);

        final StorageTreeNode newStorageTreeNode;
        if (StoragePath.isValidDirectory(targetPath)) {
            newStorageTreeNode = new StorageTreeNode(parentStorageTreeNode, new ArrayList<StorageTreeNode>(), targetPath, this.storageTreeProperties.getRecoveredDirectoryDescription());
        } else if (StoragePath.isValidFile(targetPath)) {
            newStorageTreeNode = new StorageTreeNode(parentStorageTreeNode, null, targetPath, this.storageTreeProperties.getRecoveredFileDescription());
        } else if (StoragePath.isValidNode(targetPath)) {
            throw new StorageServiceException("Provided path is not accessible!");
        } else {
            throw new StorageServiceException("Provided path leads to nothing!!!");
        }

        return newStorageTreeNode;
    }

    public StorageTreeNode publishStorageTreeNode(StorageTreeNode targetStorageTreeNode) throws Exception {
        /* we save to repository only here because the recovery of the node does not necessarily guarantee that we also want to keep it */
        final StorageTreeNode savedStorageTreeNode = storageRepository.save(targetStorageTreeNode);
        final StorageTreeNode parentStorageTreeNode = savedStorageTreeNode.getParent();
        
        final Comparator<StorageTreeNode> storageTreeNodeComparator = Comparator.comparing((StorageTreeNode iNode) -> iNode.getChildren() != null ? 0 : 1).thenComparing(StorageTreeNode::getOnDiskName);
        final Integer iNode = Collections.binarySearch(parentStorageTreeNode.getChildren(), savedStorageTreeNode, storageTreeNodeComparator);
        
        if (iNode > 0) { //TODO: this whole thing will have to be debugged thoroully!
            throw new StorageServiceException("The node is already there??? This is supposed to be impossible!!!");
        }

        /* iNode being negative means that we have the index the item should be at but is not, so we reverse it and to get the appropriate insertion index */
        parentStorageTreeNode.getChildren().add(iNode > 0 ? -(iNode + 1) : iNode, savedStorageTreeNode);
        this.storageHashMap.put(savedStorageTreeNode.getId(), savedStorageTreeNode);
        targetStorageTreeNode.setIsAccessible(true);

        return savedStorageTreeNode;
    }

    public StorageTreeNode updateStorageTreeNode(StorageTreeNode targetStorageTreeNode) throws Exception {
        if (targetStorageTreeNode.getId() == null) {
            throw new StorageServiceException("The node is not committed!");
        }

        return this.storageRepository.save(targetStorageTreeNode);
    }

    public void unPublishStorageTreeNode(StorageTreeNode targetStorageTreeNode) throws Exception {
        final StorageTreeNode parentStorageTreeNode = targetStorageTreeNode.getParent();
        
        storageRepository.delete(targetStorageTreeNode);
        this.storageHashMap.remove(targetStorageTreeNode.getId());
        parentStorageTreeNode.getChildren().remove(targetStorageTreeNode);

        /* this is just a formality, GC will take care of this node by itself */
        targetStorageTreeNode.setIsAccessible(false); 
    }

    /**
     * @return root <b>StorageTreeNode</b> of the current tree instance
     * @throws Exception to raise exceptions thrown by method calls from within
     */
    public StorageTreeNode retrievestorageRootNode() {
        return this.storageRootNode;
    }

    /**
     * <p>Will query the storage tree node hash map</p>
     * @param targetID UUID to query (returns the root node if the paramater is null)
     * @return a nullable (if nothing was found) value of <b>StorageTreeNode</b>
     * @throws Exception to raise exceptions thrown by method calls from within
     */
    public StorageTreeNode retrieveStorageTreeNode(UUID targetID) throws Exception {
        return this.storageHashMap.get(targetID);
    }

    /**
     * @param targetName target name of the file/directory that is to be transfered (if null, will be extracted from the path of the file/directory)
     * @param currentPath current path of the file/directory that is to be transfered
     * @param destinationPath destination of the file/directory to be transfered
     * @param removeOriginal should the file/directory be moved or copied
     * @param replaceExisting should the incoming file/directory replace the existing
     * @return path to the transfered file/directory
     * @throws Exception if the move/copy method fails for any reason other than the {@code FileAlreadyExistsException}
     * @throws FileAlreadyExistsException if file/directory under the same name already exists in the destination directory
     */
    public Path transferNode(String targetName, Path currentPath, Path destinationPath, Boolean removeOriginal, Boolean replaceExisting) throws Exception {
        final String targetNodeName = targetName == null ? currentPath.getFileName().toString() : targetName;
        final Boolean removeOriginalNode = removeOriginal == null ? false : removeOriginal;
        final Path targetPath = destinationPath.resolve(targetNodeName);

        if (currentPath == null || (!StoragePath.isValidDirectory(currentPath) && !StoragePath.isValidFile(currentPath))) {
            final String errorMessage = "Supplied path for the node to be transfered is invalid".formatted();
            throw new StorageServiceException(errorMessage);
        }

        if (StoragePath.isValidNode(targetPath) && (replaceExisting == null || replaceExisting == false)) {
            final String errorMessage = "%s under the name '%s' already exists in the '%s' directory!".formatted(Files.isDirectory(targetPath) ? "Directory" : "File", targetNodeName, destinationPath.getFileName().toString());
            throw new FileAlreadyExistsException(errorMessage);
        }

        if (destinationPath == null || !StoragePath.isValidDirectory(destinationPath)) {
            final String errorMessage = "Supplied path for the destination directory is invalid".formatted();
            throw new StorageServiceException(errorMessage);
        }

        Path transferedNodePath = null;
        if (removeOriginalNode && !Files.isDirectory(currentPath)) {
            if (replaceExisting != null && replaceExisting == true) {
                transferedNodePath = Files.move(currentPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                transferedNodePath = Files.move(currentPath, targetPath);
            }
        } else {
            transferedNodePath = Files.copy(currentPath, targetPath);
        }

        if (transferedNodePath == null) {
            throw new StorageServiceException(targetNodeName);
        }

        return this.storageRootDirectoryPath.relativize(transferedNodePath);
    }
}
