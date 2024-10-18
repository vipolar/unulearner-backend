package com.unulearner.backend.storage;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.core.io.UrlResource;
import org.springframework.context.annotation.Scope;
import org.springframework.web.multipart.MultipartFile;

import com.unulearner.backend.security.user.JWTCredentials;
import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.tasks.StorageTaskBase;
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
public class Storage {
    final private StorageTree storageTree;
    final private StorageTasksMap storageTasksMap;
    final private JWTCredentials userJWTCredentials;

    private Storage(StorageTree storageTree, StorageTasksMap storageTasksMap, JWTCredentials userJWTCredentials) {
        this.storageTree = storageTree;
        this.storageTasksMap = storageTasksMap;
        this.userJWTCredentials = userJWTCredentials;
    }

    //**********************************************************//
    //*                                                        *//
    //*   From here on, it be all about THEM single files!     *//
    //*                                                        *//
    //**********************************************************//

    public StorageServiceResponse createFileStorageNode(MultipartFile newFile, String fileDescription, UUID destinationDirectoryID) throws Exception {
        final StorageNode destinationStorageNode = this.storageTree.retrieveStorageNode(destinationDirectoryID);
        if (destinationStorageNode == null || !destinationStorageNode.isDirectory()) {
            throw new StorageServiceException("Destination directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString()));
        }

        try {
            final StorageTaskCreateNode storageTask = new StorageTaskCreateNode(this.storageTree, newFile, fileDescription, destinationStorageNode, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("File upload task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse updateFileStorageNode(UUID targetFileID, String updatedName, String updatedDescription) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetFileID);
        if (targetStorageNode == null || targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target file ID '%s' is invalid!".formatted(targetFileID.toString()));
        }

        try {
            final StorageTaskUpdateNode storageTask = new StorageTaskUpdateNode(this.storageTree, updatedName, targetStorageNode, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("File update task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse transferFileStorageNode(UUID targetFileID, UUID destinationDirectoryID, Boolean persistOriginal) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetFileID);
        if (targetStorageNode == null || targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target file ID '%s' is invalid!".formatted(targetFileID.toString()));
        }

        final StorageNode destinationStorageNode = this.storageTree.retrieveStorageNode(destinationDirectoryID);
        if (destinationStorageNode == null || !destinationStorageNode.isDirectory()) {
            throw new StorageServiceException("Destination Directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString()));
        }

        try {
            final StorageTaskTransferNode storageTask = new StorageTaskTransferNode(this.storageTree, targetStorageNode, destinationStorageNode, persistOriginal, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("File %s task initialization failed: %s".formatted(persistOriginal ? "copy" : "move", exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse deleteFileStorageNode(UUID targetFileID) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetFileID);
        if (targetStorageNode == null || targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target File ID '%s' is invalid!".formatted(targetFileID.toString()));
        }

        try {
            final StorageTaskDestroyNode storageTask = new StorageTaskDestroyNode(this.storageTree, targetStorageNode, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("File removal task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public Resource downloadFileStorageNode(UUID targetFileID) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetFileID);
        if (targetStorageNode == null || targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target File ID '%s' is invalid!".formatted(targetFileID.toString()));
        }

        try {
            final URI targetURI = targetStorageNode.getNodePath().getPath().toUri();
            if (targetURI == null) {
                throw new StorageServiceException("File '%s' could not be reached!".formatted(targetStorageNode.getOnDiskName()));
            }

            final Resource targetResource = new UrlResource(targetURI);
            if (targetResource == null || !targetResource.isReadable()) {
                throw new StorageServiceException("File '%s' could not be read!".formatted(targetStorageNode.getOnDiskName()));
            }

            return targetResource;
        } catch (Exception retrieveResourceFromStorageException) {
            throw new StorageServiceException("Failed to retrieve '%s' input stream from permanent storage!".formatted(targetStorageNode.getOnDiskName(), retrieveResourceFromStorageException));
        }
    }

    //*********************************************************//
    //*                                                       *//
    //*   From here on, it be all about THEM directories!     *//
    //*                                                       *//
    //*********************************************************//

    public StorageServiceResponse createDirectoryStorageNode(String directoryName, String directoryDescription, UUID destinationDirectoryID) throws Exception {
        final StorageNode destinationStorageNode = this.storageTree.retrieveStorageNode(destinationDirectoryID);
        if (destinationStorageNode == null || !destinationStorageNode.isDirectory()) {
            throw new StorageServiceException("Destination directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString()));
        }

        this.userJWTCredentials.hasWritePermission();

        try {
            final StorageTaskCreateNode storageTask = new StorageTaskCreateNode(this.storageTree, directoryName, directoryDescription, destinationStorageNode, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Directory creation task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse updateDirectoryStorageNode(UUID targetDirectoryID, String updatedName, String updatedDescription) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetDirectoryID);
        if (targetStorageNode == null || !targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target directory ID '%s' is invalid!".formatted(targetDirectoryID.toString()));
        }

        try {
            final StorageTaskUpdateNode storageTask = new StorageTaskUpdateNode(this.storageTree, updatedName, targetStorageNode, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Directory update task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse transferDirectoryStorageNode(UUID targetDirectoryID, UUID destinationDirectoryID, Boolean persistOriginal) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetDirectoryID);
        if (targetStorageNode == null || !targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target directory ID '%s' is invalid!".formatted(targetDirectoryID.toString()));
        }

        final StorageNode destinationStorageNode = this.storageTree.retrieveStorageNode(destinationDirectoryID);
        if (destinationStorageNode == null || !destinationStorageNode.isDirectory()) {
            throw new StorageServiceException("Destination directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString()));
        }

        try {
            final StorageTaskTransferNode storageTask = new StorageTaskTransferNode(this.storageTree, targetStorageNode, destinationStorageNode, persistOriginal, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Directory %s task initialization failed: %s".formatted(persistOriginal ? "copy" : "move", exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse deleteDirectoryStorageNode(UUID targetDirectoryID) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetDirectoryID);
        if (targetStorageNode == null || !targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target directory ID '%s' is invalid!".formatted(targetDirectoryID.toString()));
        }

        try {
            final StorageTaskDestroyNode storageTask = new StorageTaskDestroyNode(this.storageTree, targetStorageNode, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Directory removal task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageNode downloadDirectoryStorageNode(UUID targetDirectoryID) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetDirectoryID);
        if (targetStorageNode == null || !targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target directory ID '%s' is invalid!".formatted(targetDirectoryID.toString()));
        }

        return targetStorageNode;
    }

    /**
     * @return
     * @throws Exception
     */
    public StorageNode downloadRootDirectoryStorageNode() throws Exception {
        return this.storageTree.retrieveRootStorageNode();
    }

    //***************************************************//
    //*                                                 *//
    //*   From here on, it be all about THEM tasks!     *//
    //*                                                 *//
    //***************************************************//

    public StorageServiceResponse executeStorageTask(UUID taskID, Map<String, Object> taskParameters) throws Exception {
        try {
            if (taskID == null) {
                throw new StorageServiceException("Task ID cannot be null!".formatted());
            }

            final StorageTaskBase storageTask = this.storageTasksMap.getStorageTask(taskID);

            if (storageTask == null) {
                throw new StorageServiceException("Task under the ID could not be retrieved".formatted());
            }

            /* Synchronized, setters invisible, etc. */
            storageTask.execute(taskParameters);

            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Task ID '%s' is invalid: %s".formatted(taskID, exception.getMessage()));
        }
    }
}
