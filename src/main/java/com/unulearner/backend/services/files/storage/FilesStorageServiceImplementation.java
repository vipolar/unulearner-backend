package com.unulearner.backend.services.files.storage;

import java.util.List;
import java.util.Iterator;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Stream;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import jakarta.annotation.PostConstruct;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.core.io.UrlResource;
import org.springframework.util.FileSystemUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;

import com.unulearner.backend.configuration.properties.StorageProperties;

@Service
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class FilesStorageServiceImplementation implements FilesStorageService {

    private FilesStorageNodeRepository filesStorageNodeRepository;
    private StorageProperties storageProperties;
    private FilesStorageNode rootNode;
    private String rootPathString;
    private Path rootPath;
    private Long rootId;

    public FilesStorageServiceImplementation(FilesStorageNodeRepository filesStorageNodeRepository, StorageProperties storageProperties) {
        this.filesStorageNodeRepository = filesStorageNodeRepository;
        this.storageProperties = storageProperties;
    }

    @PostConstruct
    public void initializeStorage() throws Exception {
        try {
            this.rootPathString = storageProperties.getRootDirectory();
            this.rootPath = Paths.get(rootPathString);  

            if (Files.exists(this.rootPath)) {
                this.rootNode = this.filesStorageNodeRepository.getByParentIsNull();

                if (this.rootNode != null) {
                    this.rootId = rootNode.getId();
                    return; // Everything is fine and dandy!
                }
            } else {
                Files.createDirectory(this.rootPath);
            }

            FilesStorageNode rootDirectory = new FilesStorageNode();

            rootDirectory.setUrl("");
            rootDirectory.setName("/");
            rootDirectory.setParent(null);
            rootDirectory.setIsDirectory(true);
            rootDirectory.setDescription(this.storageProperties.getRootDirectoryDescription());
            
            this.rootNode = this.filesStorageNodeRepository.save(rootDirectory);
            this.rootId = this.rootNode.getId();
        } catch (Exception e) {
            // Storage initialization failure is a fatal error and should cause a shutdown!
            throw new RuntimeException("(initializeStorage) Exception occurred: Could not initialize storage!", e);
        }
    }

    /* THIS IS A HELPER FUNCTION CALLED ONLY BY *** saveDirectory & saveFile *** METHODS */
    private String nameValidator(String name) throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("(nameValidator) Exception occurred: Directory/file name cannot be null or empty!");
        }

        if (!name.matches("^[a-zA-Z0-9_-][a-zA-Z0-9_. -]*$")) {
            throw new IllegalArgumentException("(nameValidator) Exception occurred: Invalid characters found in the directory/file name!");
        }

        if (name.length() > this.storageProperties.getMaxNameLength()) {
            throw new IllegalArgumentException("(nameValidator) Exception occurred: Directory/file name exceeds the maximum allowed length!");
        }

        return name.replace(" ", "_");
    }

    public FilesStorageNode saveFile(MultipartFile file, Long parentId, String description) throws Exception {
        try {
            String validFileName = nameValidator(file.getOriginalFilename());
            Path validFilePath = this.rootPath.resolve(validFileName);
            FilesStorageNode parentNode = this.rootNode;
            Path parentPath = this.rootPath;

            if (parentId == null) {
                throw new RuntimeException("(saveFile) Exception occurred: Parent node ID cannot be null!");
            }

            Optional<FilesStorageNode> optionalFileNode = this.filesStorageNodeRepository.findById(parentId);

            if (!optionalFileNode.isPresent()) {
                throw new RuntimeException("(saveFile) Exception occurred: Parent node does not exist: " + parentId);
            }

            parentNode = optionalFileNode.get();

            if (!parentNode.getIsDirectory()) {
                throw new RuntimeException("(saveFile) Exception occurred: Parent node is not a directory: " + parentId);
            }

            if (parentNode.getId() != this.rootId) {
                parentPath = this.rootPath.resolve(parentNode.getUrl());
                validFilePath = parentPath.resolve(validFileName);
            }
            

            if (parentPath == null || !Files.exists(parentPath)) {
                parentNode.setPhysical(false);
                this.filesStorageNodeRepository.save(parentNode);
                throw new RuntimeException("(saveFile) Exception occurred: Requirements for file creation were not met!");
            }

            if (Files.exists(validFilePath)) {
                throw new RuntimeException("(saveFile) Exception occurred: A file with the same name already exists in the specified directory: " + validFilePath);
            }

            Files.copy(file.getInputStream(), validFilePath);

            FilesStorageNode newFile = new FilesStorageNode();

            newFile.setParent(parentNode);
            newFile.setDescription(description);
            newFile.setIsDirectory(false);
            newFile.setName(validFilePath.getFileName().toString());
            newFile.setUrl(this.rootPath.relativize(validFilePath).toString());

            return this.filesStorageNodeRepository.save(newFile);
        } catch (Exception e) {
            throw new RuntimeException("(saveFile) Exception occurred: Could not commit file to disk or database!", e);
        }
    }

    public Resource getFile(Long fileId) throws Exception {
        try {
            if (fileId == null) {
                throw new RuntimeException("(getFile) Exception occurred: File ID cannot be null!");
            }

            Optional<FilesStorageNode> optionalFileNode = this.filesStorageNodeRepository.findById(fileId);

            if (!optionalFileNode.isPresent()) {
                throw new RuntimeException("(getFile) Exception occurred: Target node does not exist: " + fileId);
            }

            FilesStorageNode targetNode = optionalFileNode.get();

            if (targetNode.getIsDirectory()) {
                throw new RuntimeException("(getFile) Exception occurred: Target node is a directory: " + fileId);
            }

            Path validFilePath = this.rootPath.resolve(targetNode.getUrl());
            if (!Files.exists(validFilePath)) {
                targetNode.setPhysical(false);
                this.filesStorageNodeRepository.save(targetNode);
                throw new RuntimeException("(getFile) Exception occurred: Could not read the file!");
            }

            Resource resource = new UrlResource(validFilePath.toUri());
            if (!(resource.exists() || resource.isReadable())) {
                targetNode.setReadable(false);
                this.filesStorageNodeRepository.save(targetNode);
                throw new RuntimeException("(getFile) Exception occurred: Could not read the file!");
            }

            return resource;
        } catch (Exception e) {
            throw new RuntimeException("(getFile) Exception occurred: File download failed: " + e.getMessage());
        }
    }

    public void deleteFile(Long fileId) throws Exception {
        try {
            FilesStorageNode targetNode = null;
            Path targetPath = null;

            if (fileId == null) {
                throw new RuntimeException("(deleteFile) Exception occurred: File node ID cannot be null!");
            }

            Optional<FilesStorageNode> optionalFileNode = this.filesStorageNodeRepository.findById(fileId);

            if (!optionalFileNode.isPresent()) {
                throw new RuntimeException("(deleteFile) Exception occurred: Target node does not exist: " + fileId);
            }

            targetNode = optionalFileNode.get();

            if (targetNode.getIsDirectory()) {
                throw new RuntimeException("(deleteFile) Exception occurred: Target node is a directory: " + fileId);
            }

            if (targetNode.getId() == this.rootId) {
                throw new RuntimeException("(deleteFile) Exception occurred: Root directory cannot be deleted! (How did you even get here?!)");
            }

            targetPath = this.rootPath.resolve(targetNode.getUrl());

            if (targetPath == null || !Files.exists(targetPath)) {
                targetNode.setPhysical(false);
                this.filesStorageNodeRepository.save(targetNode);
                throw new RuntimeException("(deleteFile) Exception occurred: File does not exist on disk: " + targetPath);
            }

            this.filesStorageNodeRepository.delete(targetNode);
            Files.delete(targetPath);
        } catch (Exception e) {
            throw new RuntimeException("(deleteFile) Exception occurred: Could not delete file!", e);
        }
    }

    public FilesStorageNode saveDirectory(Long parentId, String directoryName, String description) throws Exception {
        try {
            Path parentPath = this.rootPath;
            FilesStorageNode parentNode = this.rootNode;
            String validDirectoryName = nameValidator(directoryName);
            Path validDirectoryPath = this.rootPath.resolve(validDirectoryName);
            
            if (parentId == null) {
                throw new RuntimeException("(saveDirectory) Exception occurred: Parent node ID cannot be null!");
            }

            Optional<FilesStorageNode> optionalFileNode = this.filesStorageNodeRepository.findById(parentId);

            if (!optionalFileNode.isPresent()) {
                throw new RuntimeException("(saveDirectory) Exception occurred: Parent node does not exist: " + parentId);
            }

            parentNode = optionalFileNode.get();

            if (!parentNode.getIsDirectory()) {
                throw new RuntimeException("(saveDirectory) Exception occurred: Parent node is not a directory: " + parentId);
            }

            if (parentNode.getId() != this.rootId) {
                parentPath = this.rootPath.resolve(parentNode.getUrl());
                validDirectoryPath = parentPath.resolve(validDirectoryName);
            }

            if (parentPath == null || !Files.exists(parentPath)) {
                parentNode.setPhysical(false);
                this.filesStorageNodeRepository.save(parentNode);
                throw new RuntimeException("(saveDirectory) Exception occurred: Requirements for directory creation were not met: " + parentPath);
            }

            if (Files.exists(validDirectoryPath)) {
                throw new RuntimeException("(saveDirectory) Exception occurred: A directory with the same name already exists in the specified directory: " + validDirectoryPath);
            }

            Path newDirectoryPath = Files.createDirectory(validDirectoryPath);
            FilesStorageNode newDirectory = new FilesStorageNode();

            newDirectory.setParent(parentNode);
            newDirectory.setDescription(description);
            newDirectory.setIsDirectory(true);
            newDirectory.setName(newDirectoryPath.getFileName().toString());
            newDirectory.setUrl(this.rootPath.relativize(newDirectoryPath).toString());

            return this.filesStorageNodeRepository.save(newDirectory);
        } catch (Exception e) {
            throw new RuntimeException("(saveDirectory) Exception occurred: Could not commit directory to disk or database!", e);
        }
    }

    @Transactional /******* ALL ABOUT THEM DIRECTORIES AND THEIR HEALTH *******/
    public FilesStorageNode getDirectory(Long directoryId, Boolean diagnostics, Boolean recovery) throws Exception {
        try {
            FilesStorageNode targetNode = this.rootNode;

            if (directoryId != null) {
                Optional<FilesStorageNode> optionalFileNode = this.filesStorageNodeRepository.findById(directoryId);

                if (!optionalFileNode.isPresent()) {
                    throw new RuntimeException("(getDirectory) Exception occurred: Target node does not exist: " + directoryId);
                }

                targetNode = optionalFileNode.get();

                if (!targetNode.getIsDirectory()) {
                    throw new RuntimeException("(getDirectory) Exception occurred: Target node is not a directory: " + directoryId);
                }

                if (!Files.exists(this.rootPath.resolve(targetNode.getUrl()))) {
                    targetNode.setPhysical(false);
                    this.filesStorageNodeRepository.save(targetNode);
                    throw new RuntimeException("(getDirectory) Exception occurred: Target directory does not exist on disk: " + directoryId);
                }
            }

            buildNodeTree(targetNode, diagnostics, recovery);

            return targetNode;
        } catch (Exception e) {
            throw new RuntimeException("(getDirectory) Exception occurred: Could not build directory tree!", e);
        }
    }

    private void buildNodeTree(FilesStorageNode parentNode, Boolean diagnostics, Boolean recovery) {
        List<FilesStorageNode> childNodes = null;

        if (diagnostics == false) { // Will include only the files that are available and ready for use!
            childNodes = this.filesStorageNodeRepository.findAllByParentIdAndSafeTrueOrderByIsDirectoryDescNameAsc(parentNode.getId());
        } else { // Will include everything including uncomfirmed, unreachable, non-readable, malignant and other...
            childNodes = this.filesStorageNodeRepository.findAllByParentIdOrderByIsDirectoryDescNameAsc(parentNode.getId());
        }

        if (childNodes == null) {
            throw new RuntimeException("(getDirectory) Exception occurred: Could not complete query for children nodes!");
        }

        parentNode.setChildNodes(childNodes);
        List<Path> allFilesPathsList = null;
        Boolean isMarkedForUpdate = null;

        if (diagnostics && recovery) {
            try {
                Path targetPath = this.rootPath.resolve(parentNode.getUrl());
                Stream<Path> filePathsStream = Files.list(targetPath);
                allFilesPathsList = new ArrayList<>(filePathsStream.toList());

                filePathsStream.close();
            } catch (Exception e) {
                throw new RuntimeException("(getDirectory:healthCheck) Exception occurred: Failed to get the directory contents!", e);
            }
        }

        for (FilesStorageNode child : childNodes) {
            if (diagnostics) {
                try {
                    Path targetPath = this.rootPath.resolve(child.getUrl());
                    if (!Files.exists(targetPath)) {
                        child.setPhysical(false);
                        isMarkedForUpdate = true;
                    }

                    if (!child.getIsDirectory()) {
                        Resource resource = new UrlResource(targetPath.toUri());
                        if (!(resource.exists() || resource.isReadable())) {
                            child.setReadable(false);
                            isMarkedForUpdate = true;
                        }
                    }

                    if (!child.getPhysical() && !child.getConfirmed()) {
                        this.filesStorageNodeRepository.delete(child);
                        isMarkedForUpdate = false;
                    }

                    if (isMarkedForUpdate != null && isMarkedForUpdate == true) {
                        this.filesStorageNodeRepository.save(child);
                    }

                    /* Only orphans are allowed from here on out! */
                    if (allFilesPathsList != null && recovery) {
                        allFilesPathsList.removeIf(path -> path.equals(targetPath));
                    }
                } catch (Exception e) {
                    throw new RuntimeException("(getDirectory:healthCheck) Exception occurred: Failed to conduct a health check!", e);
                }
            }

            if (child.getIsDirectory() && child.getPhysical()) {
                buildNodeTree(child, diagnostics, recovery);
            }
        }

        if (allFilesPathsList != null && !allFilesPathsList.isEmpty() && diagnostics && recovery) {
            Iterator<Path> iterator = allFilesPathsList.iterator();
            while (iterator.hasNext()) {
                Path orphanFilePath = iterator.next();
                if (Files.exists(orphanFilePath)) {
                    recoverOrphanNode(parentNode, orphanFilePath);
                }
            }
        }
    }

    private void recoverOrphanNode(FilesStorageNode parentNode, Path orphanFilePath) {
        FilesStorageNode newFilesStorageNode = new FilesStorageNode();
        Path targetPath = orphanFilePath;

        String orphanFileName = orphanFilePath.getFileName().toString();
        String targetFileName = orphanFileName.replaceAll("[^a-zA-Z0-9_.\\-]", "_");
        targetFileName = targetFileName.trim().substring(0, Math.min(targetFileName.length(), this.storageProperties.getMaxNameLength()));
        if (!orphanFileName.equals(targetFileName)) {
            try {
                targetPath = targetPath.getParent().resolve(targetFileName);
                if (Files.exists(targetPath)) {
                    throw new RuntimeException("(getDirectory:diagnostics:recovery) CONGRATULATIONS: Attempt to rename the orphan file/directory failed because the new name clashed with an existing file! This is such a rare exception that you deserve a FUCKING medal for throwing it! RECOVERY FAILED, GOOD DAY SIR!");
                }

                targetPath = Files.move(orphanFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                throw new RuntimeException("(getDirectory:diagnostics:recovery) Exception occurred: Failed to give the orphan an appropriate name!", e);
            }
        }

        newFilesStorageNode.setName(targetFileName);
        newFilesStorageNode.setUrl(this.rootPath.relativize(targetPath).toString());
        newFilesStorageNode.setDescription("Recovered file/directory. In need of immediate attention!");

        newFilesStorageNode.setParent(parentNode);
        newFilesStorageNode.setPhysical(true);
        newFilesStorageNode.setConfirmed(false);
        newFilesStorageNode.setIsDirectory(Files.isDirectory(orphanFilePath));

        FilesStorageNode committedFilesStorageNode = this.filesStorageNodeRepository.save(newFilesStorageNode);
        List<FilesStorageNode> parentChildNodes = parentNode.getChildNodes();
        if (parentChildNodes == null) {
            parentChildNodes = new ArrayList<>();
            parentChildNodes.add(committedFilesStorageNode);
            parentNode.setChildNodes(parentChildNodes);
        } else {
            parentChildNodes.add(committedFilesStorageNode);
        }

        /* !!! committedFilesStorageNode is THE parentNode from here on out! !!! */
        if (newFilesStorageNode.getIsDirectory()) {
            try (Stream<Path> filePathsStream = Files.list(this.rootPath.resolve(committedFilesStorageNode.getUrl()))) {
                filePathsStream.forEach(currentOrphanPath -> recoverOrphanNode(committedFilesStorageNode, currentOrphanPath));
            } catch (Exception e) {
                throw new RuntimeException("(getDirectory:diagnostics:recovery) Exception occurred: Failed to get the directory contents!", e);
            }
        }

        List<FilesStorageNode> newChildNodes = committedFilesStorageNode.getChildNodes();
        if (newChildNodes != null && !newChildNodes.isEmpty()) {
            Collections.sort(newChildNodes, (node1, node2) -> {
                if (node1.getIsDirectory() && !node2.getIsDirectory()) {
                    return -1; // node1 is a directory, so it comes first
                } else if (!node1.getIsDirectory() && node2.getIsDirectory()) {
                    return 1; // node2 is a directory, so it comes first
                } else {
                    // Both nodes are either directories or files, order them by name
                    return node1.getName().compareTo(node2.getName());
                }
            });
        }
    }
    /******* ALL ABOUT THEM DIRECTORIES AND THEIR HEALTH *******/

    public void deleteDirectory(Long directoryId) throws Exception {
        try {
            FilesStorageNode targetNode = null;
            Path targetPath = null;

            if (directoryId == null) {
                throw new RuntimeException("(deleteDirectory) Exception occurred: Directory node ID cannot be null!");
            }

            Optional<FilesStorageNode> optionalFileNode = this.filesStorageNodeRepository.findById(directoryId);

            if (!optionalFileNode.isPresent()) {
                throw new RuntimeException("(deleteDirectory) Exception occurred: Target node does not exist: " + directoryId);
            }

            targetNode = optionalFileNode.get();

            if (!targetNode.getIsDirectory()) {
                throw new RuntimeException("(deleteDirectory) Exception occurred: Target node is not a directory: " + directoryId);
            }

            if (targetNode.getId() == this.rootId) {
                throw new RuntimeException("(deleteDirectory) Exception occurred: Root directory cannot be deleted!");
            }

            targetPath = this.rootPath.resolve(targetNode.getUrl());

            if (targetPath == null || !Files.exists(targetPath)) {
                targetNode.setPhysical(false);
                this.filesStorageNodeRepository.save(targetNode);
                throw new RuntimeException("(deleteDirectory) Exception occurred: Directory does not exist on disk: " + targetPath);
            }

            this.filesStorageNodeRepository.delete(targetNode);
            FileSystemUtils.deleteRecursively(targetPath);
        } catch (Exception e) {
            throw new RuntimeException("(deleteDirectory) Exception occurred: Could not delete directory!", e);
        }
    }

/*
    public void deleteAll() {
        
    }

    TODO: REPORT DAMAGED!
*/ 
}