package com.unulearner.backend.services.files.storage.tree;

import java.util.Optional;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import com.unulearner.backend.services.files.storage.FilesStorageServiceController;

@Component
public class TreeBuilderService {
    public TreeRoot buildDirectoryTree(TreeRoot root, String metadataFileName) throws IOException {
        Path rootPath = root.getAbsolutePath();
        TreeDirectoryNode rootDirectory = root.getDirectory();

        Files.walk(rootPath, Integer.MAX_VALUE)
                .filter(currentPath -> !currentPath.equals(rootPath) && Files.isRegularFile(currentPath) && !currentPath.getFileName().toString().equals(metadataFileName))
                .forEach(currentPath -> addPathToTree(rootDirectory, rootPath, currentPath));

        return root;
    }

    private void addPathToTree(TreeDirectoryNode root, Path rootPath, Path path) {
        Path relativePath = rootPath.relativize(path);
        TreeDirectoryNode currentNode = root;

        for (int i = 0; i < relativePath.getNameCount() - 1; i++) {
            String directoryName = relativePath.getName(i).toString();
            Optional<TreeDirectoryNode> existingNode = currentNode.getDirectories()
                    .stream()
                    .filter(node -> node.getName().equals(directoryName))
                    .findFirst();

            if (existingNode.isPresent()) {
                currentNode = existingNode.get();
            } else {
                TreeDirectoryNode newTreeDirectoryNode = new TreeDirectoryNode(directoryName);
                currentNode.getDirectories().add(newTreeDirectoryNode);
                currentNode = newTreeDirectoryNode;
            }
        }

        String filename = relativePath.getFileName().toString();
        String url = MvcUriComponentsBuilder
                .fromMethodName(FilesStorageServiceController.class, "getFile", relativePath.toString())
                .build().toString();

        currentNode.getFiles().add(new TreeFileNode(filename, url));
    }
}
