package com.unulearner.backend.services.files.storage;

import java.nio.file.Path;
import java.util.stream.Stream;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FilesStorageService {

    public void save(MultipartFile file, String directory);

    public Resource load(String filename);

    public Stream<Path> loadAll();

    public void deleteAll();
}
