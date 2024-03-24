package com.unulearner.backend.storage;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.nio.file.Path;
import java.io.IOException;
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
import com.unulearner.backend.storage.repository.StorageHashMap;
import com.unulearner.backend.storage.statics.StoragePath;

import org.springframework.stereotype.Service;

@Service
public class StorageTree {
    private final StorageProperties storageProperties;
    private final StorageRepository storageRepository;
    private StorageHashMap storageTreeHashMap;
    private StorageTreeNode storageTreeRoot;
    private Path storageTreeDirectoryPath;

    public StorageTree(StorageProperties storageProperties, StorageRepository storageRepository) {
        this.storageProperties = storageProperties;
        this.storageRepository = storageRepository;

        try {
            buildStorageTree();
        } catch (Exception buildStorageTreeException) {
            /* Exception is re-thrown and left unhandled! App shutdown is THE expected behavior! */
            String errorMessage = "Failed to initialize storage tree!".formatted();
            throw new RuntimeException(errorMessage, buildStorageTreeException);
        }
    }
    
    public void buildStorageTree() throws Exception {
        String errorMessage = null;
        Path rootDirectoryPathTemp = null;
        StorageTreeNode temporaryStorageNode;

        final Path rootDirectoryPath;
        final StorageTreeNode rootStorageTreeNode;
        final StorageHashMap rootDirectoryThreeHashMap;
        final List<StorageTreeNode> rootDirectoryNodeStack;
        final String rootDirectoryPersistentDefaultURL = "";
        final StorageRepository storageRepository = this.storageRepository;
        final String rootDirectoryName = this.storageProperties.getRootDirectory();
        final String rootDirectoryDescription = this.storageProperties.getRootDirectoryDescription();
        final String recoveredFileStorageTreeNodeDescription = this.storageProperties.getRecoveredFileDescription();
        final String recoveredDirectoryStorageTreeNodeDescription = this.storageProperties.getRecoveredDirectoryDescription();
        
        try {
            rootDirectoryThreeHashMap = new StorageHashMap();
            rootDirectoryPathTemp = Paths.get(rootDirectoryName);
            rootDirectoryNodeStack = new ArrayList<StorageTreeNode>();

            /* Temporary variable for rootDirectoryPath is needed because all assignments here must be final */
            if (!StoragePath.isValidDirectory(rootDirectoryPathTemp, false)) {
                rootDirectoryPath = Files.createDirectories(rootDirectoryPathTemp);
            } else {
                rootDirectoryPath = rootDirectoryPathTemp;
            }

            /* Temporary variable for rootStorageTreeNode is needed because all assignments here must be final */
            final Optional<StorageTreeNode> nullableStorageTreeNode = storageRepository.findByOnDiskURL(rootDirectoryPersistentDefaultURL);
            if (nullableStorageTreeNode.isPresent()) {
                temporaryStorageNode = nullableStorageTreeNode.get();
            } else {
                temporaryStorageNode = new StorageTreeNode(rootDirectoryPath.getFileName().toString(), rootDirectoryDescription, null, new ArrayList<StorageTreeNode>(), rootDirectoryPersistentDefaultURL, rootDirectoryPath);
                temporaryStorageNode = storageRepository.save(temporaryStorageNode);
            }

            temporaryStorageNode.setIsAccessible(true);
            rootStorageTreeNode = temporaryStorageNode;

            rootDirectoryThreeHashMap.put(rootStorageTreeNode.getId(), rootStorageTreeNode.getOnDiskURL(), rootStorageTreeNode.getAbsolutePath(), rootStorageTreeNode);
        } catch (Exception StorageTreeInitializationError) {
            errorMessage = "Failed to initialize the '%s' directory storage tree!".formatted(rootDirectoryName);
            throw new StorageServiceException(errorMessage, StorageTreeInitializationError);
        }

        try {
            Files.walkFileTree(rootDirectoryPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) {
                    String exceptionMessage = null;
                    String targetStorageTreeNodeURL = null;
                    String targetStorageTreeNodeName = null;
                    StorageTreeNode targetStorageTreeNode = null;
                    Boolean targetStorageTreeNodeIsAccessible = null;
                    StorageTreeNode targetStorageTreeNodeParent = null;
                    Optional<StorageTreeNode> nullableStorageTreeNode = null;

                    try {
                        if (!StoragePath.isValidFile(filePath)) {
                            exceptionMessage = "File '%s' cannot be accessed!".formatted(filePath.getFileName().toString());
                            throw new StorageServiceException(exceptionMessage);
                        }

                        targetStorageTreeNodeURL = rootDirectoryPath.relativize(filePath).toString();
                        nullableStorageTreeNode = storageRepository.findByOnDiskURL(targetStorageTreeNodeURL);
                        if (nullableStorageTreeNode.isPresent()) {
                            targetStorageTreeNode = nullableStorageTreeNode.get();

                            if (!rootDirectoryNodeStack.isEmpty() && !rootDirectoryNodeStack.get(rootDirectoryNodeStack.size() - 1).getId().equals(targetStorageTreeNode.getParent().getId())) {
                                exceptionMessage = "File '%s' is a child of '%s' directory yet the relationship is not reciprocated on the persistent level!".formatted(targetStorageTreeNode.getOnDiskURL(), rootDirectoryNodeStack.get(rootDirectoryNodeStack.size() - 1));
                                throw new StorageServiceException(exceptionMessage);
                            }
                        } else {
                            targetStorageTreeNodeName = filePath.getFileName().toString();
                            targetStorageTreeNodeParent = !rootDirectoryNodeStack.isEmpty() ? rootDirectoryNodeStack.get(rootDirectoryNodeStack.size() - 1) : null;
                            targetStorageTreeNode = new StorageTreeNode(targetStorageTreeNodeName, recoveredFileStorageTreeNodeDescription, targetStorageTreeNodeParent, null, targetStorageTreeNodeURL, filePath);
                            targetStorageTreeNode = storageRepository.save(targetStorageTreeNode);
                        }

                        if (!rootDirectoryNodeStack.isEmpty()) {
                            for (StorageTreeNode iNode : rootDirectoryNodeStack.get(rootDirectoryNodeStack.size() - 1).getChildren()) {
                                if (iNode.getId().equals(targetStorageTreeNode.getId())) {
                                    targetStorageTreeNodeIsAccessible = true;
                                    break;
                                }
                            }

                            if (targetStorageTreeNodeIsAccessible == null || targetStorageTreeNodeIsAccessible == false) {
                                rootDirectoryNodeStack.get(rootDirectoryNodeStack.size() - 1).getChildren().add(targetStorageTreeNode);
                                targetStorageTreeNodeIsAccessible = true;
                            }

                            if (rootDirectoryThreeHashMap.put(targetStorageTreeNode.getId(), targetStorageTreeNode.getOnDiskURL(), targetStorageTreeNode.getAbsolutePath(), targetStorageTreeNode) != null) {
                                exceptionMessage = "File node '%s' is already present on the hashmap!".formatted(targetStorageTreeNodeURL);
                                throw new StorageServiceException(exceptionMessage);
                            }
                        }

                        targetStorageTreeNode.setIsAccessible(targetStorageTreeNodeIsAccessible);
                    } catch (Exception retrieveStorageTreeNodeException) {
                        exceptionMessage = "Failed to add file '%s' to the storage tree! Error: %s".formatted(targetStorageTreeNodeURL, retrieveStorageTreeNodeException.getMessage());
                        //TODO: LOG(exceptionMessage);
                        retrieveStorageTreeNodeException.printStackTrace();
                        return FileVisitResult.CONTINUE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path directoryPath, BasicFileAttributes attrs) {
                    String exceptionMessage = null;
                    String targetStorageTreeNodeURL = null;
                    String targetStorageTreeNodeName = null;
                    StorageTreeNode targetStorageTreeNode = null;
                    Boolean targetStorageTreeNodeIsAccessible = null;
                    StorageTreeNode targetStorageTreeNodeParent = null;
                    Optional<StorageTreeNode> nullableStorageTreeNode = null;

                    try {
                        if (directoryPath.equals(rootStorageTreeNode.getAbsolutePath())) {
                            rootDirectoryNodeStack.add(rootStorageTreeNode);
                            return FileVisitResult.CONTINUE;
                        }

                        if (!StoragePath.isValidDirectory(directoryPath)) {
                            exceptionMessage = "Directory '%s' cannot be accessed!".formatted(directoryPath.getFileName().toString());
                            throw new StorageServiceException(exceptionMessage);
                        }

                        targetStorageTreeNodeURL = rootDirectoryPath.relativize(directoryPath).toString();
                        nullableStorageTreeNode = storageRepository.findByOnDiskURL(targetStorageTreeNodeURL);
                        if (nullableStorageTreeNode.isPresent()) {
                            targetStorageTreeNode = nullableStorageTreeNode.get();
                            
                            if (!rootDirectoryNodeStack.isEmpty() && !rootDirectoryNodeStack.get(rootDirectoryNodeStack.size() - 1).getId().equals(targetStorageTreeNode.getParent().getId())) {
                                exceptionMessage = "Directory '%s' is a child of '%s' directory yet the relationship is not reciprocated on the persistent level!".formatted(targetStorageTreeNode.getOnDiskURL(), rootDirectoryNodeStack.get(rootDirectoryNodeStack.size() - 1));
                                throw new StorageServiceException(exceptionMessage);
                            }

                            /* This might pull some inaccessible nodes, that is why all of the children will be checked as this directory is crawled through! */
                            targetStorageTreeNode.setChildren(storageRepository.findAllByParent(targetStorageTreeNode));
                        } else {
                            targetStorageTreeNodeName = directoryPath.getFileName().toString();
                            targetStorageTreeNodeParent = !rootDirectoryNodeStack.isEmpty() ? rootDirectoryNodeStack.get(rootDirectoryNodeStack.size() - 1) : null;
                            targetStorageTreeNode = new StorageTreeNode(targetStorageTreeNodeName, recoveredDirectoryStorageTreeNodeDescription, targetStorageTreeNodeParent, new ArrayList<StorageTreeNode>(), targetStorageTreeNodeURL, directoryPath);
                            targetStorageTreeNode = storageRepository.save(targetStorageTreeNode);
                        }

                        if (!rootDirectoryNodeStack.isEmpty()) {
                            for (StorageTreeNode iNode : rootDirectoryNodeStack.get(rootDirectoryNodeStack.size() - 1).getChildren()) {
                                if (iNode.getId().equals(targetStorageTreeNode.getId())) {
                                    targetStorageTreeNodeIsAccessible = true;
                                    break;
                                }
                            }

                            if (targetStorageTreeNodeIsAccessible == null || targetStorageTreeNodeIsAccessible == false) {
                                rootDirectoryNodeStack.get(rootDirectoryNodeStack.size() - 1).getChildren().add(targetStorageTreeNode);
                                targetStorageTreeNodeIsAccessible = true;
                            }

                            if (rootDirectoryThreeHashMap.put(targetStorageTreeNode.getId(), targetStorageTreeNode.getOnDiskURL(), targetStorageTreeNode.getAbsolutePath(), targetStorageTreeNode) != null) {
                                exceptionMessage = "Directory node '%s' is already present on the hashmap!".formatted(targetStorageTreeNodeURL);
                                throw new StorageServiceException(exceptionMessage);
                            }
                        }

                        targetStorageTreeNode.setIsAccessible(targetStorageTreeNodeIsAccessible);
                        rootDirectoryNodeStack.add(targetStorageTreeNode);
                    } catch (Exception retrieveStorageTreeNodeException) {
                        exceptionMessage = "Failed to add directory '%s' to the storage tree! Error: %s".formatted(targetStorageTreeNodeURL, retrieveStorageTreeNodeException.getMessage());
                        //TODO: LOG(exceptionMessage);
                        retrieveStorageTreeNodeException.printStackTrace();
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directoryPath, IOException exc) {
                    String exceptionMessage = null;

                    try {
                        if (!rootDirectoryNodeStack.isEmpty()) {
                            final Comparator<StorageTreeNode> storageTreeNodeComparator = Comparator.comparing((StorageTreeNode iNode) -> iNode.getChildren() != null ? 0 : 1).thenComparing(StorageTreeNode::getOnDiskName);
                            Collections.sort(rootDirectoryNodeStack.get(rootDirectoryNodeStack.size() - 1).getChildren(), storageTreeNodeComparator);
                        }

                        if (rootDirectoryNodeStack.size() - 1 > 0 && directoryPath.equals(rootDirectoryNodeStack.get(rootDirectoryNodeStack.size() - 1).getAbsolutePath())) {
                            rootDirectoryNodeStack.remove(rootDirectoryNodeStack.size() - 1);
                        }
                    } catch (Exception retrieveStorageTreeNodeException) {
                        exceptionMessage = "Failed to close directory '%s'! Error: %s".formatted(rootDirectoryPath.relativize(directoryPath).toString(), retrieveStorageTreeNodeException.getMessage());
                        //TODO: LOG(exceptionMessage);
                        retrieveStorageTreeNodeException.printStackTrace();
                        return FileVisitResult.CONTINUE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path filePath, IOException exc) {
                    //TODO: LOG(exc);
                    return FileVisitResult.CONTINUE;
                }
            });

            rootStorageTreeNode.setChildren(rootDirectoryNodeStack.get(0).getChildren());
        } catch (Exception buildStorageTreeNodeException) {
            errorMessage = "Failed to build the '%s' directory storage tree!".formatted(rootDirectoryName);
            throw new StorageServiceException(errorMessage, buildStorageTreeNodeException);
        }

        this.storageTreeRoot = rootStorageTreeNode;
        this.storageTreeDirectoryPath = rootDirectoryPath;
        this.storageTreeHashMap = rootDirectoryThreeHashMap;
    }

    /**
     * <p>Create a new node that will be commited to the permanent database <p>
     * @param targetStorageTreeNode node to be committed
     * @param destinationStorageTreeNode node to be committed to
     * @param persistOriginalFile move if false/copy if right - it's that simple
     * @param onConflict what should be done in case of a conflict?
     * <ul>
     * <li><b>overwrite</b> - overwrites the conflicting file with the original (only applicable to files)</li>
     * <li><b>merge</b> - merges the original directory with the conflicting one (only applicable to directories)</li>
     * <li><b>rename</b> - appends <b>(N)</b> to the original name (<b>N</b> being the number of the copy)</li>
     * <li><b>ignore</b> - pretty self-explanatory as far as these kind of things go...</li>
     * </ul>
     * @return a non-null value of <b>StorageTreeNode</b> that has been commited to the <b>database</b>, complete with child nodes (in case of a directory)
     * @throws FileAlreadyExistsException in case of an unresolved (ignored) file/directory conflict
     * @throws StorageServiceException if arguments passed to the method are not valid
     * @throws Exception to raise exceptions thrown by method calls from within
     */
    public StorageTreeNode commitStorageTreeNode(StorageTreeNode targetStorageTreeNode, StorageTreeNode destinationStorageTreeNode, Boolean persistOriginalFile, String onConflict) throws Exception {
        String errorMessage = null;

        Path finalStorageTreeNodePath = null;
        StorageTreeNode conflictingStorageTreeNode = null;

        final Path currentPath = targetStorageTreeNode.getAbsolutePath();
        final String currentName = targetStorageTreeNode.getOnDiskName();
        //final String currentURL = targetStorageTreeNode.getOnDiskURL();

        final Path destinationPath = destinationStorageTreeNode.getAbsolutePath();
        final String destinationName = destinationStorageTreeNode.getOnDiskName();
        //final String destinationURL = destinationStorageTreeNode.getOnDiskURL();

        final Path targetPath = destinationPath.resolve(currentName);

        if (!Files.exists(targetPath)) {
            finalStorageTreeNodePath = persistOriginalFile ? Files.copy(currentPath, targetPath) : Files.move(currentPath, targetPath);
        } else {
            conflictingStorageTreeNode = retrieveStorageTreeNode(targetPath);
        }

        if (conflictingStorageTreeNode != null) {
            if (conflictingStorageTreeNode.getId().equals(targetStorageTreeNode.getId())) {
                //TODO: rename!
            }

            switch (onConflict) {
                case "overwrite":
                    if (Files.isDirectory(currentPath)) {
                        errorMessage = "Target '%s' is a directory, making the 'overwrite' flag not applicable!".formatted(currentName);
                        throw new StorageServiceException(errorMessage);
                    }

                    if (Files.isDirectory(targetPath)) {
                        errorMessage = "Conflicting '%s' is a directory, making the 'overwrite' flag not applicable!".formatted(currentName);
                        throw new StorageServiceException(errorMessage);
                    }

                    finalStorageTreeNodePath = persistOriginalFile ? Files.copy(currentPath, targetPath, StandardCopyOption.REPLACE_EXISTING) : Files.move(currentPath, targetPath, StandardCopyOption.REPLACE_EXISTING);

                    break;
                case "merge":
                    if (!Files.isDirectory(currentPath)) {
                        errorMessage = "Target '%s' is not a directory, making the 'merge' flag not applicable!".formatted(currentName);
                        throw new StorageServiceException(errorMessage);
                    }

                    if (!Files.isDirectory(targetPath)) {
                        errorMessage = "Conflicting '%s' is not a directory, making the 'merge' flag not applicable!".formatted(currentName);
                        throw new StorageServiceException(errorMessage);
                    }

                    return conflictingStorageTreeNode;
                case "rename":
                    Pattern pattern = Pattern.compile("^(\\.)?(.+?)( \\(\\d+\\))?(\\.\\w+)?$");
                    Matcher matcher = pattern.matcher(currentName);

                    String baseDot = null;
                    String baseName = null;
                    String copyNumber = null;
                    String fileExtension = null;
                    Integer baseNameModifier = 1;
                    String temporaryName = null;
                    Path temporaryPath = null;

                    if (matcher.matches()) {
                        baseDot = matcher.group(1);
                        baseName = matcher.group(2);
                        copyNumber = matcher.group(3);
                        fileExtension = matcher.group(4);

                        if (baseDot != null && !baseDot.isEmpty()) {
                            // Combine the dot from the dotfiles with the basename
                            baseName = baseDot + baseName;
                        }

                        if (copyNumber != null && !copyNumber.isEmpty()) {
                            // Remove parentheses from copyNumber and parse it to Integer
                            baseNameModifier = Integer.parseInt(copyNumber.substring(2, copyNumber.length() - 1));
                        }

                        if (fileExtension != null && !fileExtension.isEmpty()) {
                            // Remove the dot preceding the file extension
                            fileExtension = fileExtension.substring(1);
                        }
                    } else {
                        errorMessage = "Name '%s' does not match the regex pattern!".formatted(currentName);
                        throw new StorageServiceException(errorMessage);
                    }

                    while (baseNameModifier <= Integer.MAX_VALUE) {
                        if (fileExtension == null || fileExtension.isEmpty()) {
                            temporaryName = "%s (%s)".formatted(baseName, baseNameModifier);
                        } else {
                            temporaryName = "%s (%s).%s".formatted(baseName, baseNameModifier, fileExtension);
                        }

                        temporaryPath = destinationPath.resolve(temporaryName);
                        if (!Files.exists(temporaryPath)) {
                            finalStorageTreeNodePath = persistOriginalFile ? Files.copy(currentPath, temporaryPath) : Files.move(currentPath, temporaryPath);
                        } else {
                            conflictingStorageTreeNode = retrieveStorageTreeNode(temporaryPath);
                            baseNameModifier++;
                            continue;
                        }

                        break;
                    }

                    if (finalStorageTreeNodePath == null) {
                        errorMessage = "Do you seriously intend to tell me that '%s' and its other %d iterations already exist on disk? In '%s' directory? All there? Together?".formatted(currentName, Integer.MAX_VALUE, destinationName);
                        throw new StorageServiceException(errorMessage);
                    }

                    break;
                case "ignore":
                default:
                    errorMessage = "%s '%s' already exists in the '%s' directory!".formatted(Files.isDirectory(targetPath) ? "Directory" : "File", currentName, destinationName);
                    throw new FileAlreadyExistsException(errorMessage);
            }
        }

        if (finalStorageTreeNodePath != null) {
            Integer commitedStorageTreeNodeInsertionIndex = 0;

            final String finalStorageTreeNodeName = finalStorageTreeNodePath.getFileName().toString();
            final String finalStorageTreeNodeURL = this.storageTreeDirectoryPath.relativize(finalStorageTreeNodePath).toString();
            final List<StorageTreeNode> finalStorageTreeNodeChildren = Files.isDirectory(finalStorageTreeNodePath) ? new ArrayList<StorageTreeNode>() : null;
            final StorageTreeNode finalStorageTreeNode = new StorageTreeNode(finalStorageTreeNodeName, targetStorageTreeNode.getDescription(), destinationStorageTreeNode, finalStorageTreeNodeChildren, finalStorageTreeNodeURL, finalStorageTreeNodePath);
            final StorageTreeNode commitedStorageTreeNode = this.storageRepository.save(finalStorageTreeNode);

            final Comparator<StorageTreeNode> storageNodeComparator = Comparator.comparing((StorageTreeNode node) -> node.getChildren() == null).thenComparing(StorageTreeNode::getOnDiskURL);
            while (commitedStorageTreeNodeInsertionIndex < destinationStorageTreeNode.getChildren().size() && storageNodeComparator.compare(commitedStorageTreeNode, destinationStorageTreeNode.getChildren().get(commitedStorageTreeNodeInsertionIndex)) > 0) {
                commitedStorageTreeNodeInsertionIndex++;
            }

            destinationStorageTreeNode.getChildren().add(commitedStorageTreeNodeInsertionIndex, commitedStorageTreeNode);
            this.storageTreeHashMap.put(commitedStorageTreeNode.getId(), commitedStorageTreeNode.getOnDiskURL(), commitedStorageTreeNode.getAbsolutePath(), commitedStorageTreeNode);
            //TODO: appropriate checks!!!
            return commitedStorageTreeNode;
        } else {
            errorMessage = "Failed to commit the %s '%s' to storage!".formatted(Files.isDirectory(targetPath) ? "directory" : "file", currentName);
            throw new StorageServiceException(errorMessage);
        }
    }

    /**
     * @return root <b>StorageTreeNode</b> of the current tree instance
     * @throws Exception to raise exceptions thrown by method calls from within
     */
    public StorageTreeNode retrieveStorageTreeRoot() {
        return this.storageTreeRoot;
    }

    /**
     * <p>Will query the storage tree node hash map</p>
     * @param targetID UUID to query (returns the root node if the paramater is null)
     * @return a nullable (if nothing was found) value of <b>StorageTreeNode</b>
     * @throws Exception to raise exceptions thrown by method calls from within
     */
    public StorageTreeNode retrieveStorageTreeNode(UUID targetID) throws Exception {
        return this.storageTreeHashMap.get(targetID);
    }

    /**
     * <p>Will query the storage tree node hash map</p>
     * @param targetURL URL to query (returns the root node if the paramater is null)
     * @return a nullable (if nothing was found) value of <b>StorageTreeNode</b>
     * @throws Exception to raise exceptions thrown by method calls from within
     */
    public StorageTreeNode retrieveStorageTreeNode(String targetURL) throws Exception {
        return this.storageTreeHashMap.get(targetURL);
    }

    /**
     * <p>Will query the storage tree node hash map</p>
     * @param targetPath path to query (returns the root node if the paramater is null)
     * @return a nullable (if nothing was found) value of <b>StorageTreeNode</b>
     * @throws Exception to raise exceptions thrown by method calls from within
     */
    public StorageTreeNode retrieveStorageTreeNode(Path targetPath) throws Exception {
        return this.storageTreeHashMap.get(targetPath);
    }

    public Boolean removeStorageTreeNode(StorageTreeNode targetStorageTreeNode) throws Exception {

   //     if (targetStorageTreeNode.getOnDiskURL().isEmpty()) {
    //        throw new StorageServiceException("fuck off!");
  //      }

        return true;
    }
}
