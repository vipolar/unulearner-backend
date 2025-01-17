package com.unulearner.backend.storage;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.properties.StorageProperties;
import com.unulearner.backend.storage.responses.StorageServiceResponse;
import com.unulearner.backend.storage.exceptions.StorageServiceException;

import com.unulearner.backend.storage.tasks.StorageTaskBase;
import com.unulearner.backend.storage.tasks.StorageTaskChmodNode;
import com.unulearner.backend.storage.tasks.StorageTaskChownNode;
import com.unulearner.backend.storage.tasks.StorageTaskCreateNode;
import com.unulearner.backend.storage.tasks.StorageTaskDestroyNode;
import com.unulearner.backend.storage.tasks.StorageTaskTransferNode;

@Controller
@RequestMapping(path = "/storage")
public class StorageController {
    private final Storage storage;
    private final StorageTasksMap storageTasksMap;
    private final StorageProperties storageProperties;
    private final Long PROCESS_ID = ProcessHandle.current().pid();
    private final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private final ObjectProvider<StorageTaskChownNode> storageTaskChownNode;
    private final ObjectProvider<StorageTaskChmodNode> storageTaskChmodNode;
    private final ObjectProvider<StorageTaskCreateNode> storageTaskCreateNode;
    private final ObjectProvider<StorageTaskDestroyNode> storageTaskDestroyNode;
    private final ObjectProvider<StorageTaskTransferNode> storageTaskTransferNode;

    public StorageController(Storage storage, StorageTasksMap storageTasksMap, StorageProperties storageProperties, ObjectProvider<StorageTaskChownNode> storageTaskChownNode, ObjectProvider<StorageTaskChmodNode> storageTaskChmodNode, ObjectProvider<StorageTaskCreateNode> storageTaskCreateNode, ObjectProvider<StorageTaskTransferNode> storageTaskTransferNode, ObjectProvider<StorageTaskDestroyNode> storageTaskDestroyNode) {
        this.storage = storage;
        this.storageTasksMap = storageTasksMap;
        this.storageProperties = storageProperties;
        this.storageTaskChownNode = storageTaskChownNode;
        this.storageTaskChmodNode = storageTaskChmodNode;
        this.storageTaskCreateNode = storageTaskCreateNode;
        this.storageTaskDestroyNode = storageTaskDestroyNode;
        this.storageTaskTransferNode = storageTaskTransferNode;

        /* We'll be getting some storage properties here (mostly in regards to logging) */
    }

    /* TODO: update error messages and messages in all cases! */

    /**
     * @param targetDirectoryID UUID of the directory to download
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code storageNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @GetMapping(value = {"/ftp", "/ftp/{targetStorageNodeUUID}"})
    public ResponseEntity<?> ftp(
        @PathVariable(required = false) UUID targetStorageNodeUUID) {

        if (targetStorageNodeUUID == null) {
            return new ResponseEntity<StorageNode>(this.storage.retrieveRootStorageNode(), HttpStatus.OK);
        }

        try {
            final StorageNode targetStorageNode = this.storage.retrieveStorageNode(targetStorageNodeUUID);
            if (targetStorageNode == null || !targetStorageNode.getNodePath().isValidNode()) {
                throw new StorageServiceException("Invalid target UUID: %s".formatted(targetStorageNodeUUID.toString()));
            }
    
            return new ResponseEntity<StorageNode>(targetStorageNode, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Directory '%s' could not be downloaded: %s".formatted(targetStorageNodeUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param content the file itself...
     * @param destinationStorageNodeUUID UUID of the directory to upload to
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code storageNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping(value = {"/scp/{destinationStorageNodeUUID}", "/scp/{destinationStorageNodeUUID}/{newFileName}"})
        public ResponseEntity<?> scp(
        @PathVariable UUID destinationStorageNodeUUID,
        @PathVariable(required = false) String newFileName,
        @RequestParam(name = "file") MultipartFile multipartFile) {

        try {
            final StorageNode destinationStorageNode = this.storage.retrieveStorageNode(destinationStorageNodeUUID);
            if (destinationStorageNode == null || !destinationStorageNode.getIsDirectory() || !destinationStorageNode.getNodePath().isValidDirectory()) {
                throw new StorageServiceException("Invalid destination UUID: %s".formatted(destinationStorageNodeUUID.toString()));
            }

            final File tempFile = new File(TEMP_DIR, "tmp-%d-%s-%s.tmp".formatted(PROCESS_ID, UUID.randomUUID().toString(), newFileName != null ? newFileName : multipartFile.getOriginalFilename()));
            multipartFile.transferTo(tempFile); /* Failure at this point is fatal to the request (it is an intended behavior!) */

