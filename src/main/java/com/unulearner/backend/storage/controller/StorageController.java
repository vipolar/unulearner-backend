package com.unulearner.backend.storage.controller;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Controller;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.unulearner.backend.storage.model.Entry;
import com.unulearner.backend.storage.taskflow.Base;
import com.unulearner.backend.storage.taskflow.Create;
import com.unulearner.backend.storage.taskflow.Delete;
import com.unulearner.backend.storage.taskflow.Modify;
import com.unulearner.backend.storage.taskflow.Transfer;
import com.unulearner.backend.storage.taskflow.ModifyField;
import com.unulearner.backend.storage.taskflow.ModifyOwnership;
import com.unulearner.backend.storage.taskflow.ModifyPermissions;
import com.unulearner.backend.storage.taskflow.response.Response;
import com.unulearner.backend.storage.taskflow.dispatcher.TaskDispatch;

import com.unulearner.backend.storage.Storage;
import com.unulearner.backend.storage.config.StorageProperties;
import com.unulearner.backend.storage.exceptions.StorageControllerException;


@Controller
@RequestMapping(path = "/storage")
public class StorageController {
    private final Storage storage;
    private final TaskDispatch taskDispatch;
    private final StorageProperties storageProperties;
    private final Long PROCESS_ID = ProcessHandle.current().pid();
    private final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private final Boolean printStackTrace;

    public StorageController(Storage storage, TaskDispatch taskDispatch, StorageProperties storageProperties) {
        this.storage = storage;
        this.taskDispatch = taskDispatch;
        this.storageProperties = storageProperties;

        /* We'll be getting some storage properties here (mostly in regards to logging) */
        this.printStackTrace = this.storageProperties.getControllerPrintExceptionStackTrace();
    }

