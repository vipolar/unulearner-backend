package com.unulearner.backend.storage.service;

import java.net.URI;

import java.util.UUID;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Scope;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;

import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;

import com.unulearner.backend.storage.tasks.StorageRemoveRecursivelyTask;
import com.unulearner.backend.storage.tasks.StorageTransferRecursivelyTask;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.responses.StorageServiceResponse;

import java.nio.file.FileAlreadyExistsException;
import com.unulearner.backend.storage.exceptions.StorageServiceException;

@Service
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class StorageServiceImplementation implements StorageService {

    // TODO: Cache this thing
    @Autowired
    private StorageTree storageTree;

    final private StorageTasksMap<StorageRemoveRecursivelyTask> storageRemoveRecursivelyTaskMap;
    final private StorageTasksMap<StorageTransferRecursivelyTask> storageTransferRecursivelyTaskMap;

    private StorageServiceImplementation(
        StorageTasksMap<StorageRemoveRecursivelyTask> storageRemoveRecursivelyTaskMap,
        StorageTasksMap<StorageTransferRecursivelyTask> storageTransferRecursivelyTaskMap
    ) {
        this.storageRemoveRecursivelyTaskMap = storageRemoveRecursivelyTaskMap;
        this.storageTransferRecursivelyTaskMap = storageTransferRecursivelyTaskMap;
    }

    /* FILES */

    public StorageTreeNode createFileStorageTreeNode(MultipartFile newFile, String fileDescription, UUID destinationDirectoryID, String onConflict) throws Exception {
        String errorMessage = null;
        Path temporaryFilePath = null;
        StorageTreeNode temporaryStorageTreeNode = null;

        final StorageTreeNode destinationStorageTreeNode = this.storageTree.retrieveStorageTreeNode(destinationDirectoryID);
        if (destinationStorageTreeNode == null || destinationStorageTreeNode.getChildren() == null) {
            errorMessage = String.format("Destination directory ID '%s' is invalid!", destinationDirectoryID.toString());
            throw new StorageServiceException(errorMessage);
        }

        final String fileName = newFile.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            errorMessage = String.format("Invalid file name!");
            throw new StorageServiceException(errorMessage);
        }

        if (fileDescription == null || fileDescription.isBlank()) {
            errorMessage = String.format("Invalid file description!");
            throw new StorageServiceException(errorMessage);
        }

        try {
            temporaryFilePath = Files.createTempFile(fileName, ".tmp");
            if (Files.copy(newFile.getInputStream(), temporaryFilePath, StandardCopyOption.REPLACE_EXISTING) <= 0) {
                errorMessage = String.format("File '%s' is not writeable!", fileName);
                throw new StorageServiceException(errorMessage);
            }

            //TODO: URL? Parent?
            temporaryStorageTreeNode = new StorageTreeNode(fileName, fileDescription, null, null, null, temporaryFilePath);
        } catch (Exception createTemporaryFileException) {
            errorMessage = String.format("Failed to write '%s' to disk!", fileName);
            throw new StorageServiceException(errorMessage, createTemporaryFileException);
        }

        try {
            return this.storageTree.commitStorageTreeNode(temporaryStorageTreeNode, destinationStorageTreeNode, false, onConflict);
        } catch (FileAlreadyExistsException nodeAlreadyExistsException) {
            throw new FileAlreadyExistsException(nodeAlreadyExistsException.getMessage());
        } catch (StorageServiceException commitStorageTreeNodeException) {
            errorMessage = String.format("Failed to commit '%s' to permanent storage!", fileName);
            throw new StorageServiceException(errorMessage, commitStorageTreeNodeException);
        }
    }

    public StorageTreeNode updateFileStorageTreeNode(MultipartFile updatedFile, String updatedName, String updatedDescription, UUID targetFileID, String onConflict) throws Exception {
        String errorMessage = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetFileID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() != null) {
            errorMessage = String.format("Target file ID '%s' is invalid!", targetFileID.toString());
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
            errorMessage = String.format("Failed to commit changes to '%s' to permanent storage!", targetStorageTreeNode.getOnDiskName());
            throw new StorageServiceException(errorMessage, commitStorageTreeNodeException);
        }
    }

    public StorageTreeNode transferFileStorageTreeNode(UUID targetFileID, UUID destinationDirectoryID, Boolean persistOriginalNode, String onConflict) throws Exception {
        String errorMessage = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetFileID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() != null) {
            errorMessage = String.format("Target file ID '%s' is invalid!", targetFileID.toString());
            throw new StorageServiceException(errorMessage);
        }

        final StorageTreeNode destinationStorageTreeNode = this.storageTree.retrieveStorageTreeNode(destinationDirectoryID);
        if (destinationStorageTreeNode == null || destinationStorageTreeNode.getChildren() == null) {
            errorMessage = String.format("Destination Directory ID '%s' is invalid!", destinationDirectoryID.toString());
            throw new StorageServiceException(errorMessage);
        }

        try {
            return this.storageTree.commitStorageTreeNode(targetStorageTreeNode, destinationStorageTreeNode, persistOriginalNode, onConflict);
        } catch (Exception commitStorageTreeNodeException) {
            errorMessage = String.format("Failed to %s file '%s' to '%s' and commit it to permanent storage!", persistOriginalNode ? "copy" : "move", targetStorageTreeNode.getOnDiskName(), destinationStorageTreeNode.getOnDiskName());
            throw new StorageServiceException(errorMessage, commitStorageTreeNodeException);
        }
    }

    public Resource downloadFileStorageTreeNode(UUID targetFileID) throws Exception {
        String errorMessage = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetFileID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() != null) {
            errorMessage = String.format("Target File ID '%s' is invalid!", targetFileID.toString());
            throw new StorageServiceException(errorMessage);
        }

        try {
            final URI targetURI = targetStorageTreeNode.getAbsolutePath().toUri();
            if (targetURI == null) {
                errorMessage = String.format("File '%s' could not be reached!", targetStorageTreeNode.getOnDiskName());
                throw new StorageServiceException(errorMessage);
            }

            final Resource targetResource = new UrlResource(targetURI);
            if (targetResource == null || !targetResource.isReadable()) {
                errorMessage = String.format("File '%s' could not be read!", targetStorageTreeNode.getOnDiskName());
                throw new StorageServiceException(errorMessage);
            }

            return targetResource;
        } catch (Exception retrieveResourceFromStorageException) {
            errorMessage = String.format("Failed to retrieve '%s' input stream from permanent storage!", targetStorageTreeNode.getOnDiskName());
            throw new StorageServiceException(errorMessage, retrieveResourceFromStorageException);
        }
    }

    public void deleteFileStorageTreeNode(UUID targetFileID) throws Exception {
        String errorMessage = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetFileID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() != null) {
            errorMessage = String.format("Target File ID '%s' is invalid!", targetFileID.toString());
            throw new StorageServiceException(errorMessage);
        }

        this.storageTree.removeStorageTreeNode(targetStorageTreeNode);
    }

    /* DIRECTORIES */

    public StorageTreeNode createDirectoryStorageTreeNode(String directoryName, String directoryDescription, UUID destinationDirectoryID, String onConflict) throws Exception {
        String errorMessage = null;
        Path temporaryDirectoryPath = null;
        StorageTreeNode temporaryStorageTreeNode = null;

        final StorageTreeNode destinationStorageTreeNode = this.storageTree.retrieveStorageTreeNode(destinationDirectoryID);
        if (destinationStorageTreeNode == null || destinationStorageTreeNode.getChildren() == null) {
            errorMessage = String.format("Destination directory ID '%s' is invalid!", destinationDirectoryID.toString());
            throw new StorageServiceException(errorMessage);
        }

        if (directoryName == null || directoryName.isBlank()) {
            errorMessage = String.format("Invalid directory name!");
            throw new StorageServiceException(errorMessage);
        }

        if (directoryDescription == null || directoryDescription.isBlank()) {
            errorMessage = String.format("Invalid directory description!");
            throw new StorageServiceException(errorMessage);
        }

        try {
            temporaryDirectoryPath = Files.createTempDirectory(directoryName);
            temporaryStorageTreeNode = new StorageTreeNode(directoryName, directoryDescription, null, null, null, temporaryDirectoryPath);
        } catch (Exception createTemporaryDirectoryException) {
            errorMessage = String.format("Failed to write '%s' to disk!", directoryName);
            throw new StorageServiceException(errorMessage, createTemporaryDirectoryException);
        }

        try {
            return this.storageTree.commitStorageTreeNode(temporaryStorageTreeNode, destinationStorageTreeNode, false, onConflict);
        } catch (FileAlreadyExistsException nodeAlreadyExistsException) {
            throw new FileAlreadyExistsException(nodeAlreadyExistsException.getMessage());
        } catch (Exception commitStorageTreeNodeException) {
            errorMessage = String.format("Failed to commit '%s' to permanent storage!", directoryName);
            throw new StorageServiceException(errorMessage, commitStorageTreeNodeException);
        }
    }

    public StorageTreeNode updateDirectoryStorageTreeNode(String updatedName, String updatedDescription, UUID targetDirectoryID, String onConflict) throws Exception {
        String errorMessage = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetDirectoryID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() == null) {
            errorMessage = String.format("Target directory ID '%s' is invalid!", targetDirectoryID.toString());
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
            errorMessage = String.format("Failed to commit changes to '%s' to permanent storage!", targetStorageTreeNode.getOnDiskName());
            throw new StorageServiceException(errorMessage, commitStorageTreeNodeException);
        }
    }

    public StorageTreeNode transferDirectoryStorageTreeNode(UUID targetDirectoryID, UUID destinationDirectoryID, Boolean persistOriginalNode, String onConflict) throws Exception {
        return transferDirectoryStorageTreeNode(targetDirectoryID, destinationDirectoryID, persistOriginalNode, onConflict, null, null, null).getNode();
    }

    public StorageServiceResponse transferDirectoryStorageTreeNode(UUID targetDirectoryID, UUID destinationDirectoryID, Boolean persistOriginalNode, String onConflict, UUID taskID, String newOnExceptionAction, Boolean newOnExceptionActionIsPersistent) throws Exception {
        Integer timeLeft = null;
        String logMessage = null;
        String errorMessage = null;
        String successMessage = null;
        String exceptionMessage = null;
        String onExceptionAction = null;
        StorageTreeNode temporaryNode = null;
        StorageTreeNode currentTargetNode = null;
        StorageTreeNode currentDestinationNode = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetDirectoryID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() == null) {
            errorMessage = String.format("Target directory ID '%s' is invalid!", targetDirectoryID.toString());
            throw new StorageServiceException(errorMessage);
        }

        final StorageTreeNode destinationStorageTreeNode = this.storageTree.retrieveStorageTreeNode(destinationDirectoryID);
        if (destinationStorageTreeNode == null || destinationStorageTreeNode.getChildren() == null) {
            errorMessage = String.format("Destination directory ID '%s' is invalid!", destinationDirectoryID.toString());
            throw new StorageServiceException(errorMessage);
        }

        final StorageTransferRecursivelyTask storageTransferRecursivelyTask;
        if (taskID != null) {
            try {
                storageTransferRecursivelyTask = this.storageTransferRecursivelyTaskMap.getStorageTask(taskID);
                if (storageTransferRecursivelyTask == null) {
                    errorMessage = String.format("Task ID '%s' is invalid!", taskID.toString());
                    throw new StorageServiceException(errorMessage);
                }
            } catch (Exception exception) {
                errorMessage = String.format("Task ID '%s' could not be resumed!", taskID.toString());
                throw new StorageServiceException(errorMessage, exception);
            }
        } else {
            try {
                storageTransferRecursivelyTask = new StorageTransferRecursivelyTask(targetStorageTreeNode, destinationStorageTreeNode);
                taskID = this.storageTransferRecursivelyTaskMap.addStorageTask(storageTransferRecursivelyTask);
            } catch (Exception exception) {
                errorMessage = String.format("Task creation failed!");
                throw new StorageServiceException(errorMessage, exception);
            }
        }

        if (newOnExceptionAction != null) {
            currentTargetNode = storageTransferRecursivelyTask.getTransferTarget();
            currentDestinationNode = storageTransferRecursivelyTask.getTransferDestination();
            switch (newOnExceptionAction) {
                case "retry":
                    break;
                case "ignore":
                    logMessage = String.format("%s '%s' was ignored!", currentTargetNode.getChildren() != null ? "Directory" : "File", currentTargetNode.getOnDiskURL());
                    storageTransferRecursivelyTask.logTransferAttempt(logMessage);
                    storageTransferRecursivelyTask.advanceTransferIterator(null);
                    break;
                case "cancel":
                    this.storageRemoveRecursivelyTaskMap.removeStorageTask(taskID);
                    successMessage = String.format("Directory '%s' transfer to '%s' was cancelled!", targetStorageTreeNode.getOnDiskURL(), currentDestinationNode.getOnDiskURL());
                    return new StorageServiceResponse(successMessage, HttpStatus.OK, null, taskID, storageTransferRecursivelyTask.getTransferLog(), null, null);
                default:
                    timeLeft = this.storageRemoveRecursivelyTaskMap.scheduleStorageTaskRemoval(taskID);
                    errorMessage = String.format("Action '%s' is invalid!", newOnExceptionAction);
                    return new StorageServiceResponse(errorMessage, HttpStatus.BAD_REQUEST, currentTargetNode, taskID, storageTransferRecursivelyTask.getTransferLog(), null, null);
            }

            if (newOnExceptionActionIsPersistent != null && newOnExceptionActionIsPersistent == true) {
                if (currentTargetNode.getChildren() != null) {
                    storageTransferRecursivelyTask.setOnDirectoryException(newOnExceptionAction);
                } else {
                    storageTransferRecursivelyTask.setOnFileException(newOnExceptionAction);
                }
            }
        }

        do {
            currentTargetNode = storageTransferRecursivelyTask.getTransferTarget();
            currentDestinationNode = storageTransferRecursivelyTask.getTransferDestination();

            try {
                temporaryNode = currentTargetNode;
                //temporaryNode = this.storageTree.commitStorageTreeNode(currentTargetNode, currentDestinationNode, persistOriginalNode, onConflict);
                logMessage = String.format("%s '%s' was transfered to '%s' directory successfully!", currentTargetNode.getChildren() != null ? "Directory" : "File", currentTargetNode.getOnDiskURL(), currentDestinationNode.getOnDiskURL());
                storageTransferRecursivelyTask.logTransferAttempt(logMessage);
                storageTransferRecursivelyTask.advanceTransferIterator(temporaryNode);
            } catch (Exception exception) {
                onExceptionAction = currentTargetNode.getChildren() != null ? storageTransferRecursivelyTask.getOnDirectoryException() : storageTransferRecursivelyTask.getOnFileException();

                if (onExceptionAction != null && onExceptionAction.equals("ignore")) {
                    logMessage = String.format("%s '%s' was ignored!", currentTargetNode.getChildren() != null ? "Directory" : "File", currentTargetNode.getOnDiskURL());
                    storageTransferRecursivelyTask.logTransferAttempt(logMessage);
                    storageTransferRecursivelyTask.advanceTransferIterator(null);
                    continue;
                }

                if (exception instanceof FileAlreadyExistsException) {
                    //do the stuff!
                }

                timeLeft = this.storageRemoveRecursivelyTaskMap.scheduleStorageTaskRemoval(taskID);
                exceptionMessage = String.format("%s '%s' could not be transfered to '%s' directory: %s", currentTargetNode.getChildren() != null ? "Directory" : "File", currentTargetNode.getOnDiskURL(), currentDestinationNode.getOnDiskURL(), exception.getMessage());
                return new StorageServiceResponse(exceptionMessage, HttpStatus.UNPROCESSABLE_ENTITY, currentTargetNode, taskID, storageTransferRecursivelyTask.getTransferLog(), storageTransferRecursivelyTask.getOnExceptionOptions(currentTargetNode.getChildren() != null), timeLeft);
            }
        } while (storageTransferRecursivelyTask.hasNextTarget());

        for (String ss : storageTransferRecursivelyTask.getTransferLog()) {
            System.out.println("-----");
            System.out.println(ss);
        }

        this.storageRemoveRecursivelyTaskMap.removeStorageTask(taskID);
        successMessage = String.format("Directory '%s' and all of its contents have been transfered to '%s' directory successfully!", targetStorageTreeNode.getOnDiskURL(), destinationStorageTreeNode.getOnDiskURL());
        return new StorageServiceResponse(successMessage, HttpStatus.OK, null, taskID, storageTransferRecursivelyTask.getTransferLog(), null, null);
    }

    public StorageTreeNode downloadDirectoryStorageTreeNode(UUID targetDirectoryID) throws Exception {
        String errorMessage = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetDirectoryID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() == null) {
            errorMessage = String.format("Target directory ID '%s' is invalid!", targetDirectoryID.toString());
            throw new StorageServiceException(errorMessage);
        }

        return targetStorageTreeNode;
    }

    public StorageServiceResponse deleteDirectoryStorageTreeNode(UUID targetDirectoryID, UUID taskID, String newOnExceptionAction, Boolean newOnExceptionActionIsPersistent) throws Exception {
        Integer timeLeft = null;
        String logMessage = null;
        String errorMessage = null;
        String successMessage = null;
        String exceptionMessage = null;
        String onExceptionAction = null;
        StorageTreeNode currentNode = null;

        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetDirectoryID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.getChildren() == null) {
            errorMessage = String.format("Target directory ID '%s' is invalid!", targetDirectoryID.toString());
            throw new StorageServiceException(errorMessage);
        }

        final StorageRemoveRecursivelyTask storageRemoveRecursivelyTask;
        if (taskID != null) {
            try {
                storageRemoveRecursivelyTask = this.storageRemoveRecursivelyTaskMap.getStorageTask(taskID);
                if (storageRemoveRecursivelyTask == null) {
                    errorMessage = String.format("Task ID '%s' is invalid!", taskID.toString());
                    throw new StorageServiceException(errorMessage);
                }
            } catch (Exception exception) {
                errorMessage = String.format("Task ID '%s' could not be resumed!", taskID.toString());
                throw new StorageServiceException(errorMessage, exception);
            }
        } else {
            try {
                storageRemoveRecursivelyTask = new StorageRemoveRecursivelyTask(this.storageTree, targetStorageTreeNode);
                taskID = this.storageRemoveRecursivelyTaskMap.addStorageTask(storageRemoveRecursivelyTask);
            } catch (Exception exception) {
                errorMessage = String.format("Task creation failed!");
                throw new StorageServiceException(errorMessage, exception);
            }
        }

        if (newOnExceptionAction != null) {
            currentNode = storageRemoveRecursivelyTask.getRemovalTarget();
            switch (newOnExceptionAction) {
                case "retry":
                    break;
                case "ignore":
                    logMessage = String.format("%s '%s' was ignored!", currentNode.getChildren() != null ? "Directory" : "File", currentNode.getOnDiskURL());
                    storageRemoveRecursivelyTask.logRemovalAttempt(logMessage);
                    storageRemoveRecursivelyTask.advanceRemovalIterator();
                    break;
                case "cancel":
                    this.storageRemoveRecursivelyTaskMap.removeStorageTask(taskID);
                    successMessage = String.format("Directory '%s' removal was cancelled!", targetStorageTreeNode.getOnDiskURL());
                    return new StorageServiceResponse(successMessage, HttpStatus.OK, null, taskID, storageRemoveRecursivelyTask.getRemovalLog(), null, null);
                default:
                    timeLeft = this.storageRemoveRecursivelyTaskMap.scheduleStorageTaskRemoval(taskID);
                    errorMessage = String.format("Action '%s' is invalid!", newOnExceptionAction);
                    return new StorageServiceResponse(errorMessage, HttpStatus.BAD_REQUEST, currentNode, taskID, storageRemoveRecursivelyTask.getRemovalLog(), null, null);
            }

            if (newOnExceptionActionIsPersistent != null && newOnExceptionActionIsPersistent == true) {
                if (currentNode.getChildren() != null) {
                    storageRemoveRecursivelyTask.setOnDirectoryException(newOnExceptionAction);
                } else {
                    storageRemoveRecursivelyTask.setOnFileException(newOnExceptionAction);
                }
            }
        }

        if (storageRemoveRecursivelyTask.executedSuccessfully() == true) {
            timeLeft = this.storageRemoveRecursivelyTaskMap.removeStorageTask(taskID);
        } else {
            timeLeft = this.storageRemoveRecursivelyTaskMap.scheduleStorageTaskRemoval(taskID);
        }

        return new StorageServiceResponse(storageRemoveRecursivelyTask.getExitMessage(), storageRemoveRecursivelyTask.getExitHttpStatus(), storageRemoveRecursivelyTask.getCurrentTarget(), taskID, storageRemoveRecursivelyTask.getLog(), storageRemoveRecursivelyTask.getOnExceptionOptions(), timeLeft);
    }

    /* THE REST */

    /**
     * @return
     * @throws Exception
     */
    public StorageTreeNode downloadStorageTreeRootNode() throws Exception {
        return this.storageTree.retrieveStorageTreeRoot();
    }
}
