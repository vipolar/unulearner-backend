package com.unulearner.backend.services.files.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FilesStorageService {

    public FilesStorageNode saveFile(MultipartFile file, Long parentId, String description) throws Exception;
    public Resource getFile(Long fileId) throws Exception;
    public void deleteFile(Long fileId) throws Exception;

    public FilesStorageNode saveDirectory(Long parentId, String directory, String description) throws Exception;
    public FilesStorageNode getDirectory(Long directoryId, Boolean checkHealth, Boolean checkOrphans) throws Exception;
    public void deleteDirectory(Long directoryId) throws Exception;

}
