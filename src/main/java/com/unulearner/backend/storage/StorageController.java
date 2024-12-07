package com.unulearner.backend.storage;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.properties.StorageProperties;
import com.unulearner.backend.storage.responses.StorageServiceResponse;

@Controller
@RequestMapping(path = "/storage")
public class StorageController {
    private final Storage storageService;
    private final StorageProperties storageProperties;
    private final Long PROCESS_ID = ProcessHandle.current().pid();
    private final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    public StorageController(Storage storageService, StorageProperties storageProperties) {
        this.storageService = storageService;
        this.storageProperties = storageProperties;

        /* We'll be getting some storage properties here (mostly in regards to logging) */
    }

    //**********************************************************//
    //*                                                        *//
    //*   From here on, it be all about THEM single files!     *//
    //*                                                        *//
    //**********************************************************//

    /**
     * @param content the file itself...
     * @param description of the file (storage node)
     * @param destinationDirectoryID UUID of the directory to upload to
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping(value = "/upload/file/to/{destinationDirectoryID}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<?> uploadFile(
        @PathVariable UUID destinationDirectoryID,
        @RequestParam(name = "content") MultipartFile content,
        @RequestPart(name = "node") StorageEntry newStorageNode) {

        try {
            final File tempFile = new File(TEMP_DIR, "tmp-%d-%s-%s.tmp".formatted(PROCESS_ID, UUID.randomUUID().toString(), newStorageNode.getName()));
            content.transferTo(tempFile); /* Failure at this point is fatal to the request (it is an intended behavior!) */

