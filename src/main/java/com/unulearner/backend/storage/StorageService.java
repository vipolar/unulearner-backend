package com.unulearner.backend.storage;

import java.net.URI;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.core.io.UrlResource;
import org.springframework.context.annotation.Scope;
import org.springframework.web.multipart.MultipartFile;

import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.tasks.StorageTaskBase;
import com.unulearner.backend.storage.entities.StorageTreeNode;
import com.unulearner.backend.storage.tasks.StorageTaskCreateNode;
import com.unulearner.backend.storage.tasks.StorageTaskUpdateNode;
import com.unulearner.backend.storage.tasks.StorageTaskDestroyNode;
import com.unulearner.backend.storage.tasks.StorageTaskTransferNode;

import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.responses.StorageServiceResponse;
import com.unulearner.backend.storage.exceptions.StorageServiceException;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;

@Service
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class StorageService {
    final private StorageTree storageTree;
    final private StorageTasksMap storageTasksMap;

    private StorageService(StorageTree storageTree, StorageTasksMap storageTasksMap) {
        this.storageTree = storageTree;
        this.storageTasksMap = storageTasksMap;
    }

    //**********************************************************//
    //*                                                        *//
    //*   From here on, it be all about THEM single files!     *//
    //*                                                        *//
    //**********************************************************//

    public StorageServiceResponse createFileStorageTreeNode(MultipartFile newFile, String fileDescription, UUID destinationDirectoryID) throws Exception {
        final StorageTreeNode destinationStorageTreeNode = this.storageTree.retrieveStorageTreeNode(destinationDirectoryID);
        if (destinationStorageTreeNode == null || !destinationStorageTreeNode.isDirectory()) {
            throw new StorageServiceException("Destination directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString()));
        }

        try {
            final StorageTaskCreateNode storageTask = new StorageTaskCreateNode(this.storageTree, newFile, fileDescription, destinationStorageTreeNode, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("File upload task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse updateFileStorageTreeNode(UUID targetFileID, String updatedName, String updatedDescription) throws Exception {
        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetFileID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.isDirectory()) {
            throw new StorageServiceException("Target file ID '%s' is invalid!".formatted(targetFileID.toString()));
        }

        try {
            final StorageTaskUpdateNode storageTask = new StorageTaskUpdateNode(this.storageTree, updatedName, targetStorageTreeNode, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("File update task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse transferFileStorageTreeNode(UUID targetFileID, UUID destinationDirectoryID, Boolean persistOriginal) throws Exception {
        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetFileID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.isDirectory()) {
            throw new StorageServiceException("Target file ID '%s' is invalid!".formatted(targetFileID.toString()));
        }

        final StorageTreeNode destinationStorageTreeNode = this.storageTree.retrieveStorageTreeNode(destinationDirectoryID);
        if (destinationStorageTreeNode == null || !destinationStorageTreeNode.isDirectory()) {
            throw new StorageServiceException("Destination Directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString()));
        }

        try {
            final StorageTaskTransferNode storageTask = new StorageTaskTransferNode(this.storageTree, targetStorageTreeNode, destinationStorageTreeNode, persistOriginal, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("File %s task initialization failed: %s".formatted(persistOriginal ? "copy" : "move", exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse deleteFileStorageTreeNode(UUID targetFileID) throws Exception {
        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetFileID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.isDirectory()) {
            throw new StorageServiceException("Target File ID '%s' is invalid!".formatted(targetFileID.toString()));
        }

        try {
            final StorageTaskDestroyNode storageTask = new StorageTaskDestroyNode(this.storageTree, targetStorageTreeNode, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("File removal task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public Resource downloadFileStorageTreeNode(UUID targetFileID) throws Exception {
        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetFileID);
        if (targetStorageTreeNode == null || targetStorageTreeNode.isDirectory()) {
            throw new StorageServiceException("Target File ID '%s' is invalid!".formatted(targetFileID.toString()));
        }

        try {
            final URI targetURI = targetStorageTreeNode.getNodePath().getPath().toUri();
            if (targetURI == null) {
                throw new StorageServiceException("File '%s' could not be reached!".formatted(targetStorageTreeNode.getOnDiskName()));
            }

            final Resource targetResource = new UrlResource(targetURI);
            if (targetResource == null || !targetResource.isReadable()) {
                throw new StorageServiceException("File '%s' could not be read!".formatted(targetStorageTreeNode.getOnDiskName()));
            }

            return targetResource;
        } catch (Exception retrieveResourceFromStorageException) {
            throw new StorageServiceException("Failed to retrieve '%s' input stream from permanent storage!".formatted(targetStorageTreeNode.getOnDiskName(), retrieveResourceFromStorageException));
        }
    }

    //*********************************************************//
    //*                                                       *//
    //*   From here on, it be all about THEM directories!     *//
    //*                                                       *//
    //*********************************************************//

    public StorageServiceResponse createDirectoryStorageTreeNode(String directoryName, String directoryDescription, UUID destinationDirectoryID) throws Exception {
        final StorageTreeNode destinationStorageTreeNode = this.storageTree.retrieveStorageTreeNode(destinationDirectoryID);
        if (destinationStorageTreeNode == null || !destinationStorageTreeNode.isDirectory()) {
            throw new StorageServiceException("Destination directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString()));
        }

        try {
            final StorageTaskCreateNode storageTask = new StorageTaskCreateNode(this.storageTree, directoryName, directoryDescription, destinationStorageTreeNode, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Directory creation task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse updateDirectoryStorageTreeNode(UUID targetDirectoryID, String updatedName, String updatedDescription) throws Exception {
        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetDirectoryID);
        if (targetStorageTreeNode == null || !targetStorageTreeNode.isDirectory()) {
            throw new StorageServiceException("Target directory ID '%s' is invalid!".formatted(targetDirectoryID.toString()));
        }

        try {
            final StorageTaskUpdateNode storageTask = new StorageTaskUpdateNode(this.storageTree, updatedName, targetStorageTreeNode, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Directory update task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse transferDirectoryStorageTreeNode(UUID targetDirectoryID, UUID destinationDirectoryID, Boolean persistOriginal) throws Exception {
        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetDirectoryID);
        if (targetStorageTreeNode == null || !targetStorageTreeNode.isDirectory()) {
            throw new StorageServiceException("Target directory ID '%s' is invalid!".formatted(targetDirectoryID.toString()));
        }

        final StorageTreeNode destinationStorageTreeNode = this.storageTree.retrieveStorageTreeNode(destinationDirectoryID);
        if (destinationStorageTreeNode == null || !destinationStorageTreeNode.isDirectory()) {
            throw new StorageServiceException("Destination directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString()));
        }

        try {
            final StorageTaskTransferNode storageTask = new StorageTaskTransferNode(this.storageTree, targetStorageTreeNode, destinationStorageTreeNode, persistOriginal, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Directory %s task initialization failed: %s".formatted(persistOriginal ? "copy" : "move", exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse deleteDirectoryStorageTreeNode(UUID targetDirectoryID) throws Exception {
        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetDirectoryID);
        if (targetStorageTreeNode == null || !targetStorageTreeNode.isDirectory()) {
            throw new StorageServiceException("Target directory ID '%s' is invalid!".formatted(targetDirectoryID.toString()));
        }

        try {
            final StorageTaskDestroyNode storageTask = new StorageTaskDestroyNode(this.storageTree, targetStorageTreeNode, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Directory removal task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }


    public StorageTreeNode downloadDirectoryStorageTreeNode(UUID targetDirectoryID) throws Exception {
        final StorageTreeNode targetStorageTreeNode = this.storageTree.retrieveStorageTreeNode(targetDirectoryID);
        if (targetStorageTreeNode == null || !targetStorageTreeNode.isDirectory()) {
            throw new StorageServiceException("Target directory ID '%s' is invalid!".formatted(targetDirectoryID.toString()));
        }

        return targetStorageTreeNode;
    }

    //***************************************************//
    //*                                                 *//
    //*   From here on, it be all about THEM tasks!     *//
    //*                                                 *//
    //***************************************************//

    public StorageServiceResponse executeStorageTask(UUID taskID, String onExceptionAction, Boolean onExceptionActionIsPersistent) throws Exception {
        try {
            if (taskID == null) {
                throw new StorageServiceException("Task ID cannot be null!".formatted());
            }

            final StorageTaskBase storageTask = this.storageTasksMap.getStorageTask(taskID);

            if (storageTask == null) {
                throw new StorageServiceException("Task under the ID could not be retrieved".formatted());
            }

            /* Synchronized, setters invisible, whatever... */
            storageTask.execute(null, onExceptionAction, onExceptionActionIsPersistent);

            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Task ID '%s' is invalid: %s".formatted(taskID, exception.getMessage()));
        }
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
        return this.storageTree.retrievestorageRootNode();
    }
}
