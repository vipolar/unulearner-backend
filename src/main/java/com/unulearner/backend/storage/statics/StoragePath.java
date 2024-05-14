package com.unulearner.backend.storage.statics;

import java.net.URI;

import java.nio.file.Path;
import java.nio.file.Files;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.net.MalformedURLException;

public final class StoragePath {
    /**
     * @param targetPath path of the node to be validated
     * @return true if the node is valid
     */
    public static Boolean isValidNode(Path targetPath) {
        return StoragePath.validateTargetPath(targetPath, true, true, false);
    }

    /**
     * @param targetPath path of the node to be validated
     * @param allowSymbolicLinks whether to allow symbolic links or not (false by default)
     * @return true if the node is valid
     */
    public static Boolean isValidNode(Path targetPath, Boolean allowSymbolicLinks) {
        return StoragePath.validateTargetPath(targetPath, true, true, allowSymbolicLinks == null ? false : allowSymbolicLinks);
    }

    /**
     * @param targetPath path of the node to be validated as a file
     * @return true if the node is a valid file
     */
    public static Boolean isValidFile(Path targetPath) {
        return StoragePath.validateTargetPath(targetPath, true, false, false);
    }

    /**
     * @param targetPath path of the node to be validated as a file
     * @param allowSymbolicLinks whether to allow symbolic links or not (false by default)
     * @return true if the node is a valid file
     */
    public static Boolean isValidFile(Path targetPath, Boolean allowSymbolicLinks) {
        return StoragePath.validateTargetPath(targetPath, true, false, allowSymbolicLinks == null ? false : allowSymbolicLinks);
    }

    /**
     * @param targetPath path of the node to be validated as a directory
     * @return true if the node is a valid directory
     */
    public static Boolean isValidDirectory(Path targetPath) {
        return StoragePath.validateTargetPath(targetPath, false, true, false);
    }

    /**
     * @param targetPath path of the node to be validated as a directory
     * @param allowSymbolicLinks whether to allow symbolic links or not (false by default)
     * @return true if the node is a valid directory
     */
    public static Boolean isValidDirectory(Path targetPath, Boolean allowSymbolicLinks) {
        return StoragePath.validateTargetPath(targetPath, false, true, allowSymbolicLinks == null ? false : allowSymbolicLinks);
    }

    /**
     * This one actually does the stuff...
     */
    private static Boolean validateTargetPath(Path targetPath, Boolean validateFile, Boolean validateDirectory, Boolean allowSymbolicLinks) {
        if (targetPath == null) {
            return false;
        }

        if (!Files.exists(targetPath)) {
            return false;
        }

        if (allowSymbolicLinks == false && Files.isSymbolicLink(targetPath)) {
            return false;
        }

        if (validateDirectory && Files.isDirectory(targetPath)) {
            if (!Files.isReadable(targetPath) || !Files.isWritable(targetPath)) {
                return false;
            }
        }

        if (validateFile && !Files.isDirectory(targetPath)) {
            final URI targetURI = targetPath.toUri();
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

    /**
     * To be called if file/directory transfer has failed due to a {@code FileAlreadyExistsException} and automatic rename of the file/directory is an acceptable option
     * @param targetName current name of the file/directory that is to be transfered
     * @param destinationPath destination of the file/directory to be transfered
     * @return an (N) appended name of the file/directory to be transfered - (N) being the number corresponding to the next available itteration of the file/directory name. {@code returns null if name could not be generated}
     */
    public static String findNextAvailableName(String targetName, Path destinationPath) {
        final Pattern pattern = Pattern.compile("^(\\.)?(.+?)( \\(\\d+\\))?(\\.\\w+)?$");
        final Matcher matcher = pattern.matcher(targetName);

        String baseDot = null;
        String baseName = null;
        String copyNumber = null;
        String fileExtension = null;
        Integer baseNameModifier = 1;
        String possibleNewName = null;

        if (matcher.matches()) {
            baseDot = matcher.group(1);
            baseName = matcher.group(2);
            copyNumber = matcher.group(3);
            fileExtension = matcher.group(4);

            if (baseDot != null && !baseDot.isEmpty()) {
                // Combine the dot from the dotfiles with the basename
                baseName = baseDot + baseName;
            }

            if (copyNumber != null && !copyNumber.isEmpty()) {
                // Remove parentheses from copyNumber and parse it to Integer
                baseNameModifier = Integer.parseInt(copyNumber.substring(2, copyNumber.length() - 1));
            }

            if (fileExtension != null && !fileExtension.isEmpty()) {
                // Remove the dot preceding the file extension
                fileExtension = fileExtension.substring(1);
            }
        } else {
            return null;
        }

        if (fileExtension == null || fileExtension.isEmpty()) {
            possibleNewName = "%s (%s)".formatted(baseName, baseNameModifier);
        } else {
            possibleNewName = "%s (%s).%s".formatted(baseName, baseNameModifier, fileExtension);
        }

        if (possibleNewName == null || (possibleNewName = possibleNewName.trim()).isEmpty()) {
            return null;
        }

        return possibleNewName;
    }
}
