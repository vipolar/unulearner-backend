package com.unulearner.backend.services.files.storage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.FileAlreadyExistsException;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.core.io.UrlResource;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.unulearner.backend.configuration.properties.StorageProperties;

import com.unulearner.backend.services.files.storage.tree.TreeBuilderService;
import com.unulearner.backend.services.files.storage.tree.TreeMetadata;
import com.unulearner.backend.services.files.storage.tree.TreeRoot;

@Service
public class FilesStorageServiceImplementation implements FilesStorageService {

    private TreeBuilderService treeBuilder;
    private String metadataFile;
    private Path rootPath;
    private String root;

    // @Autowired
    public FilesStorageServiceImplementation(TreeBuilderService treeBuilder, StorageProperties storageProperties) {
        this.metadataFile = storageProperties.getMetaDataFileName();
        this.root = storageProperties.getRootDirectory();
        this.rootPath = Paths.get(root);
        this.treeBuilder = treeBuilder;
    }

    public void save(MultipartFile file, String directory) {
        String saveURI = this.root + directory;
        Path savePath = Paths.get(saveURI);

        try {
            Path currentPath = Files.createDirectory(savePath);

            while (!currentPath.equals(this.rootPath) && !Files.exists(currentPath.resolve(this.metadataFile))) {
                TreeMetadata metadata = new TreeMetadata();

                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                String metadataContent = mapper.writeValueAsString(metadata);
                Files.write(currentPath.resolve(this.metadataFile), metadataContent.getBytes());

                currentPath = currentPath.getParent();
            }
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

    public TreeRoot loadAll(String directory) throws IOException {
        try {
            String rootPathString = this.root + directory;
            Path rootPath = Paths.get(rootPathString);

            TreeRoot root = new TreeRoot(rootPath, this.metadataFile);
            return treeBuilder.buildDirectoryTree(root, this.metadataFile);
        } catch (IOException e) {
            throw new RuntimeException("Could not build the directory tree!");
        }
    }

    public void deleteAll() {
        FileSystemUtils.deleteRecursively(rootPath.toFile());
    }
}