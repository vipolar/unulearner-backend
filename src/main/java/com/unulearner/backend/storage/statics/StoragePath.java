package com.unulearner.backend.storage.statics;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

public final class StoragePath {
    public static Boolean isValid(Path targetPath) throws Exception {
        return validateTargetPath(targetPath, true, true, true);
    }

    public static Boolean isValid(Path targetPath, Boolean allowSymbolicLinks) throws Exception {
        return validateTargetPath(targetPath, true, true, allowSymbolicLinks);
    }

    public static Boolean isValidFile(Path targetPath) throws Exception {
        return validateTargetPath(targetPath, true, false, true);
    }

    public static Boolean isValidFile(Path targetPath, Boolean allowSymbolicLinks) throws Exception {
        return validateTargetPath(targetPath, true, false, allowSymbolicLinks);
    }

    public static Boolean isValidDirectory(Path targetPath) throws Exception {
        return validateTargetPath(targetPath, false, true, true);
    }

    public static Boolean isValidDirectory(Path targetPath, Boolean allowSymbolicLinks) throws Exception {
        return validateTargetPath(targetPath, false, true, allowSymbolicLinks);
    }

    private static Boolean validateTargetPath(Path targetPath, Boolean validateFile, Boolean validateDirectory, Boolean allowSymbolicLinks) throws Exception {
        if (targetPath == null) {
            return false;
        }

        if (!Files.exists(targetPath)) {
            return false;
        }

        if (allowSymbolicLinks == false && Files.isSymbolicLink(targetPath)) {
            return false;
        }

        if (validateFile && !Files.isDirectory(targetPath)) {
            final URI targetURI = targetPath.toUri();
            if (targetURI == null) {
                return false;
            }

            final Resource targetResource = new UrlResource(targetURI);
            if (!targetResource.isReadable()) {
                return false;
            }

            return true;
        }

        if (validateDirectory && Files.isDirectory(targetPath)) {
            if (!Files.isReadable(targetPath) || !Files.isWritable(targetPath)) {
                return false;
            }

            return true;
        }

        return false;
    }
}
