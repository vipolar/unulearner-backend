package com.unulearner.backend.services.files.storage.tree;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TreeRoot {

    @JsonIgnore
    private Path absolutePath;

    @JsonIgnore
    private String metadataFileName;

    private TreeMetadata metadata;
    private TreeDirectoryNode directory;
    

    public Path getAbsolutePath() {
        return absolutePath;
    }

    public void setAbsolutePath(Path absolutePath) {
        this.absolutePath = absolutePath;
    }

    public String getMetadataFileName() {
        return metadataFileName;
    }

    public void setMetadataFileName(String metadataFileName) {
        this.metadataFileName = metadataFileName;
    }

    public TreeMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(TreeMetadata metadata) {
        this.metadata = metadata;
    }

    public TreeDirectoryNode getDirectory() {
        return directory;
    }

    public void setDirectory(TreeDirectoryNode directory) {
        this.directory = directory;
    }

    public TreeRoot(Path rootPath, String metadataFileName) throws IOException {  
        // Passing Path and then toString()-ing it hides the parent directories to the root
        this.directory = new TreeDirectoryNode(rootPath.getFileName().toString());
        this.metadataFileName = metadataFileName;
        this.absolutePath = rootPath;

        // Try to load metadata from the .metadata file
        Path metadataFile = rootPath.resolve(this.metadataFileName);

        if (Files.exists(metadataFile)) {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            this.metadata = mapper.readValue(metadataFile.toFile(), TreeMetadata.class);
        } else {
            // Set values to null to signal error!
            // We only get here if the directory was created manually, outside of the backend itself!
            this.metadata = new TreeMetadata();

            this.metadata.setName(null);
            this.metadata.setCreated(null);
            this.metadata.setPurpose(null);   
        }
    }
}
