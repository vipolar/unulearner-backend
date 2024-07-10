package com.unulearner.backend.storage.extensions;

import java.nio.file.Path;
import java.nio.file.Files;

import java.net.URI;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.net.MalformedURLException;

public class NodePath {
    private final Path rootPath;
    private final Path path;

    private NodePath(Path rootPath, Path path) {
        this.rootPath = rootPath;
        this.path = path;
    }

    public NodePath(Path path) {
        this.rootPath = path;
        this.path = path;
    }

    public Path getPath() {
        return this.path;
    }

    public Path getRootPath() {
        return this.rootPath;
    }

    public Path getFileName() {
        return this.path.getFileName();
    }

    public Path getRelativePath() {
        return this.rootPath.relativize(path);
    }

    public NodePath resolve(String other) {
        return new NodePath(this.rootPath, this.path.resolve(other));
    }

    public NodePath resolve(Path other) {
        return new NodePath(this.rootPath, this.path.resolve(other));
    }

    public NodePath resolveSibling(String other) {
        return new NodePath(this.rootPath, this.path.resolveSibling(other));
    }

    public NodePath resolveSibling(Path other) {
        return new NodePath(this.rootPath, this.path.resolveSibling(other));
    }

    public NodePath resolveFromRoot(Path other) throws Exception {
        final Path absoluteRoot = this.rootPath.toAbsolutePath();
        final Path absoluteOther = other.toAbsolutePath();

        /* TODO: error handling */
        final Path relativeOther = absoluteRoot.relativize(absoluteOther);
        final Path finalOther = this.rootPath.resolve(relativeOther);

        /* We jump through so many hoops because we don't know if root is absolute or not (both is supported) */
        return new NodePath(this.rootPath, finalOther);
    }

    /* TODO: handle symlinks */

    /**
     * @return true if the node is valid
     */
    public Boolean isValidNode() {
        return this.validateNodePath(true, true, false);
    }

    /**
     * @param allowSymbolicLinks whether to allow symbolic links or not (false by default)
     * @return true if the node is valid
     */
    public Boolean isValidNode(Boolean allowSymbolicLinks) {
        return this.validateNodePath(true, true, allowSymbolicLinks == null ? false : allowSymbolicLinks);
    }

    /**
     * @return true if the node is a valid file
     */
    public Boolean isValidFile() {
        return this.validateNodePath(true, false, false);
    }

    /**
     * @param allowSymbolicLinks whether to allow symbolic links or not (false by default)
     * @return true if the node is a valid file
     */
    public Boolean isValidFile(Boolean allowSymbolicLinks) {
        return this.validateNodePath(true, false, allowSymbolicLinks == null ? false : allowSymbolicLinks);
    }

    /**
     * @return true if the node is a valid directory
     */
    public Boolean isValidDirectory() {
        return this.validateNodePath(false, true, false);
    }

    /**
     * @param allowSymbolicLinks whether to allow symbolic links or not (false by default)
     * @return true if the node is a valid directory
     */
    public Boolean isValidDirectory(Boolean allowSymbolicLinks) {
        return this.validateNodePath(false, true, allowSymbolicLinks == null ? false : allowSymbolicLinks);
    }

    /**
     * This one does the actual stuff...
     */
    private Boolean validateNodePath(Boolean validateFile, Boolean validateDirectory, Boolean allowSymbolicLinks) {
        if (!Files.exists(this.path)) {
            return false;
        }

        if (allowSymbolicLinks == false && Files.isSymbolicLink(this.path)) {
            return false;
        }

        if (validateDirectory && Files.isDirectory(this.path)) {
            if (!Files.isReadable(this.path) || !Files.isWritable(this.path)) {
                return false;
            }
        }

        if (validateFile && !Files.isDirectory(this.path)) {
            final URI targetURI = this.path.toUri();
            if (targetURI == null) {
                return false;
            }

            try {
                final Resource targetResource = new UrlResource(targetURI);
                if (!targetResource.isReadable()) {
                    return false;
                }
            } catch (MalformedURLException exception) {
                return false;
            }
        }

        return true;
    }


    /*
    public boolean isFileHidden() {
        try {
            return Files.isHidden(this.path);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    } */

    /*
    public String getFileNameWithoutExtension() {
        String fileName = this.path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    } */
}
