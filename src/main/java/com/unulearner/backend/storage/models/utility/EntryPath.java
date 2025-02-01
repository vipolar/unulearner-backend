package com.unulearner.backend.storage.models.utility;

import java.net.URI;

import java.nio.file.Path;
import java.nio.file.Files;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.net.MalformedURLException;

public class EntryPath {
    private final Path rootPath;
    private final Path path;

    private EntryPath(Path rootPath, Path path) {
        this.rootPath = rootPath;
        this.path = path;
    }

    public EntryPath(Path path) {
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

    public EntryPath resolve(String other) {
        return new EntryPath(this.rootPath, this.path.resolve(other));
    }

    public EntryPath resolve(Path other) {
        return new EntryPath(this.rootPath, this.path.resolve(other));
    }

    public EntryPath resolveSibling(String other) {
        return new EntryPath(this.rootPath, this.path.resolveSibling(other));
    }

    public EntryPath resolveSibling(Path other) {
        return new EntryPath(this.rootPath, this.path.resolveSibling(other));
    }

    public EntryPath resolveFromRoot(Path other) throws Exception {
        final Path absoluteRoot = this.rootPath.toAbsolutePath();
        final Path absoluteOther = other.toAbsolutePath();

        /* TODO: error handling */
        final Path relativeOther = absoluteRoot.relativize(absoluteOther);
        final Path finalOther = this.rootPath.resolve(relativeOther);

        /* We jump through so many hoops because we don't know if root is absolute or not (both is supported) */
        return new EntryPath(this.rootPath, finalOther);
    }

    /* TODO: handle symlinks */

    /**
     * @return true if the entry is valid
     */
    public Boolean isValid() {
        return this.validateEntryPath(true, true, false);
    }

    /**
     * @param allowSymbolicLinks whether to allow symbolic links or not (false by default)
     * @return true if the entry is valid
     */
    public Boolean isValid(Boolean allowSymbolicLinks) {
        return this.validateEntryPath(true, true, allowSymbolicLinks == null ? false : allowSymbolicLinks);
    }

    /**
     * @return true if the entry is a valid file
     */
    public Boolean isValidFile() {
        return this.validateEntryPath(true, false, false);
    }

    /**
     * @param allowSymbolicLinks whether to allow symbolic links or not (false by default)
     * @return true if the entry is a valid file
     */
    public Boolean isValidFile(Boolean allowSymbolicLinks) {
        return this.validateEntryPath(true, false, allowSymbolicLinks == null ? false : allowSymbolicLinks);
    }

    /**
     * @return true if the entry is a valid directory
     */
    public Boolean isValidDirectory() {
        return this.validateEntryPath(false, true, false);
    }

    /**
     * @param allowSymbolicLinks whether to allow symbolic links or not (false by default)
     * @return true if the entry is a valid directory
     */
    public Boolean isValidDirectory(Boolean allowSymbolicLinks) {
        return this.validateEntryPath(false, true, allowSymbolicLinks == null ? false : allowSymbolicLinks);
    }

    /**
     * This one does the actual stuff...
     */
    private Boolean validateEntryPath(Boolean validateFile, Boolean validateDirectory, Boolean allowSymbolicLinks) {
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        EntryPath other = (EntryPath) obj;
        return this.rootPath.equals(other.rootPath) && this.path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return 31 * rootPath.hashCode() + path.hashCode();
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
