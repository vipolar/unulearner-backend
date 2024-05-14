package com.unulearner.backend.storage.service;

import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.responses.StorageServiceResponse;

public interface StorageService {
    /* FILES */
    public StorageServiceResponse createFileStorageTreeNode(MultipartFile newFile, String fileDescription, UUID destinationDirectoryID) throws Exception;
    public StorageServiceResponse updateFileStorageTreeNode(UUID targetFileID, String updatedName, String updatedDescription) throws Exception;
    public StorageServiceResponse transferFileStorageTreeNode(UUID targetFileID, UUID destinationDirectoryID, Boolean persistOriginal) throws Exception;
    public StorageServiceResponse deleteFileStorageTreeNode(UUID targetFileID) throws Exception;
    public Resource downloadFileStorageTreeNode(UUID targetFileID) throws Exception;

    /* DIRECTORIES */
    public StorageServiceResponse createDirectoryStorageTreeNode(String directoryName, String directoryDescription, UUID destinationDirectoryID) throws Exception;
    public StorageServiceResponse updateDirectoryStorageTreeNode(UUID targetDirectoryID, String updatedName, String updatedDescription) throws Exception;
    public StorageServiceResponse transferDirectoryStorageTreeNode(UUID targetDirectoryID, UUID destinationDirectoryID, Boolean persistOriginal) throws Exception;
    public StorageServiceResponse deleteDirectoryStorageTreeNode(UUID targetDirectoryID) throws Exception;
    public StorageTreeNode downloadDirectoryStorageTreeNode(UUID targetDirectoryID) throws Exception;

    /* THE ONE AND THE ONLY ALL-ENCOMPASSING TASK RUNNER */
    public StorageServiceResponse executeStorageTask(UUID taskID, Boolean skipOnException, Boolean skipOnExceptionIsPersistent, String onExceptionAction, Boolean onExceptionActionIsPersistent, Boolean cancelTaskExecution) throws Exception;
    
    /* THE REST */
    public StorageTreeNode downloadStorageTreeRootNode() throws Exception;
}
