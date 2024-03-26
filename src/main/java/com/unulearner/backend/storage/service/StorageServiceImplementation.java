package com.unulearner.backend.storage.service;

import java.net.URI;
import java.util.UUID;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.core.io.UrlResource;
import org.springframework.context.annotation.Scope;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;

import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.tasks.StorageTask;
import com.unulearner.backend.storage.tasks.StorageTaskDeleteDirectory;
import com.unulearner.backend.storage.tasks.StorageTaskDeleteFile;
import com.unulearner.backend.storage.tasks.StorageTaskTransferDirectory;
import com.unulearner.backend.storage.tasks.StorageTaskTransferFile;
import com.unulearner.backend.storage.responses.StorageServiceResponse;
import com.unulearner.backend.storage.repository.StorageTasksMap;

import java.nio.file.FileAlreadyExistsException;
import com.unulearner.backend.storage.exceptions.StorageServiceException;

@Service
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class StorageServiceImplementation implements StorageService {
    final private StorageTree storageTree;
    final private StorageTasksMap storageTasksMap;

    private StorageServiceImplementation(StorageTree storageTree, StorageTasksMap storageTasksMap) {
        this.storageTree = storageTree;
        this.storageTasksMap = storageTasksMap;
    }

    //**********************************************************//
    //*                                                        *//
    //*   From here on, it be all about THEM single files!     *//
    //*                                                        *//
    //**********************************************************//

    public StorageTreeNode createFileStorageTreeNode(MultipartFile newFile, String fileDescription, UUID destinationDirectoryID, String onConflict) throws Exception {
        String errorMessage = null;
        Path temporaryFilePath = null;
        StorageTreeNode temporaryStorageTreeNode = null;

        final StorageTreeNode destinationStorageTreeNode = this.storageTree.retrieveStorageTreeNode(destinationDirectoryID);
        if (destinationStorageTreeNode == null || destinationStorageTreeNode.getChildren() == null) {
            errorMessage = "Destination directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString());
            throw new StorageServiceException(errorMessage);
        }

        final String fileName = newFile.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            errorMessage = "Invalid file name!".formatted();
            throw new StorageServiceException(errorMessage);
        }

        if (fileDescription == null || fileDescription.isBlank()) {
            errorMessage = "Invalid file description!".formatted();
            throw new StorageServiceException(errorMessage);
        }

        try {
            temporaryFilePath = Files.createTempFile(fileName, ".tmp");
            if (Files.copy(newFile.getInputStream(), temporaryFilePath, StandardCopyOption.REPLACE_EXISTING) <= 0) {
                errorMessage = "File '%s' is not writeable!".formatted(fileName);
                throw new StorageServiceException(errorMessage);
            }

            //TODO: URL? Parent?
            temporaryStorageTreeNode = new StorageTreeNode(fileName, fileDescription, null, null, null, temporaryFilePath);
        } catch (Exception createTemporaryFileException) {
            errorMessage = "Failed to write '%s' to disk!".formatted(fileName);
            throw new StorageServiceException(errorMessage, createTemporaryFileException);
        }

        try {
            return this.storageTree.commitStorageTreeNode(temporaryStorageTreeNode, destinationStorageTreeNode, false, onConflict);
        } catch (FileAlreadyExistsException nodeAlreadyExistsException) {
            throw new FileAlreadyExistsException(nodeAlreadyExistsException.getMessage());
        } catch (StorageServiceException commitStorageTreeNodeException) {
            errorMessage = "Failed to commit '%s' to permanent storage!".formatted(fileName);
            throw new StorageServiceException(errorMessage, commitStorageTreeNodeException);
        }
    }

    public StorageTreeNode updateFileStorageTreeNode(MultipartFile updatedFile, String updatedName, String updatedDescription, UUID targetFileID, String onConflict) throws Exception {
        String errorMessage = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetFileID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() != null) {
            errorMessage = "Target file ID '%s' is invalid!".formatted(targetFileID.toString());
            throw new StorageServiceException(errorMessage);
        }

        //TODO: edit logic...
        final StorageTreeNode temporaryStorageTreeNode = new StorageTreeNode(updatedName, updatedDescription, targetStorageTreeNode.getParent(), targetStorageTreeNode.getChildren(), targetStorageTreeNode.getOnDiskURL(), targetStorageTreeNode.getAbsolutePath());
        temporaryStorageTreeNode.setId(targetStorageTreeNode.getId());

        try {
            return this.storageTree.commitStorageTreeNode(temporaryStorageTreeNode, targetStorageTreeNode.getParent(), false, onConflict);
        } catch (FileAlreadyExistsException nodeAlreadyExistsException) {
            throw new FileAlreadyExistsException(nodeAlreadyExistsException.getMessage());
        } catch (Exception commitStorageTreeNodeException) {
            errorMessage = "Failed to commit changes to '%s' to permanent storage!".formatted(targetStorageTreeNode.getOnDiskName());
            throw new StorageServiceException(errorMessage, commitStorageTreeNodeException);
        }
    }

    public StorageServiceResponse transferFileStorageTreeNode(UUID targetFileID, UUID destinationDirectoryID, Boolean persistOriginal) throws Exception {
        String errorMessage = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetFileID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() != null) {
            errorMessage = "Target file ID '%s' is invalid!".formatted(targetFileID.toString());
            throw new StorageServiceException(errorMessage);
        }

        final StorageTreeNode destinationStorageTreeNode = this.storageTree.retrieveStorageTreeNode(destinationDirectoryID);
        if (destinationStorageTreeNode == null || destinationStorageTreeNode.getChildren() == null) {
            errorMessage = "Destination Directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString());
            throw new StorageServiceException(errorMessage);
        }

        final StorageTaskTransferFile storageTask;
        try {
            storageTask = new StorageTaskTransferFile(this.storageTree, targetStorageTreeNode, destinationStorageTreeNode, persistOriginal, this.storageTasksMap);
        } catch (Exception exception) {
            errorMessage = "File %s task creation failed: %s".formatted(persistOriginal ? "copy" : "move", exception.getMessage());
            throw new StorageServiceException(errorMessage, exception);
        }
        
        return storageTask.getCurrentState();
    }

    public StorageServiceResponse deleteFileStorageTreeNode(UUID targetFileID) throws Exception {
        String errorMessage = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetFileID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() != null) {
            errorMessage = "Target File ID '%s' is invalid!".formatted(targetFileID.toString());
            throw new StorageServiceException(errorMessage);
        }

        this.storageTree.removeStorageTreeNode(targetStorageTreeNode);

        final StorageTaskDeleteFile storageTask;
        try {
            storageTask = new StorageTaskDeleteFile(this.storageTree, targetStorageTreeNode, null, this.storageTasksMap);
        } catch (Exception exception) {
            errorMessage = "File removal task creation failed: %s".formatted(exception.getMessage());
            throw new StorageServiceException(errorMessage, exception);
        }

        return storageTask.getCurrentState();
    }

    public Resource downloadFileStorageTreeNode(UUID targetFileID) throws Exception {
        String errorMessage = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetFileID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() != null) {
            errorMessage = "Target File ID '%s' is invalid!".formatted(targetFileID.toString());
            throw new StorageServiceException(errorMessage);
        }

        try {
            final URI targetURI = targetStorageTreeNode.getAbsolutePath().toUri();
            if (targetURI == null) {
                errorMessage = "File '%s' could not be reached!".formatted(targetStorageTreeNode.getOnDiskName());
                throw new StorageServiceException(errorMessage);
            }

            final Resource targetResource = new UrlResource(targetURI);
            if (targetResource == null || !targetResource.isReadable()) {
                errorMessage = "File '%s' could not be read!".formatted(targetStorageTreeNode.getOnDiskName());
                throw new StorageServiceException(errorMessage);
            }

            return targetResource;
        } catch (Exception retrieveResourceFromStorageException) {
            errorMessage = "Failed to retrieve '%s' input stream from permanent storage!".formatted(targetStorageTreeNode.getOnDiskName());
            throw new StorageServiceException(errorMessage, retrieveResourceFromStorageException);
        }
    }

    //*********************************************************//
    //*                                                       *//
    //*   From here on, it be all about THEM directories!     *//
    //*                                                       *//
    //*********************************************************//

    public StorageTreeNode createDirectoryStorageTreeNode(String directoryName, String directoryDescription, UUID destinationDirectoryID, String onConflict) throws Exception {
        String errorMessage = null;
        Path temporaryDirectoryPath = null;
        StorageTreeNode temporaryStorageTreeNode = null;

        final StorageTreeNode destinationStorageTreeNode = this.storageTree.retrieveStorageTreeNode(destinationDirectoryID);
        if (destinationStorageTreeNode == null || destinationStorageTreeNode.getChildren() == null) {
            errorMessage = "Destination directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString());
            throw new StorageServiceException(errorMessage);
        }

        if (directoryName == null || directoryName.isBlank()) {
            errorMessage = "Invalid directory name!".formatted();
            throw new StorageServiceException(errorMessage);
        }

        if (directoryDescription == null || directoryDescription.isBlank()) {
            errorMessage = "Invalid directory description!".formatted();
            throw new StorageServiceException(errorMessage);
        }

        try {
            temporaryDirectoryPath = Files.createTempDirectory(directoryName);
            temporaryStorageTreeNode = new StorageTreeNode(directoryName, directoryDescription, null, null, null, temporaryDirectoryPath);
        } catch (Exception createTemporaryDirectoryException) {
            errorMessage = "Failed to write '%s' to disk!".formatted(directoryName);
            throw new StorageServiceException(errorMessage, createTemporaryDirectoryException);
        }

        try {
            return this.storageTree.commitStorageTreeNode(temporaryStorageTreeNode, destinationStorageTreeNode, false, onConflict);
        } catch (FileAlreadyExistsException nodeAlreadyExistsException) {
            throw new FileAlreadyExistsException(nodeAlreadyExistsException.getMessage());
        } catch (Exception commitStorageTreeNodeException) {
            errorMessage = "Failed to commit '%s' to permanent storage!".formatted(directoryName);
            throw new StorageServiceException(errorMessage, commitStorageTreeNodeException);
        }
    }

    public StorageTreeNode updateDirectoryStorageTreeNode(String updatedName, String updatedDescription, UUID targetDirectoryID, String onConflict) throws Exception {
        String errorMessage = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetDirectoryID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() == null) {
            errorMessage = "Target directory ID '%s' is invalid!".formatted(targetDirectoryID.toString());
            throw new StorageServiceException(errorMessage);
        }

        //TODO: edit logic...
        final StorageTreeNode temporaryStorageTreeNode = new StorageTreeNode(updatedName, updatedDescription, targetStorageTreeNode.getParent(), targetStorageTreeNode.getChildren(), targetStorageTreeNode.getOnDiskURL(), targetStorageTreeNode.getAbsolutePath());
        temporaryStorageTreeNode.setId(targetStorageTreeNode.getId());

        try {
            return this.storageTree.commitStorageTreeNode(temporaryStorageTreeNode, targetStorageTreeNode.getParent(), false, onConflict);
        } catch (FileAlreadyExistsException nodeAlreadyExistsException) {
            throw new FileAlreadyExistsException(nodeAlreadyExistsException.getMessage());
        } catch (Exception commitStorageTreeNodeException) {
            errorMessage = "Failed to commit changes to '%s' to permanent storage!".formatted(targetStorageTreeNode.getOnDiskName());
            throw new StorageServiceException(errorMessage, commitStorageTreeNodeException);
        }
    }

    public StorageServiceResponse transferDirectoryStorageTreeNode(UUID targetDirectoryID, UUID destinationDirectoryID, Boolean persistOriginal) throws Exception {
        String errorMessage = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetDirectoryID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() == null) {
            errorMessage = "Target directory ID '%s' is invalid!".formatted(targetDirectoryID.toString());
            throw new StorageServiceException(errorMessage);
        }

        final StorageTreeNode destinationStorageTreeNode = this.storageTree.retrieveStorageTreeNode(destinationDirectoryID);
        if (destinationStorageTreeNode == null || destinationStorageTreeNode.getChildren() == null) {
            errorMessage = "Destination directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString());
            throw new StorageServiceException(errorMessage);
        }

        final StorageTaskTransferDirectory storageTask;
        try {
            storageTask = new StorageTaskTransferDirectory(this.storageTree, targetStorageTreeNode, destinationStorageTreeNode, persistOriginal, this.storageTasksMap);
        } catch (Exception exception) {
            errorMessage = "Directory %s task creation failed: %s".formatted(persistOriginal ? "copy" : "move", exception.getMessage());
            throw new StorageServiceException(errorMessage, exception);
        }
        
        return storageTask.getCurrentState();
    }

    public StorageServiceResponse deleteDirectoryStorageTreeNode(UUID targetDirectoryID) throws Exception {
        String errorMessage = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetDirectoryID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() == null) {
            errorMessage = "Target directory ID '%s' is invalid!".formatted(targetDirectoryID.toString());
            throw new StorageServiceException(errorMessage);
        }

        final StorageTaskDeleteDirectory storageTask;
        try {
            storageTask = new StorageTaskDeleteDirectory(this.storageTree, targetStorageTreeNode, null, this.storageTasksMap);
        } catch (Exception exception) {
            errorMessage = "Directory removal task creation failed: %s".formatted(exception.getMessage());
            throw new StorageServiceException(errorMessage, exception);
        }

        return storageTask.getCurrentState();
    }


    public StorageTreeNode downloadDirectoryStorageTreeNode(UUID targetDirectoryID) throws Exception {
        String errorMessage = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetDirectoryID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() == null) {
            errorMessage = "Target directory ID '%s' is invalid!".formatted(targetDirectoryID.toString());
            throw new StorageServiceException(errorMessage);
        }

        return targetStorageTreeNode;
    }

    //***************************************************//
    //*                                                 *//
    //*   From here on, it be all about THEM tasks!     *//
    //*                                                 *//
    //***************************************************//

    public StorageServiceResponse executeStorageTask(UUID taskID, String onConflictAction, Boolean onConflictActionIsPersistent, Boolean cancelTaskExecution) throws Exception {
        String errorMessage = null;

        if (taskID == null) {
            errorMessage = "Task ID is null!".formatted();
            throw new StorageServiceException(errorMessage);
        }

        final StorageTask storageTask;
        try {
            storageTask = this.storageTasksMap.getStorageTask(taskID);
            if (storageTask == null) {
                errorMessage = "Task ID '%s' is invalid!".formatted(taskID.toString());
                throw new StorageServiceException(errorMessage);
            }
        } catch (Exception exception) {
            if (cancelTaskExecution != null && cancelTaskExecution == true) {
                errorMessage = "Task ID '%s' cannot be canceled: %s".formatted(taskID.toString(), exception.getMessage());
            } else {
                errorMessage = "Task ID '%s' cannot be executed: %s".formatted(taskID.toString(), exception.getMessage());
            }

            throw new StorageServiceException(errorMessage, exception);
        }

        /* Synchronized, setters invisible, whatever... */
        storageTask.executeTask(onConflictAction, onConflictActionIsPersistent, cancelTaskExecution);

        return storageTask.getCurrentState();
    }

    //***************************************************//
    //*                                                 *//
    //*   From here on, it be all about THEM rests!     *//
    //*                                                 *//
    //***************************************************//

    /**
     * @return
     * @throws Exception
     */
    public StorageTreeNode downloadStorageTreeRootNode() throws Exception {
        return this.storageTree.retrieveStorageTreeRoot();
    }
}
