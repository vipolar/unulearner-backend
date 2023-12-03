package com.unulearner.backend.services.files.storage;

import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.unulearner.backend.services.files.storage.tree.TreeRoot;

public interface FilesStorageService {

    public void save(MultipartFile file, String directory);

    public TreeRoot loadAll(String directory) throws IOException;

    public Resource load(String filename);

    public void deleteAll();
}
