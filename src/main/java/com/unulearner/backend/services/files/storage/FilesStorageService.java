package com.unulearner.backend.services.files.storage;

import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FilesStorageService {

    public FilesStorageNode saveFile(MultipartFile file, UUID parentId, String description) throws Exception;
    public FilesStorageNode copyFile(UUID fileId, UUID destinationId, String resolveConflictBy) throws Exception;
    public FilesStorageNode moveFile(UUID fileId, UUID destinationId, String resolveConflictBy) throws Exception;
    public FilesStorageNode editFile(UUID fileId, String fileName, String description) throws Exception;
    public Resource getFile(UUID fileId) throws Exception;
    public void deleteFile(UUID fileId) throws Exception;

    public FilesStorageNode saveDirectory(String directory, UUID parentId, String description) throws Exception;
//    public FilesStorageNode copyDirectory(UUID directoryId, UUID destinationId, String resolveConflictBy) throws Exception;
//    public FilesStorageNode moveDirectory(UUID directoryId, UUID destinationId, String resolveConflictBy) throws Exception;
    public FilesStorageNode editDirectory(UUID directoryId, String directoryName, String description) throws Exception;
    public FilesStorageNode getDirectory(UUID directoryId, Boolean diagnostics, Boolean recovery) throws Exception;
    public void deleteDirectory(UUID directoryId) throws Exception;

}
