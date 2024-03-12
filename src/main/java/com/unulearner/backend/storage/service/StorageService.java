package com.unulearner.backend.storage.service;

import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.responses.StorageServiceResponse;

public interface StorageService {
    /* FILES */
    public StorageTreeNode createFileStorageTreeNode(MultipartFile newFile, String fileDescription, UUID destinationDirectoryID, String onConflict) throws Exception;
    public StorageTreeNode updateFileStorageTreeNode(MultipartFile updatedFile, String updatedName, String updatedDescription, UUID targetFileID, String onConflict) throws Exception;
    public StorageTreeNode transferFileStorageTreeNode(UUID targetFileID, UUID destinationDirectoryID, Boolean persistOriginalNode, String onConflict) throws Exception;
    public Resource downloadFileStorageTreeNode(UUID targetFileID) throws Exception;
    public void deleteFileStorageTreeNode(UUID targetFileID) throws Exception;

    /* DIRECTORIES */
    public StorageTreeNode createDirectoryStorageTreeNode(String directoryName, String directoryDescription, UUID destinationDirectoryID, String onConflict) throws Exception;
    public StorageTreeNode updateDirectoryStorageTreeNode(String updatedName, String updatedDescription, UUID targetDirectoryID, String onConflict) throws Exception ;
    public StorageTreeNode transferDirectoryStorageTreeNode(UUID targetDirectoryID, UUID destinationDirectoryID, Boolean persistOriginalNode, String onConflict) throws Exception;
    public StorageTreeNode downloadDirectoryStorageTreeNode(UUID targetDirectoryID) throws Exception;
    //public void deleteDirectoryStorageTreeNode(UUID targetFileID) throws Exception;
    public StorageServiceResponse deleteDirectoryStorageTreeNode(UUID targetDirectoryID, UUID taskID, String taskOnExceptionAction, Boolean taskOnExceptionActionIsPersistent) throws Exception;

    /* THE REST */
    public StorageTreeNode downloadStorageTreeRootNode() throws Exception;
}