            final StorageServiceResponse response = storageService.createFileStorageNode(tempFile, newStorageNode, destinationDirectoryID);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to upload file '%s' to '%s' directory! Error: %s".formatted(newStorageNode.getName(), destinationDirectoryID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetFileID UUID of the file to update
     * @param updatedName updated name for the file...
     * @param updatedDescription updated description for the file (storage node)
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping("/update/file")
    public ResponseEntity<?> updateFile(
        @RequestBody StorageEntry updatedStorageNode) {

        try {
            final StorageServiceResponse response = storageService.updateFileStorageNode(updatedStorageNode);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to update '%s' file! Error: %s".formatted(updatedStorageNode.getId().toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/chmod/file/{targetFileID}")
    public ResponseEntity<?> chmodFile(
        @PathVariable UUID targetFileID,
        @RequestParam(required = true) Short flags,
        @RequestParam(required = false) Boolean recursive) {

        try {
            final StorageServiceResponse response = storageService.chmodFileStorageNode(targetFileID, flags, recursive);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to update '%s' file permission flags! Error: %s".formatted(targetFileID, exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/chown/file/{targetFileID}")
    public ResponseEntity<?> chownFile(
        @PathVariable UUID targetFileID,
        @RequestParam(required = true) UUID user,
        @RequestParam(required = true) UUID group,
        @RequestParam(required = false) Boolean recursive) {

        try {
            final StorageServiceResponse response = storageService.chownFileStorageNode(targetFileID, user, group, recursive);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to update '%s' file permission flags! Error: %s".formatted(targetFileID, exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetFileID UUID of the file to copy
     * @param destinationDirectoryID UUID of the directory to copy to
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping("/copy/file/{targetFileID}/to/{destinationDirectoryID}")
    public ResponseEntity<?> copyFile(
        @PathVariable UUID targetFileID,
        @PathVariable UUID destinationDirectoryID,
        @RequestParam(required = false) String newName) {

        try {
            final StorageServiceResponse response = storageService.transferFileStorageNode(targetFileID, destinationDirectoryID, newName, true);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to copy file '%s' to '%s' directory! Error: %s".formatted(targetFileID, destinationDirectoryID, exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetFileID UUID of the file to move
     * @param destinationDirectoryID UUID of the directory to move to
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping("/move/file/{targetFileID}/to/{destinationDirectoryID}")
    public ResponseEntity<?> moveFile(
        @PathVariable UUID targetFileID,
        @PathVariable UUID destinationDirectoryID,
        @RequestParam(required = false) String newName) {

        try {
            final StorageServiceResponse response = storageService.transferFileStorageNode(targetFileID, destinationDirectoryID, newName, false);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to move file '%s' to '%s' directory! Error: %s".formatted(targetFileID, destinationDirectoryID, exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetFileID UUID of the file to delete
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @DeleteMapping("/delete/file/{targetFileID}")
    public ResponseEntity<?> deleteFile(
        @PathVariable UUID targetFileID) {

        try {
            final StorageServiceResponse response = storageService.deleteFileStorageNode(targetFileID);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("File '%s' could not be removed: %s".formatted(targetFileID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetFileID UUID of the file to download
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @ResponseBody
    @GetMapping("/download/file/{targetFileID}")
    public ResponseEntity<?> downloadFile(
        @PathVariable UUID targetFileID) {

        try {
            final Resource returnResource = storageService.downloadFileStorageNode(targetFileID);
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + returnResource.getFilename() + "\"").body(returnResource);
        } catch (Exception exception) {
            return new ResponseEntity<String>("File '%s' could not be downloaded: %s".formatted(targetFileID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    //*********************************************************//
    //*                                                       *//
    //*   From here on, it be all about THEM directories!     *//
    //*                                                       *//
    //*********************************************************//

    /**
     * @param directory name of the directory...
     * @param description of the directory (storage node)
     * @param destinationDirectoryID UUID of the directory to create in
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping("/create/directory/in/{destinationDirectoryID}")
    public ResponseEntity<?> createDirectory(
        @PathVariable UUID destinationDirectoryID,
        @RequestBody StorageEntry newStorageNode) {

        try {
            final StorageServiceResponse response = storageService.createDirectoryStorageNode(newStorageNode, destinationDirectoryID);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to create new directory '%s' and add it to '%s' directory! Error: %s".formatted("directory", destinationDirectoryID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param updatedName updated name for the directory...
     * @param updatedDescription updated description for the directory (storage node)
     * @param targetDirectoryID UUID of the directory to update
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping("/update/directory")
    public ResponseEntity<?> updateDirectory(
        @RequestBody StorageEntry updatedStorageNode) {

        try {
            final StorageServiceResponse response = storageService.updateDirectoryStorageNode(updatedStorageNode);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to update '%s' directory! Error: %s".formatted(updatedStorageNode.getId().toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/chmod/directory/{targetDirectoryID}")
    public ResponseEntity<?> chmodDirectory(
        @PathVariable UUID targetDirectoryID,
        @RequestParam(required = true) Short flags,
        @RequestParam(required = false) Boolean recursive) {

        try {
            final StorageServiceResponse response = storageService.chmodDirectoryStorageNode(targetDirectoryID, flags, recursive);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to update '%s' directory permission flags! Error: %s".formatted(targetDirectoryID, exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/chown/directory/{targetDirectoryID}")
    public ResponseEntity<?> chownDirectory(
        @PathVariable UUID targetDirectoryID,
        @RequestParam(required = true) UUID user,
        @RequestParam(required = true) UUID group,
        @RequestParam(required = false) Boolean recursive) {

        try {
            final StorageServiceResponse response = storageService.chownDirectoryStorageNode(targetDirectoryID, user, group, recursive);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to update '%s' directory permission flags! Error: %s".formatted(targetDirectoryID, exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetDirectoryID UUID of the directory to copy
     * @param destinationDirectoryID UUID of the directory to copy to
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping("/copy/directory/{targetDirectoryID}/to/{destinationDirectoryID}")
    public ResponseEntity<?> copyDirectory(
        @PathVariable UUID targetDirectoryID,
        @PathVariable UUID destinationDirectoryID,
        @RequestParam(required = false) String newName) {

        try {
            final StorageServiceResponse response = storageService.transferDirectoryStorageNode(targetDirectoryID, destinationDirectoryID, newName, true);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to copy directory '%s' to '%s' directory! Error: %s".formatted(targetDirectoryID, destinationDirectoryID, exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetDirectoryID UUID of the directory to move
     * @param destinationDirectoryID UUID of the directory to move to
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping("/move/directory/{targetDirectoryID}/to/{destinationDirectoryID}")
    public ResponseEntity<?> moveDirectory(
        @PathVariable UUID targetDirectoryID,
        @PathVariable UUID destinationDirectoryID,
        @RequestParam(required = false) String newName) {

        try {
            final StorageServiceResponse response = storageService.transferDirectoryStorageNode(targetDirectoryID, destinationDirectoryID, newName, false);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Failed to move directory '%s' to '%s' directory! Error: %s".formatted(targetDirectoryID, destinationDirectoryID, exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetDirectoryID UUID of the directory to delete
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @DeleteMapping("/delete/directory/{targetDirectoryID}")
    public ResponseEntity<?> deleteDirectory(
        @PathVariable UUID targetDirectoryID) {

        try {
            final StorageServiceResponse response = storageService.deleteDirectoryStorageNode(targetDirectoryID);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Directory '%s' could not be removed: %s".formatted(targetDirectoryID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetDirectoryID UUID of the directory to download
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @GetMapping("/download/directory/{targetDirectoryID}")
    public ResponseEntity<?> downloadDirectory(
        @PathVariable UUID targetDirectoryID) {

        try {
            final StorageNode response = storageService.downloadDirectoryStorageNode(targetDirectoryID);
            return new ResponseEntity<StorageNode>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Directory '%s' could not be downloaded: %s".formatted(targetDirectoryID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
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
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageTaskState}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @GetMapping("/execute/task/{taskID}")
    public ResponseEntity<?> executeTask(
        @PathVariable(name = "taskID", required = true) UUID taskID,
        @RequestParam(name = "taskParameters", required = false) Map<String, Object> taskParameters) {

        try {
            final StorageServiceResponse response = storageService.executeStorageTask(taskID, taskParameters);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Task '%s' could not be executed: %s".formatted(taskID.toString(), exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    //***************************************************//
    //*                                                 *//
    //*   From here on, it be all about THEM rests!     *//
    //*                                                 *//
    //***************************************************//

    /**
     * @return
     */
    @GetMapping("/root/download")
    public ResponseEntity<?> rootDownload() {
        try {
            final StorageNode returnValue = storageService.downloadRootDirectoryStorageNode();
            return new ResponseEntity<>(returnValue, HttpStatus.OK);
        } catch (Exception exception) {
            return new ResponseEntity<String>("Root directory could not be downloaded: %s".formatted(exception.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}