    /**
     * Executes a storage task identified by its UUID with the provided parameters.
     *
     * <p>This method retrieves the specified task, executes it using the given parameters, 
     * and returns the result of the execution. The response contains either a successful 
     * state with associated storage entry(ies) or an error message indicating failure.</p>
     *
     * @param taskUUID the UUID of the task to execute
     * @param parameters an optional map of parameters to be used during task execution
     * @return a {@link ResponseEntity} wrapping:
     *         <ul>
     *           <li>response containing the result of the task execution, 
     *           including the associated storage entry(ies)</li>
     *           <li>an error message if the task fails</li>
     *         </ul>
     * @throws Exception if the task cannot be retrieved, executed, or any other error occurs during the process
     */
    @GetMapping(value = "/exec/{taskUUID}")
    public ResponseEntity<?> exec(
        @PathVariable() UUID taskUUID,
        @RequestParam(required = false) Map<String, Object> parameters) {

        try {
            final Base task = this.taskDispatch.retrieveTask(taskUUID); task.execute(parameters);
            return new ResponseEntity<Response>(task.getResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            if (this.printStackTrace) exception.printStackTrace();
            return new ResponseEntity<String>("Task '%s' cannot be executed: %s".formatted(taskUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Retrieves a storage entry and its children, reflecting the file/directory tree structure.
     *
     * <p>If the provided UUID corresponds to a valid storage entry, the method returns the entry along 
     * with its children. If no UUID is provided (i.e., null), the method retrieves the root storage 
     * entry.</p>
     *
     * @param targetEntryUUID the UUID of the storage entry to retrieve; if null, retrieves the root entry
     * @return a {@link ResponseEntity} containing the requested storage entry along with its children, 
     *         or an error message with an appropriate HTTP status code if the retrieval fails
     * @throws StorageControllerException if the target UUID is invalid, does not point to a valid storage entry,
     * or any other errors encountered during the retrieval process
     */
    @GetMapping(value = {"/ls", "/ls/{targetEntryUUID}"})
    public ResponseEntity<?> ls(
        @PathVariable(required = false) UUID targetEntryUUID) {

        if (targetEntryUUID == null) {
            return new ResponseEntity<Entry>(this.storage.getRootEntry(), HttpStatus.OK);
        }

        try {
            final Entry targetEntry = this.storage.retrieveEntry(targetEntryUUID);
            if (targetEntry == null || !targetEntry.getEntryPath().isValid()) {
                throw new StorageControllerException("Invalid target UUID: %s".formatted(targetEntryUUID.toString()));
            }
    
            return new ResponseEntity<Entry>(targetEntry, HttpStatus.OK);
        } catch (Exception exception) {
            if (this.printStackTrace) exception.printStackTrace();
            return new ResponseEntity<String>("Directory '%s' cannot be traversed: %s".formatted(targetEntryUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Uploads a file to a specified destination directory and optionally renames it during the process.
     *
     * <p>This method saves the uploaded file (received as a {@link MultipartFile}) to the directory 
     * specified by its UUID. An optional new name can be provided to save the file under a different name 
     * than its original filename. The uploaded file is stored as a new storage entry.</p>
     *
     * @param destinationEntryUUID the UUID of the storage entry representing the directory where the file is to be saved
     * @param newFileName (optional) an alternative name under which the file will be saved on disk; if not provided, 
     *                    the original filename from the uploaded file will be used
     * @param multipartFile the file to be uploaded and saved on disk; this is required and should be sent as part of 
     *                      the request
     * @return a {@link ResponseEntity} containing the newly created storage entry reflecting the uploaded file, 
     *         or an error message with an appropriate HTTP status code if the operation fails
     * @throws StorageControllerException if the destination directory UUID is invalid, does not point to a valid directory, 
     *         or other issues occur during the file upload
     */
    @PostMapping(value = {"/scp/{destinationEntryUUID}", "/scp/{destinationEntryUUID}/{newFileName}"})
        public ResponseEntity<?> scp(
        @PathVariable UUID destinationEntryUUID,
        @PathVariable(required = false) String newFileName,
        @RequestParam(name = "file") MultipartFile multipartFile) {

        try {
            final Entry destinationEntry = this.storage.retrieveEntry(destinationEntryUUID);
            if (destinationEntry == null || !destinationEntry.getIsDirectory() || !destinationEntry.getEntryPath().isValidDirectory()) {
                throw new StorageControllerException("Invalid destination UUID: %s".formatted(destinationEntryUUID.toString()));
            }

            final File tempFile = new File(TEMP_DIR, "tmp-%d-%s-%s.tmp".formatted(PROCESS_ID, UUID.randomUUID().toString(), newFileName != null ? newFileName : multipartFile.getOriginalFilename()));
            multipartFile.transferTo(tempFile); /* Failure at this point is fatal to the request and it is an intended behavior! */

            final Create task = this.taskDispatch.createTask(Create.class).initialize(destinationEntry, newFileName != null ? newFileName : multipartFile.getOriginalFilename(), tempFile);
            return new ResponseEntity<Response>(task.getResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            if (this.printStackTrace) exception.printStackTrace();
            return new ResponseEntity<String>("Failed to upload file '%s' to '%s' directory! Error: %s".formatted(newFileName != null ? newFileName : multipartFile.getOriginalFilename(), destinationEntryUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Creates a new directory within the specified destination directory.
     *
     * <p>This method creates a new directory with the specified name inside the target destination 
     * directory identified by its UUID. The resulting directory is registered as a new storage entry.</p>
     *
     * @param destinationEntryUUID the UUID of the storage entry (directory) where the new directory will be created
     * @param newDirectoryName the name of the new directory to be created
     * @return a {@link ResponseEntity} containing the newly created storage entry reflecting the new directory, 
     *         or an error message with an appropriate HTTP status code if the operation fails
     * @throws StorageControllerException if the destination entry UUID is invalid, does not point to a valid directory, 
     *         or other issues occur during the directory creation
     */
    @PostMapping(value = "/mkdir/{destinationEntryUUID}/{newDirectoryName}")
    public ResponseEntity<?> mkdir(
        @PathVariable UUID destinationEntryUUID,
        @PathVariable(required = true) String newDirectoryName) {

        try {
            final Entry destinationEntry = this.storage.retrieveEntry(destinationEntryUUID);
            if (destinationEntry == null || !destinationEntry.getIsDirectory() || !destinationEntry.getEntryPath().isValidDirectory()) {
                throw new StorageControllerException("Invalid destination UUID: %s".formatted(destinationEntryUUID.toString()));
            }

            final Create task = this.taskDispatch.createTask(Create.class).initialize(destinationEntry, newDirectoryName);
            return new ResponseEntity<Response>(task.getResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            if (this.printStackTrace) exception.printStackTrace();
            return new ResponseEntity<String>("Failed to create new directory '%s' and add it to '%s' directory! Error: %s".formatted(newDirectoryName, destinationEntryUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Copies a file or directory to a new location, optionally renaming it.
     *
     * <p>This method creates a copy of the specified storage entry (file or directory) identified by its UUID 
     * and places it in the target destination directory, also identified by its UUID. If an optional new name 
     * is provided, the copy is saved under that name in the destination directory. The original file or directory 
     * remains unchanged.</p>
     *
     * @param targetEntryUUID the UUID of the storage entry (file or directory) to be copied
     * @param destinationEntryUUID the UUID of the destination directory where the copy will be placed
     * @param newNodeName (optional) a new name for the copy of the file or directory; 
     *                    if omitted, the original name is used
     * @return a {@link ResponseEntity} containing the newly created storage entry reflecting the copy of the 
     *         target file or directory (with the new name, if provided), or an error message with an appropriate 
     *         HTTP status code if the operation fails
     * @throws StorageControllerException if the target entry or destination entry UUID is invalid, 
     *         the destination is not a valid directory, or other issues occur
     */
    @PostMapping(value = {"/cp/{targetEntryUUID}/{destinationEntryUUID}", "/cp/{targetEntryUUID}/{destinationEntryUUID}/{newNodeName}"})
    public ResponseEntity<?> cp(
        @PathVariable UUID targetEntryUUID,
        @PathVariable UUID destinationEntryUUID,
        @PathVariable(required = false) String newNodeName) {

        try {
            final Entry targetEntry = this.storage.retrieveEntry(targetEntryUUID);
            if (targetEntry == null || !targetEntry.getEntryPath().isValid()) {
                throw new StorageControllerException("Invalid target UUID: %s".formatted(targetEntryUUID.toString()));
            }
    
            final Entry destinationEntry = this.storage.retrieveEntry(destinationEntryUUID);
            if (destinationEntry == null || !destinationEntry.getIsDirectory() || !destinationEntry.getEntryPath().isValidDirectory()) {
                throw new StorageControllerException("Invalid destination UUID: %s".formatted(destinationEntryUUID.toString()));
            }

            final Transfer task = this.taskDispatch.createTask(Transfer.class).initialize(targetEntry, destinationEntry, newNodeName, true);
            return new ResponseEntity<Response>(task.getResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            if (this.printStackTrace) exception.printStackTrace();
            return new ResponseEntity<String>("Failed to copy '%s' to '%s' directory! Error: %s".formatted(targetEntryUUID.toString(), destinationEntryUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Moves a file or directory to a new location, optionally renaming it.
     *
     * <p>This method moves the specified storage entry (file or directory) identified by its UUID 
     * to a target destination directory, also identified by its UUID. If an optional new name is provided, 
     * the file or directory is saved under that name in the destination directory. If the destination directory 
     * is the same as the current parent directory, the operation acts as a rename.</p>
     *
     * @param targetEntryUUID the UUID of the storage entry (file or directory) to be moved
     * @param destinationEntryUUID the UUID of the destination directory where the storage entry will be moved
     * @param newNodeName (optional) a new name under which to save the file or directory after moving it; 
     *                    if omitted, the original name is preserved
     * @return a {@link ResponseEntity} containing the modified storage entry reflecting the new location 
     *         (and new name, if provided), or an error message with an appropriate HTTP status code if the operation fails
     * @throws StorageControllerException if the target entry or destination entry UUID is invalid, 
     *         the destination is not a directory, or other issues occur
     */
    @PostMapping(value = {"/mv/{targetEntryUUID}/{destinationEntryUUID}", "/mv/{targetEntryUUID}/{destinationEntryUUID}/{newNodeName}"})
    public ResponseEntity<?> mv(
        @PathVariable UUID targetEntryUUID,
        @PathVariable UUID destinationEntryUUID,
        @PathVariable(required = false) String newNodeName) {

        try {
            final Entry targetEntry = this.storage.retrieveEntry(targetEntryUUID);
            if (targetEntry == null || !targetEntry.getEntryPath().isValid()) {
                throw new StorageControllerException("Invalid target UUID: %s".formatted(targetEntryUUID.toString()));
            }

            if (destinationEntryUUID.equals(targetEntry.getParent().getId()) && newNodeName != null) {
                final Transfer task = this.taskDispatch.createTask(Transfer.class).initialize(targetEntry, newNodeName);
                return new ResponseEntity<Response>(task.getResponse(), HttpStatus.OK);
            }

            final Entry destinationEntry = this.storage.retrieveEntry(destinationEntryUUID);
            if (destinationEntry == null || !destinationEntry.getIsDirectory() || !destinationEntry.getEntryPath().isValidDirectory()) {
                throw new StorageControllerException("Invalid destination UUID: %s".formatted(destinationEntryUUID.toString()));
            }

            final Transfer task = this.taskDispatch.createTask(Transfer.class).initialize(targetEntry, destinationEntry, newNodeName, false);
            return new ResponseEntity<Response>(task.getResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            if (this.printStackTrace) exception.printStackTrace();
            return new ResponseEntity<String>("Failed to move '%s' to '%s' directory! Error: %s".formatted(targetEntryUUID.toString(), destinationEntryUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Sets or modifies the permission flags of a storage entry and optionally its children.
     *
     * <p>This method updates the permission flags of the specified storage entry identified by its UUID.
     * The permission flags can be set explicitly or modified using a symbol-based notation (e.g., `u+x`, `g-w`). 
     * If the `recursive` parameter is provided and set to true, the permission modification is applied 
     * recursively to all children of the target directory.</p>
     *
     * @param targetEntryUUID the UUID of the storage entry whose permission flags are to be set or modified
     * @param options the permission flags to set or a symbol-based representation of operations to modify them
     *                (e.g., `755` for explicit flags or `u+x` for symbolic operations)
     * @param recursive (optional) if true, the permission modification is applied recursively to all children of the target directory
     * @return a {@link ResponseEntity} containing the modified storage entry reflecting the updated permission flags, 
     *         or an error message with an appropriate HTTP status code if the operation fails
     * @throws StorageControllerException if the `options` parameter is invalid, the target UUID is invalid, or other issues occur
     */
    @PostMapping(value = "/chmod/{targetEntryUUID}")
    public ResponseEntity<?> chmod(
        @PathVariable UUID targetEntryUUID,
        @RequestParam(required = true) String options,
        @RequestParam(required = false) Boolean recursive) {

        try {
            if (options.isEmpty() || options.isBlank()) {
                throw new StorageControllerException("Empty permissions parameter".formatted());
            }

            final Entry targetEntry = this.storage.retrieveEntry(targetEntryUUID);
            if (targetEntry == null || !targetEntry.getEntryPath().isValid()) {
                throw new StorageControllerException("Invalid target UUID: %s".formatted(targetEntryUUID.toString()));
            }

            final ModifyPermissions task = this.taskDispatch.createTask(ModifyPermissions.class).initialize(targetEntry, options, recursive);
            return new ResponseEntity<Response>(task.getResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            if (this.printStackTrace) exception.printStackTrace();
            return new ResponseEntity<String>("Failed to update '%s' permission flags! Error: %s".formatted(targetEntryUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Sets or modifies the ownership (user and/or group) of a storage entry and optionally its children.
     *
     * <p>This method updates the ownership of the specified storage entry identified by its UUID. 
     * The ownership is defined as a user:group pair, where either or both components can be modified. 
     * If the `recursive` parameter is provided and set to true, the ownership change is applied 
     * to all children of the target directory as well.</p>
     *
     * @param targetEntryUUID the UUID of the storage entry whose ownership is to be set or modified
     * @param owners a user:group pair representing the new ownership; at least one of the user or group must be specified
     * @param recursive (optional) if true, the ownership change is applied recursively to all children of the target directory
     * @return a {@link ResponseEntity} containing the modified storage entry reflecting the updated ownership, 
     *         or an error message with an appropriate HTTP status code if the operation fails
     * @throws StorageControllerException if the `owners` parameter is invalid, the target UUID is invalid, or other issues occur
     */
    @PostMapping(value = "/chown/{targetEntryUUID}")
    public ResponseEntity<?> chown(
        @PathVariable UUID targetEntryUUID,
        @RequestParam(required = true) String owners,
        @RequestParam(required = false) Boolean recursive) {

        try {
            if (owners.isEmpty() || owners.isBlank()) {
                throw new StorageControllerException("Empty 'user:group' parameter".formatted());
            }

            final Entry targetEntry = this.storage.retrieveEntry(targetEntryUUID);
            if (targetEntry == null || !targetEntry.getEntryPath().isValid()) {
                throw new StorageControllerException("Invalid target UUID: %s".formatted(targetEntryUUID.toString()));
            }

            final ModifyOwnership task = this.taskDispatch.createTask(ModifyOwnership.class).initialize(targetEntry, owners, recursive);
            return new ResponseEntity<Response>(task.getResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            if (this.printStackTrace) exception.printStackTrace();
            return new ResponseEntity<String>("Failed to update '%s' entry ownership! Error: %s".formatted(targetEntryUUID, exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Handles the download of a file associated with a given storage entry UUID.
     *
     * <p>This method attempts to retrieve the storage entry corresponding to the provided UUID.
     * If the entry is valid and points to a readable file, it streams the file as a downloadable 
     * resource with an appropriate "Content-Disposition" header. If any error occurs during the 
     * process, an appropriate HTTP error response is returned.</p>
     *
     * @param targetEntryUUID the UUID of the storage entry representing the file to be downloaded
     * @return a {@link ResponseEntity} containing the file resource if successful, or an error 
     *         message with an appropriate HTTP status code if the operation fails
     * @throws StorageControllerException if the storage entry is invalid, the file cannot be reached, 
     *         or the file is not readable
     */
    @ResponseBody
    @GetMapping(value = "/wget/{targetEntryUUID}")
    public ResponseEntity<?> wget(
        @PathVariable UUID targetEntryUUID) {

        try {
            final Entry targetEntry = this.storage.retrieveEntry(targetEntryUUID);
            if (targetEntry == null || !targetEntry.getEntryPath().isValidFile()) {
                throw new StorageControllerException("Invalid target UUID: %s".formatted(targetEntryUUID.toString()));
            }

            final URI targetURI = targetEntry.getEntryPath().getPath().toUri();
            if (targetURI == null) {
                throw new StorageControllerException("File '%s' cannot be reached!".formatted(targetEntry.getName()));
            }

            final Resource fileResource = new UrlResource(targetURI);
            if (fileResource == null || !fileResource.isReadable()) {
                throw new StorageControllerException("File '%s' cannot be read!".formatted(targetEntry.getName()));
            }

            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileResource.getFilename() + "\"").body(fileResource);
        } catch (Exception exception) {
            if (this.printStackTrace) exception.printStackTrace();
            return new ResponseEntity<String>("Failed to download '%s' from storage! %s".formatted(targetEntryUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Updates metadata of a storage entry (file or directory).
     *
     * <p>This method allows updating metadata attributes of a storage entry identified by its UUID.
     * The updated metadata is provided as key-value pairs in the {@code updatedMetadata} parameter.
     * On success, the response contains the updated storage entry. On failure, it includes an error message.</p>
     *
     * @param targetEntryUUID the UUID of the storage entry (file/directory) to update
     * @param updatedMetadata a map of metadata key-value pairs to update for the specified storage entry
     * @return a {@link ResponseEntity} containing the updated storage entry or an error message if the update fails
     * @throws StorageControllerException if the target storage entry is invalid, cannot be updated, or other issues occur
     */
    @PostMapping(value = "/doc/{targetEntryUUID}")
    public ResponseEntity<?> doc(
        @PathVariable UUID targetEntryUUID,
        @RequestParam Map<String, Object> updatedMetadata) {

        try {
            final Entry targetEntry = this.storage.retrieveEntry(targetEntryUUID);
            if (targetEntry == null || !targetEntry.getEntryPath().isValid()) {
                throw new StorageControllerException("Invalid target UUID: %s".formatted(targetEntryUUID.toString()));
            }

            final ModifyField task = this.taskDispatch.createTask(ModifyField.class).initialize(targetEntry, updatedMetadata);
            return new ResponseEntity<Response>(task.getResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to update '%s' entry metadata! Error: %s".formatted(targetEntryUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Updates a storage entry (file or directory) without modifying its metadata.
     *
     * <p>This method allows performing an update operation on a storage entry identified by its UUID.
     * Unlike the other update method, this version does not modify metadata attributes but instead
     * applies a predefined update operation on the target entry.
     * On success, the response contains the updated storage entry. On failure, it includes an error message.</p>
     *
     * @param targetEntryUUID the UUID of the storage entry (file/directory) to update
     * @return a {@link ResponseEntity} containing the updated storage entry or an error message if the update fails
     * @throws StorageControllerException if the target storage entry is invalid, cannot be updated, or other issues occur
     */
    @PostMapping(value = "/dos/{targetEntryUUID}")
    public ResponseEntity<?> dos(
        @RequestBody Entry targetEntry) {

        try {
            if (targetEntry == null) {
                throw new StorageControllerException("Invalid target".formatted());
            }

            if (this.storage.retrieveEntry(targetEntry.getId()) == null) {
                throw new StorageControllerException("Invalid target UUID: %s".formatted(targetEntry.getId().toString()));
            }

            final Modify task = this.taskDispatch.createTask(Modify.class).initialize(targetEntry);
            return new ResponseEntity<Response>(task.getResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to update '%s' entry! Error: %s".formatted(targetEntry != null ? targetEntry.getId().toString() : "null", exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Deletes a specified file or directory represented by its UUID.
     *
     * <p>This method removes the storage entry corresponding to the given UUID. If the specified entry 
     * is invalid or cannot be located, an error is returned. The deletion operation is irreversible 
     * and may fail if the target entry is in use or lacks proper permissions.</p>
     *
     * @param targetEntryUUID the UUID of the storage entry to be deleted
     * @return a {@link ResponseEntity} containing the result of the deletion operation. If successful, 
     *         the response contains information about the deleted entry. If an error occurs, an 
     *         appropriate error message with an HTTP status code is returned.
     * @throws StorageControllerException if the target UUID is invalid, does not point to a valid storage entry,
     * or any other errors encountered during the deletion process
     */
    @DeleteMapping(value = "/rm/{targetEntryUUID}")
    public ResponseEntity<?> rm(
        @PathVariable UUID targetEntryUUID) {

        try {
            final Entry targetEntry = this.storage.retrieveEntry(targetEntryUUID);
            if (targetEntry == null || !targetEntry.getEntryPath().isValid()) {
                throw new StorageControllerException("Invalid target UUID: %s".formatted(targetEntryUUID.toString()));
            }

            final Delete task = this.taskDispatch.createTask(Delete.class).initialize(targetEntry);
            return new ResponseEntity<Response>(task.getResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            if (this.printStackTrace) exception.printStackTrace();
            return new ResponseEntity<String>("Failed to remove '%s' from storage! Error: %s".formatted(targetEntryUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}