package com.unulearner.backend.services.files.storage;

import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FilesStorageService {

    public FilesStorageNode saveFile(MultipartFile file, UUID parentId, String description) throws Exception;
    public FilesStorageNode editFile(UUID fileId, String fileName, String description) throws Exception;
    public Resource getFile(UUID fileId) throws Exception;
    public void deleteFile(UUID fileId) throws Exception;

    public FilesStorageNode saveDirectory(UUID parentId, String directory, String description) throws Exception;
    public FilesStorageNode editDirectory(UUID directoryId, String directoryName, String description) throws Exception;
    public FilesStorageNode getDirectory(UUID directoryId, Boolean diagnostics, Boolean recovery) throws Exception;
//    public FilesStorageNode moveDirectory(UUID sourceDirectoryId, UUID targetDirectoryId) throws Exception;
    public void deleteDirectory(UUID directoryId) throws Exception;

}