            final StorageTaskCreateNode storageTask = this.storageTaskCreateNode.getObject().initialize(destinationStorageNode, newFileName != null ? newFileName : multipartFile.getOriginalFilename(), tempFile);
            return new ResponseEntity<StorageServiceResponse>(storageTask.getStorageServiceResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to upload file '%s' to '%s' directory! Error: %s".formatted(newFileName != null ? newFileName : multipartFile.getOriginalFilename(), destinationStorageNodeUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param directory name of the directory...
     * @param description of the directory (storage node)
     * @param destinationDirectoryID UUID of the directory to create in
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code storageNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping(value = "/mkdir/{destinationStorageNodeUUID}/{newDirectoryName}")
    public ResponseEntity<?> mkdir(
        @PathVariable UUID destinationStorageNodeUUID,
        @PathVariable(required = true) String newDirectoryName) {

        try {
            final StorageNode destinationStorageNode = this.storage.retrieveStorageNode(destinationStorageNodeUUID);
            if (destinationStorageNode == null || !destinationStorageNode.getIsDirectory() || !destinationStorageNode.getNodePath().isValidDirectory()) {
                throw new StorageServiceException("Invalid destination UUID: %s".formatted(destinationStorageNodeUUID.toString()));
            }

            final StorageTaskCreateNode storageTask = this.storageTaskCreateNode.getObject().initialize(destinationStorageNode, newDirectoryName);
            return new ResponseEntity<StorageServiceResponse>(storageTask.getStorageServiceResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            exception.printStackTrace();
            return new ResponseEntity<String>("Failed to create new directory '%s' and add it to '%s' directory! Error: %s".formatted(newDirectoryName, destinationStorageNodeUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetStorageNodeUUID UUID of the file to copy
     * @param destinationStorageNodeUUID UUID of the directory to copy to
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code storageNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping(value = {"/cp/{targetStorageNodeUUID}/{destinationStorageNodeUUID}", "/cp/{targetStorageNodeUUID}/{destinationStorageNodeUUID}/{newNodeName}"})
    public ResponseEntity<?> cp(
        @PathVariable UUID targetStorageNodeUUID,
        @PathVariable UUID destinationStorageNodeUUID,
        @PathVariable(required = false) String newNodeName) {

        try {
            final StorageNode targetStorageNode = this.storage.retrieveStorageNode(targetStorageNodeUUID);
            if (targetStorageNode == null || !targetStorageNode.getNodePath().isValidNode()) {
                throw new StorageServiceException("Invalid target UUID: %s".formatted(targetStorageNodeUUID.toString()));
            }
    
            final StorageNode destinationStorageNode = this.storage.retrieveStorageNode(destinationStorageNodeUUID);
            if (destinationStorageNode == null || !destinationStorageNode.getIsDirectory() || !destinationStorageNode.getNodePath().isValidDirectory()) {
                throw new StorageServiceException("Invalid destination UUID: %s".formatted(destinationStorageNodeUUID.toString()));
            }

            final StorageTaskTransferNode storageTask = this.storageTaskTransferNode.getObject().initialize(targetStorageNode, destinationStorageNode, newNodeName, true);
            return new ResponseEntity<StorageServiceResponse>(storageTask.getStorageServiceResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to copy '%s' to '%s'! Error: %s".formatted(targetStorageNodeUUID.toString(), destinationStorageNodeUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetStorageNodeUUID UUID of the file to move
     * @param destinationStorageNodeUUID UUID of the directory to move to
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code storageNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping(value = {"/mv/{targetStorageNodeUUID}/{destinationStorageNodeUUID}", "/mv/{targetStorageNodeUUID}/{destinationStorageNodeUUID}/{newNodeName}"})
    public ResponseEntity<?> mv(
        @PathVariable UUID targetStorageNodeUUID,
        @PathVariable UUID destinationStorageNodeUUID,
        @PathVariable(required = false) String newNodeName) {

        try {
            final StorageNode targetStorageNode = this.storage.retrieveStorageNode(targetStorageNodeUUID);
            if (targetStorageNode == null || !targetStorageNode.getNodePath().isValidNode()) {
                throw new StorageServiceException("Invalid target UUID: %s".formatted(targetStorageNodeUUID.toString()));
            }

            if (destinationStorageNodeUUID.equals(targetStorageNode.getParent().getId()) && newNodeName != null) {
                final StorageTaskTransferNode storageTask = this.storageTaskTransferNode.getObject().initialize(targetStorageNode, newNodeName);
                return new ResponseEntity<StorageServiceResponse>(storageTask.getStorageServiceResponse(), HttpStatus.OK);
            }

            final StorageNode destinationStorageNode = this.storage.retrieveStorageNode(destinationStorageNodeUUID);
            if (destinationStorageNode == null || !destinationStorageNode.getIsDirectory() || !destinationStorageNode.getNodePath().isValidDirectory()) {
                throw new StorageServiceException("Invalid destination UUID: %s".formatted(destinationStorageNodeUUID.toString()));
            }

            final StorageTaskTransferNode storageTask = this.storageTaskTransferNode.getObject().initialize(targetStorageNode, destinationStorageNode, newNodeName, false);
            return new ResponseEntity<StorageServiceResponse>(storageTask.getStorageServiceResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to move '%s' to '%s'! Error: %s".formatted(targetStorageNodeUUID.toString(), destinationStorageNodeUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping(value = "/chmod/{targetStorageNodeUUID}")
    public ResponseEntity<?> chmod(
        @PathVariable UUID targetStorageNodeUUID,
        @RequestParam(required = true) String options,
        @RequestParam(required = false) Boolean recursive) {

        try {
            if (options.isEmpty() || options.isBlank()) {
                throw new StorageServiceException("Empty permissions parameter".formatted());
            }

            final StorageNode targetStorageNode = this.storage.retrieveStorageNode(targetStorageNodeUUID);
            if (targetStorageNode == null || !targetStorageNode.getNodePath().isValidNode()) {
                throw new StorageServiceException("Invalid target UUID: %s".formatted(targetStorageNodeUUID.toString()));
            }

            final StorageTaskChmodNode storageTask = this.storageTaskChmodNode.getObject().initialize(targetStorageNode, options, recursive);
            return new ResponseEntity<StorageServiceResponse>(storageTask.getStorageServiceResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to update '%s' file permission flags! Error: %s".formatted(targetStorageNodeUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping(value = "/chown/{targetStorageNodeUUID}")
    public ResponseEntity<?> chown(
        @PathVariable UUID targetStorageNodeUUID,
        @RequestParam(required = true) String owners,
        @RequestParam(required = false) Boolean recursive) {

        try {
            if (owners.isEmpty() || owners.isBlank()) {
                throw new StorageServiceException("Empty 'user:group' parameter".formatted());
            }

            final StorageNode targetStorageNode = this.storage.retrieveStorageNode(targetStorageNodeUUID);
            if (targetStorageNode == null || !targetStorageNode.getNodePath().isValidNode()) {
                throw new StorageServiceException("Invalid target UUID: %s".formatted(targetStorageNodeUUID.toString()));
            }

            final StorageTaskChownNode storageTask = this.storageTaskChownNode.getObject().initialize(targetStorageNode, owners, recursive);
            return new ResponseEntity<StorageServiceResponse>(storageTask.getStorageServiceResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to update '%s' file permission flags! Error: %s".formatted(targetStorageNodeUUID, exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetFileID UUID of the file to download
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code storageNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @ResponseBody
    @GetMapping(value = "/curl/{targetStorageNodeUUID}")
    public ResponseEntity<?> curl(
        @PathVariable UUID targetStorageNodeUUID) {

        try {
            final StorageNode targetStorageNode = this.storage.retrieveStorageNode(targetStorageNodeUUID);
            if (targetStorageNode == null || !targetStorageNode.getNodePath().isValidNode()) {
                throw new StorageServiceException("Invalid target UUID: %s".formatted(targetStorageNodeUUID.toString()));
            }

            final URI targetURI = targetStorageNode.getNodePath().getPath().toUri();
            if (targetURI == null) {
                throw new StorageServiceException("File '%s' could not be reached!".formatted(targetStorageNode.getName()));
            }

            final Resource fileResource = new UrlResource(targetURI);
            if (fileResource == null || !fileResource.isReadable()) {
                throw new StorageServiceException("File '%s' could not be read!".formatted(targetStorageNode.getName()));
            }

            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileResource.getFilename() + "\"").body(fileResource);
        } catch (Exception exception) {
            return new ResponseEntity<String>("File '%s' could not be downloaded: %s".formatted(targetStorageNodeUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetFileID UUID of the file to delete
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code storageNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @DeleteMapping(value = "/rm/{targetStorageNodeUUID}")
    public ResponseEntity<?> rm(
        @PathVariable UUID targetStorageNodeUUID) {

        try {
            final StorageNode targetStorageNode = this.storage.retrieveStorageNode(targetStorageNodeUUID);
            if (targetStorageNode == null || !targetStorageNode.getNodePath().isValidNode()) {
                throw new StorageServiceException("Invalid target UUID: %s".formatted(targetStorageNodeUUID.toString()));
            }

            final StorageTaskDestroyNode storageTask = this.storageTaskDestroyNode.getObject().initialize(targetStorageNode);
            return new ResponseEntity<StorageServiceResponse>(storageTask.getStorageServiceResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("File '%s' could not be removed: %s".formatted(targetStorageNodeUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    //***************************************************//
    //*                                                 *//
    //*   From here on, it be all about THEM tasks!     *//
    //*                                                 *//
    //***************************************************//

    /**
     * @param taskID UUID of the task to execute
     * @param taskParameters pretty self explanatory
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code storageNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @GetMapping(value = "/exec/{storageTaskUUID}")
    public ResponseEntity<?> exec(
        @PathVariable() UUID storageTaskUUID,
        @RequestParam(required = false) Map<String, Object> taskParameters) {

        try {
            final StorageTaskBase storageTask = this.storageTasksMap.getStorageTask(storageTaskUUID); storageTask.execute(taskParameters);
            return new ResponseEntity<StorageServiceResponse>(storageTask.getStorageServiceResponse(), HttpStatus.OK);
        } catch (Exception exception) {
            exception.printStackTrace();
            return new ResponseEntity<String>("Task '%s' could not be executed: %s".formatted(storageTaskUUID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    //**********************************************************//
    //*                                                        *//
    //*   From here on, it be all about THEM single files!     *//
    //*                                                        *//
    //**********************************************************//

    /**
     * @param targetFileID UUID of the file to update
     * @param updatedName updated name for the file...
     * @param updatedDescription updated description for the file (storage node)
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code storageNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     *
    @PostMapping(value = "/update/file")
    public ResponseEntity<?> updateFile(
        @RequestBody StorageNode updatedStorageNode) {

        try {
            final StorageServiceResponse response = storageService.updateFileStorageNode(updatedStorageNode);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to update '%s' file! Error: %s".formatted(updatedStorageNode.getId().toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public StorageServiceResponse renameDirectoryStorageNode(UUID targetDirectoryID, String newDirectoryName) throws Exception {
        final StorageNode targetStorageNode = this.storage.retrieveStorageNode(targetDirectoryID);
        if (targetStorageNode == null || !targetStorageNode.getIsDirectory()) {
            throw new StorageServiceException("Target directory ID '%s' is invalid!".formatted(targetDirectoryID));
        }

        if (newDirectoryName == null || newDirectoryName.isEmpty() || newDirectoryName.isBlank()) {
            throw new StorageServiceException("New directory name cannot be blank!".formatted());
        }

        try {
            final StorageTaskTransferNode storageTask = new StorageTaskTransferNode(targetStorageNode, newDirectoryName);
            return storageTask.getStorageServiceResponse();
        } catch (Exception exception) {
            throw new StorageServiceException("Directory update task initialization failed: %s".formatted(exception.getMessage()), exception);
        }
    } */


}