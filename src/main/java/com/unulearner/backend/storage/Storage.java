package com.unulearner.backend.storage;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.core.io.UrlResource;
import org.springframework.context.annotation.Scope;

import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.tasks.StorageTaskBase;
import com.unulearner.backend.storage.tasks.StorageTaskChmodNode;
import com.unulearner.backend.storage.tasks.StorageTaskChownNode;
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

    private Storage(StorageTree storageTree, StorageTasksMap storageTasksMap) {
        this.storageTree = storageTree;
        this.storageTasksMap = storageTasksMap;
    }

    //**********************************************************//
    //*                                                        *//
    //*   From here on, it be all about THEM single files!     *//
    //*                                                        *//
    //**********************************************************//

    public StorageServiceResponse createFileStorageNode(File newFile, StorageNode newStorageNode, UUID destinationDirectoryID) throws Exception {
        final StorageNode destinationStorageNode = this.storageTree.retrieveStorageNode(destinationDirectoryID);
        if (destinationStorageNode == null || !destinationStorageNode.isDirectory()) {
            throw new StorageServiceException("Destination directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString()));
        }

        try {
            final StorageTaskCreateNode storageTask = new StorageTaskCreateNode(this.storageTree, newFile, newStorageNode.setParent(destinationStorageNode), this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("File upload task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse updateFileStorageNode(StorageNode updatedStorageNode) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(updatedStorageNode.getId());
        if (targetStorageNode == null || targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target file ID '%s' is invalid!".formatted(updatedStorageNode.getId().toString()));
        }

        try {
            final StorageTaskUpdateNode storageTask = new StorageTaskUpdateNode(this.storageTree, updatedStorageNode, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("File update task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse chmodFileStorageNode(UUID targetFileID, Short flags, Boolean isRecursive) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetFileID);
        if (targetStorageNode == null || targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target file ID '%s' is invalid!".formatted(targetFileID));
        }

        try {
            final StorageTaskChmodNode storageTask = new StorageTaskChmodNode(this.storageTree, targetStorageNode, flags, isRecursive, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("File permission flags update task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse chownFileStorageNode(UUID targetFileID, UUID user, UUID group, Boolean isRecursive) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetFileID);
        if (targetStorageNode == null || targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target file ID '%s' is invalid!".formatted(targetFileID));
        }

        try {
            final StorageTaskChownNode storageTask = new StorageTaskChownNode(this.storageTree, targetStorageNode, user, group, isRecursive, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("File ownership change task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse renameFileStorageNode(UUID targetFileID, String newfileName) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetFileID);
        if (targetStorageNode == null || targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target file ID '%s' is invalid!".formatted(targetFileID));
        }

        if (newfileName == null || newfileName.isEmpty() || newfileName.isBlank()) {
            throw new StorageServiceException("New file name cannot be blank!".formatted());
        }

        try {
            final StorageTaskTransferNode storageTask = new StorageTaskTransferNode(this.storageTree, targetStorageNode, newfileName, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("File remane task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse transferFileStorageNode(UUID targetFileID, UUID destinationDirectoryID, String newDirectoryName, Boolean persistOriginal) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetFileID);
        if (targetStorageNode == null || targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target file ID '%s' is invalid!".formatted(targetFileID.toString()));
        }

        final StorageNode destinationStorageNode = this.storageTree.retrieveStorageNode(destinationDirectoryID);
        if (destinationStorageNode == null || !destinationStorageNode.isDirectory()) {
            throw new StorageServiceException("Destination Directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString()));
        }

        try {
            final StorageTaskTransferNode storageTask = new StorageTaskTransferNode(this.storageTree, targetStorageNode, destinationStorageNode, newDirectoryName, persistOriginal, this.storageTasksMap);
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
                throw new StorageServiceException("File '%s' could not be reached!".formatted(targetStorageNode.getName()));
            }

            final Resource targetResource = new UrlResource(targetURI);
            if (targetResource == null || !targetResource.isReadable()) {
                throw new StorageServiceException("File '%s' could not be read!".formatted(targetStorageNode.getName()));
            }

            return targetResource;
        } catch (Exception retrieveResourceFromStorageException) {
            throw new StorageServiceException("Failed to retrieve '%s' input stream from permanent storage!".formatted(targetStorageNode.getName(), retrieveResourceFromStorageException));
        }
    }

    //*********************************************************//
    //*                                                       *//
    //*   From here on, it be all about THEM directories!     *//
    //*                                                       *//
    //*********************************************************//

    public StorageServiceResponse createDirectoryStorageNode(StorageNode newStorageNode, UUID destinationDirectoryID) throws Exception {
        final StorageNode destinationStorageNode = this.storageTree.retrieveStorageNode(destinationDirectoryID);
        if (destinationStorageNode == null || !destinationStorageNode.isDirectory()) {
            throw new StorageServiceException("Destination directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString()));
        }

        try {
            final StorageTaskCreateNode storageTask = new StorageTaskCreateNode(this.storageTree, newStorageNode.setParent(destinationStorageNode), this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Directory creation task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse updateDirectoryStorageNode(StorageNode updatedStorageNode) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(updatedStorageNode.getId());
        if (targetStorageNode == null || !targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target directory ID '%s' is invalid!".formatted(updatedStorageNode.toString()));
        }

        try {
            final StorageTaskUpdateNode storageTask = new StorageTaskUpdateNode(this.storageTree, updatedStorageNode, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Directory update task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse chmodDirectoryStorageNode(UUID targetDirectoryID, Short flags, Boolean isRecursive) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetDirectoryID);
        if (targetStorageNode == null || !targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target directory ID '%s' is invalid!".formatted(targetDirectoryID));
        }

        try {
            final StorageTaskChmodNode storageTask = new StorageTaskChmodNode(this.storageTree, targetStorageNode, flags, isRecursive, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Directory permission flags update task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse chownDirectoryStorageNode(UUID targetDirectoryID, UUID user, UUID group, Boolean isRecursive) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetDirectoryID);
        if (targetStorageNode == null || !targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target directory ID '%s' is invalid!".formatted(targetDirectoryID));
        }

        try {
            final StorageTaskChownNode storageTask = new StorageTaskChownNode(this.storageTree, targetStorageNode, user, group, isRecursive, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Directory ownership change task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse renameDirectoryStorageNode(UUID targetDirectoryID, String newDirectoryName) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetDirectoryID);
        if (targetStorageNode == null || !targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target directory ID '%s' is invalid!".formatted(targetDirectoryID));
        }

        if (newDirectoryName == null || newDirectoryName.isEmpty() || newDirectoryName.isBlank()) {
            throw new StorageServiceException("New directory name cannot be blank!".formatted());
        }

        try {
            final StorageTaskTransferNode storageTask = new StorageTaskTransferNode(this.storageTree, targetStorageNode, newDirectoryName, this.storageTasksMap);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Directory update task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    public StorageServiceResponse transferDirectoryStorageNode(UUID targetDirectoryID, UUID destinationDirectoryID, String newDirectoryName, Boolean persistOriginal) throws Exception {
        final StorageNode targetStorageNode = this.storageTree.retrieveStorageNode(targetDirectoryID);
        if (targetStorageNode == null || !targetStorageNode.isDirectory()) {
            throw new StorageServiceException("Target directory ID '%s' is invalid!".formatted(targetDirectoryID.toString()));
        }

        final StorageNode destinationStorageNode = this.storageTree.retrieveStorageNode(destinationDirectoryID);
        if (destinationStorageNode == null || !destinationStorageNode.isDirectory()) {
            throw new StorageServiceException("Destination directory ID '%s' is invalid!".formatted(destinationDirectoryID.toString()));
        }

        try {
            final StorageTaskTransferNode storageTask = new StorageTaskTransferNode(this.storageTree, targetStorageNode, destinationStorageNode, newDirectoryName, persistOriginal, this.storageTasksMap);
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
            return storageTask.execute(taskParameters);
        } catch (Exception exception) {
            throw new StorageServiceException("Task ID '%s' is invalid: %s".formatted(taskID, exception.getMessage()));
        }
    }
}
