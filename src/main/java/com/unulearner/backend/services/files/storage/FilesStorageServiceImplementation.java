package com.unulearner.backend.services.files.storage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.util.stream.Stream;
import java.net.MalformedURLException;
import java.nio.file.FileAlreadyExistsException;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.core.io.UrlResource;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;

import com.unulearner.backend.configuration.properties.StorageProperties;

@Service
public class FilesStorageServiceImplementation implements FilesStorageService {

    private String root;
    private Path rootPath;

    @Autowired
    public FilesStorageServiceImplementation(StorageProperties storageProperties) {
        this.root = storageProperties.getRootDirectory();
        this.rootPath = Paths.get(root);
    }

    public void save(MultipartFile file, String directory) {
        String saveURI = this.root + directory;
        Path savePath = Paths.get(saveURI);

        try {
            Files.createDirectories(savePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize folder for upload!");
        }

        try {
            Files.copy(file.getInputStream(), savePath.resolve(file.getOriginalFilename()));
        } catch (Exception e) {
            if (e instanceof FileAlreadyExistsException) {
                throw new RuntimeException("A file of that name already exists.");
            }

            throw new RuntimeException(e.getMessage());
        }
    }

    public Resource load(String filename) {
        try {
            Path file = rootPath.resolve(filename);
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read the file!");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }

    public Stream<Path> loadAll() {
        try {
            return Files.walk(this.rootPath, 1).filter(path -> !path.equals(this.rootPath)).map(this.rootPath::relativize);
        } catch (IOException e) {
            throw new RuntimeException("Could not load the files!");
        }
    }

    public void deleteAll() {
        FileSystemUtils.deleteRecursively(rootPath.toFile());
    }
}