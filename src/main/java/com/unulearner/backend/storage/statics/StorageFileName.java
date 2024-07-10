package com.unulearner.backend.storage.statics;

import com.unulearner.backend.storage.exceptions.FileNameValidationException;
import com.unulearner.backend.storage.exceptions.FileNameGenerationException;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public final class StorageFileName {

    // Define the maximum length for the file name
    private static final int MAX_LENGTH = 128;

    // Define a regex pattern for invalid characters (including control characters and Unix reserved characters)
    private static final Pattern INVALID_CHARS_PATTERN = Pattern.compile("[<>:\"/\\\\|?*\\p{Cntrl}]");

    // List of reserved file names in Unix-like and Windows systems
    private static final String[] OS_RESERVED_NAMES = {
        ".", "..", "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    };
 
    /**
     * Validates file name against all possible patterns that an OS could find invalid
     * @param targetName name to validate
     * @return validated name in case of success
     * @throws FileNameValidationException 
     */
    public static String validateFileName(String targetName) throws FileNameValidationException {
        final String validatedName;

        // Check if the file name is null, empty, blank
        if (targetName == null || targetName.isEmpty() || (validatedName = targetName.trim()).isBlank()) {
            throw new FileNameValidationException("File name is null or empty.");
        }

        // Check if the file name exceeds the maximum length
        if (validatedName.length() > MAX_LENGTH) {
            throw new FileNameValidationException("File name exceeds the maximum length of " + MAX_LENGTH + " characters.");
        }

        // Check for invalid characters
        Matcher matcher = INVALID_CHARS_PATTERN.matcher(validatedName);
        if (matcher.find()) {
            throw new FileNameValidationException("File name contains invalid character: '" + matcher.group().charAt(0) + "'");
        }

        // Check for trailing spaces or periods
        if (validatedName.endsWith(" ") || validatedName.endsWith(".")) {
            throw new FileNameValidationException("File name ends with an invalid character: '" + validatedName.charAt(validatedName.length() - 1) + "'");
        }

        // Check for system-specific reserved file names
        for (String reservedName : OS_RESERVED_NAMES) {
            if (validatedName.equalsIgnoreCase(reservedName)) {
                throw new FileNameValidationException("File name is an OS reserved name.");
            }
        }

        return validatedName;
    }
 

    // Define pattern to break up a file name (base name, copy (N), extension, and so on...)
    private static final Pattern FILE_NAME_DIVIDER_PATTERN = Pattern.compile("^(\\.)?(.+?)( \\(\\d+\\))?(\\.\\w+)?$");

    /**
     * Breaks the provided name to its core parts and appends or increments the already existing (N) to it (N being the number of the copy)
     * @param targetName name to generate a new from
     * @return an (N) appended name of the provided name
     * @throws FileNameGenerationException 
     */
    public static String findNextAvailableFileName(String targetName) throws FileNameGenerationException {
        final Matcher matcher = FILE_NAME_DIVIDER_PATTERN.matcher(targetName);

        String retVal = null;
        String baseDot = null;
        String baseName = null;
        String copyNumber = null;
        String fileExtension = null;
        Integer baseNameModifier = 1;

        if (matcher.matches()) {
            baseDot = matcher.group(1);
            baseName = matcher.group(2);
            copyNumber = matcher.group(3);
            fileExtension = matcher.group(4);

            // Combine the dot from the dotfiles and the basename
            if (baseDot != null && !baseDot.isEmpty()) {
                baseName = baseDot + baseName;
            }

            // Remove parentheses from copyNumber and parse it to Integer
            if (copyNumber != null && !copyNumber.isEmpty()) {
                baseNameModifier = Integer.parseInt(copyNumber.substring(2, copyNumber.length() - 1));
            }

            // Remove the dot preceding the file extension
            if (fileExtension != null && !fileExtension.isEmpty()) {
                fileExtension = fileExtension.substring(1);
            }
        } else {
            throw new FileNameGenerationException("File name couldn't be parsed.");
        }

        // Assemble the file name from the parts available
        if (fileExtension == null || fileExtension.isEmpty()) {
            retVal = "%s (%s)".formatted(baseName, baseNameModifier);
        } else {
            retVal = "%s (%s).%s".formatted(baseName, baseNameModifier, fileExtension);
        }

        if (retVal == null || retVal.isEmpty() || retVal.isBlank()) {
            throw new FileNameGenerationException("File name generation was unsuccessful.");
        }

        return retVal.trim();
    }
}
