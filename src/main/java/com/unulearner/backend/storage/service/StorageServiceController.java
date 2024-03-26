package com.unulearner.backend.storage.service;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.file.FileAlreadyExistsException;

import org.springframework.beans.factory.annotation.Autowired;

import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.responses.StorageServiceError;
import com.unulearner.backend.storage.responses.StorageServiceResponse;

@Controller
@RequestMapping(path = "/storage")
public class StorageServiceController {

    @Autowired
    StorageServiceImplementation storageService;
    private final Boolean debugPrintStackTrace = true;

    //**********************************************************//
    //*                                                        *//
    //*   From here on, it be all about THEM single files!     *//
    //*                                                        *//
    //**********************************************************//

    /**
     * @param content
     * @param description
     * @param destinationDirectoryID
     * @param onConflict
     * @return
     */
    @PostMapping("/file/add/to/{destinationDirectoryID}")
    public ResponseEntity<?> fileUpload(
        @RequestParam MultipartFile content,
        @RequestParam String description,
        @PathVariable UUID destinationDirectoryID,
        @RequestParam(name = "conflict", defaultValue = "default", required = false) String onConflict) {

        String errorMessage = null;
        StorageTreeNode returnValue = null;
        HttpStatus status = HttpStatus.CREATED;
        //TODO: rework!

        try {
            returnValue = storageService.createFileStorageTreeNode(content, description, destinationDirectoryID, onConflict);
            return new ResponseEntity<>(returnValue, status);
        } catch (Exception fileUploadException) {
            if (fileUploadException instanceof FileAlreadyExistsException) {
                status = HttpStatus.CONFLICT;
            } else {
                status = HttpStatus.EXPECTATION_FAILED;
            }

            if (debugPrintStackTrace) {fileUploadException.printStackTrace();}
            errorMessage = "Failed to upload file '%s' to '%s' directory! Error: %s".formatted(content.getOriginalFilename(), destinationDirectoryID.toString(), fileUploadException.getMessage());
            return new ResponseEntity<>(new StorageServiceResponse(errorMessage), status);
        }
    }

    /**
     * @param updatedName
     * @param content
     * @param updatedDescription
     * @param targetFileID
     * @param onConflict
     * @return
     */
    @PostMapping("/file/update/{targetFileID}")
    public ResponseEntity<?> fileUpdate(
        @RequestParam(name = "name") String updatedName,
        @RequestParam(name = "description") String updatedDescription,
        @RequestParam(required = false) MultipartFile content,
        @PathVariable UUID targetFileID,
        @RequestParam(name = "conflict", defaultValue = "default", required = false) String onConflict) {

        String errorMessage = null;
        StorageTreeNode returnValue = null;
        HttpStatus status = HttpStatus.OK;
        //TODO: rework!

        try {
            returnValue = storageService.updateFileStorageTreeNode(content, updatedName, updatedDescription, targetFileID, onConflict);
            return new ResponseEntity<>(returnValue, status);
        } catch (Exception fileUpdateException) {
            if (fileUpdateException instanceof FileAlreadyExistsException) {
                status = HttpStatus.CONFLICT;
            } else {
                status = HttpStatus.EXPECTATION_FAILED;
            }

            if (debugPrintStackTrace) {fileUpdateException.printStackTrace();}
            errorMessage = "Failed to update '%s' file! Error: %s".formatted(targetFileID.toString(), fileUpdateException.getMessage());
            return new ResponseEntity<>(new StorageServiceResponse(errorMessage), status);
        }
    }

    /**
     * @param targetFileID UUID of the file to copy
     * @param destinationDirectoryID UUID of the directory to copy to
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageServiceResponse}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping("/copy/file/{targetFileID}/to/{destinationDirectoryID}")
    public ResponseEntity<?> copyFile(
        @PathVariable UUID targetFileID,
        @PathVariable UUID destinationDirectoryID) {

        try {
            final StorageServiceResponse response = storageService.transferFileStorageTreeNode(targetFileID, destinationDirectoryID, true);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            if (debugPrintStackTrace) {
                exception.printStackTrace();
            }

            final StorageServiceError error = new StorageServiceError("Failed to copy file '%s' to '%s' directory! Error: %s".formatted(targetFileID, destinationDirectoryID, exception.getMessage()));
            return new ResponseEntity<StorageServiceError>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetFileID UUID of the file to move
     * @param destinationDirectoryID UUID of the directory to move to
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageServiceResponse}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping("/move/file/{targetFileID}/to/{destinationDirectoryID}")
    public ResponseEntity<?> moveFile(
        @PathVariable UUID targetFileID,
        @PathVariable UUID destinationDirectoryID) {

        try {
            final StorageServiceResponse response = storageService.transferFileStorageTreeNode(targetFileID, destinationDirectoryID, false);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            if (debugPrintStackTrace) {
                exception.printStackTrace();
            }

            final StorageServiceError error = new StorageServiceError("Failed to move file '%s' to '%s' directory! Error: %s".formatted(targetFileID, destinationDirectoryID, exception.getMessage()));
            return new ResponseEntity<StorageServiceError>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetFileID UUID of the file to delete
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageServiceResponse}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @DeleteMapping("/delete/file/{targetFileID}")
    public ResponseEntity<?> deleteFile(
        @PathVariable UUID targetFileID) {

        try {
            final StorageServiceResponse response = storageService.deleteFileStorageTreeNode(targetFileID);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            if (debugPrintStackTrace) {
                exception.printStackTrace();
            }

            final StorageServiceError error = new StorageServiceError("File '%s' could not be removed: %s".formatted(targetFileID.toString(), exception.getMessage()));
            return new ResponseEntity<StorageServiceError>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetFileID UUID of the file to download
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageServiceResponse}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @ResponseBody
    @GetMapping("/download/file/{targetFileID}")
    public ResponseEntity<?> downloadFile(
        @PathVariable UUID targetFileID) {

        try {
            final Resource returnResource = storageService.downloadFileStorageTreeNode(targetFileID);
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + returnResource.getFilename() + "\"").body(returnResource);
        } catch (Exception exception) {
            if (debugPrintStackTrace) {
                exception.printStackTrace();
            }

            final StorageServiceError error = new StorageServiceError("File '%s' could not be download: %s".formatted(targetFileID.toString(), exception.getMessage()));
            return new ResponseEntity<StorageServiceError>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    //*********************************************************//
    //*                                                       *//
    //*   From here on, it be all about THEM directories!     *//
    //*                                                       *//
    //*********************************************************//

    /**
     * @param directory
     * @param description
     * @param destinationDirectoryID
     * @param onConflict
     * @return
     */
    @PostMapping("/directory/add/to/{destinationDirectoryID}")
    public ResponseEntity<?> directoryCreate(
        @RequestParam String directory,
        @RequestParam String description,
        @PathVariable UUID destinationDirectoryID,
        @RequestParam(name = "conflict", defaultValue = "default", required = false) String onConflict) {

        String errorMessage = null;
        StorageTreeNode returnValue = null;
        HttpStatus status = HttpStatus.CREATED;
        //TODO: rework!

        try {
            returnValue = storageService.createDirectoryStorageTreeNode(directory, description, destinationDirectoryID, onConflict);
            return new ResponseEntity<>(returnValue, status);
        } catch (Exception directoryCreateException) {
            if (directoryCreateException instanceof FileAlreadyExistsException) {
                status = HttpStatus.CONFLICT;
            } else {
                status = HttpStatus.EXPECTATION_FAILED;
            }

            if (debugPrintStackTrace) {directoryCreateException.printStackTrace();}
            errorMessage = "Failed to create new directory '%s' and add it to '%s' directory! Error: %s".formatted(directory, destinationDirectoryID.toString(), directoryCreateException.getMessage());
            return new ResponseEntity<>(new StorageServiceResponse(errorMessage), status);
        }
    }

    /**
     * @param updatedName
     * @param updatedDescription
     * @param targetDirectoryID
     * @param onConflict
     * @return
     */
    @PostMapping("/directory/update/{targetDirectoryID}")
    public ResponseEntity<?> directoryUpdate(
        @RequestParam(name = "directory") String updatedName,
        @RequestParam(name = "description") String updatedDescription,
        @PathVariable UUID targetDirectoryID,
        @RequestParam(name = "conflict", defaultValue = "default", required = false) String onConflict) {

        String errorMessage = null;
        StorageTreeNode returnValue = null;
        HttpStatus status = HttpStatus.OK;
        //TODO: rework!

        try {
            returnValue = storageService.updateDirectoryStorageTreeNode(updatedName, updatedDescription, targetDirectoryID, onConflict);
            return new ResponseEntity<>(returnValue, status);
        } catch (Exception directoryUpdateException) {
            if (directoryUpdateException instanceof FileAlreadyExistsException) {
                status = HttpStatus.CONFLICT;
            } else {
                status = HttpStatus.EXPECTATION_FAILED;
            }

            if (debugPrintStackTrace) {directoryUpdateException.printStackTrace();}
            errorMessage = "Failed to update '%s' directory! Error: %s".formatted(targetDirectoryID.toString(), directoryUpdateException.getMessage());
            return new ResponseEntity<>(new StorageServiceResponse(errorMessage), status);
        }
    }

    /**
     * @param targetDirectoryID UUID of the directory to copy
     * @param destinationDirectoryID UUID of the directory to copy to
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageServiceResponse}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping("/copy/directory/{targetDirectoryID}/to/{destinationDirectoryID}")
    public ResponseEntity<?> copyDirectory(
        @PathVariable UUID targetDirectoryID,
        @PathVariable UUID destinationDirectoryID) {

        try {
            final StorageServiceResponse response = storageService.transferDirectoryStorageTreeNode(targetDirectoryID, destinationDirectoryID, true);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            if (debugPrintStackTrace) {
                exception.printStackTrace();
            }

            final StorageServiceError error = new StorageServiceError("Failed to copy directory '%s' to '%s' directory! Error: %s".formatted(targetDirectoryID, destinationDirectoryID, exception.getMessage()));
            return new ResponseEntity<StorageServiceError>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetDirectoryID UUID of the directory to move
     * @param destinationDirectoryID UUID of the directory to move to
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageServiceResponse}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @PostMapping("/move/directory/{targetDirectoryID}/to/{destinationDirectoryID}")
    public ResponseEntity<?> moveDirectory(
        @PathVariable UUID targetDirectoryID,
        @PathVariable UUID destinationDirectoryID) {

        try {
            final StorageServiceResponse response = storageService.transferDirectoryStorageTreeNode(targetDirectoryID, destinationDirectoryID, false);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            if (debugPrintStackTrace) {
                exception.printStackTrace();
            }

            final StorageServiceError error = new StorageServiceError("Failed to move directory '%s' to '%s' directory! Error: %s".formatted(targetDirectoryID, destinationDirectoryID, exception.getMessage()));
            return new ResponseEntity<StorageServiceError>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetDirectoryID UUID of the directory to delete
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageServiceResponse}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @DeleteMapping("/delete/directory/{targetDirectoryID}")
    public ResponseEntity<?> deleteDirectory(
        @PathVariable UUID targetDirectoryID) {

        try {
            final StorageServiceResponse response = storageService.deleteDirectoryStorageTreeNode(targetDirectoryID);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            if (debugPrintStackTrace) {
                exception.printStackTrace();
            }

            final StorageServiceError error = new StorageServiceError("Directory '%s' could not be removed: %s".formatted(targetDirectoryID.toString(), exception.getMessage()));
            return new ResponseEntity<StorageServiceError>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param targetDirectoryID UUID of the directory to download
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageServiceResponse}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @GetMapping("/download/directory/{targetDirectoryID}")
    public ResponseEntity<?> downloadDirectory(
        @PathVariable UUID targetDirectoryID) {

        try {
            final StorageTreeNode response = storageService.downloadDirectoryStorageTreeNode(targetDirectoryID);
            return new ResponseEntity<StorageTreeNode>(response, HttpStatus.OK);
        } catch (Exception exception) {
            if (debugPrintStackTrace) {
                exception.printStackTrace();
            }

            final StorageServiceError error = new StorageServiceError("Directory '%s' could not be download: %s".formatted(targetDirectoryID.toString(), exception.getMessage()));
            return new ResponseEntity<StorageServiceError>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    //***************************************************//
    //*                                                 *//
    //*   From here on, it be all about THEM tasks!     *//
    //*                                                 *//
    //***************************************************//

    /**
     * @param taskID UUID of the task to execute
     * @param onException directive to dictate {@code on-exception action}
     * @param onExceptionIsPersistent whether the aforementioned {@code on-exception action} applies to just this or all {@code StorageTreeNodes} similar to it 
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageServiceResponse}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @GetMapping("/execute/task/{taskID}")
    public ResponseEntity<?> executeTask(
        @PathVariable(name = "taskID", required = true) UUID taskID,
        @RequestParam(name = "exception", required = false) String onException,
        @RequestParam(name = "persistent", required = false) Boolean onExceptionIsPersistent) {

        try {
            final StorageServiceResponse response = storageService.executeStorageTask(taskID, onException, onExceptionIsPersistent, false);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            if (debugPrintStackTrace) {
                exception.printStackTrace();
            }

            final StorageServiceError error = new StorageServiceError("Task '%s' could not be executed: %s".formatted(taskID.toString(), exception.getMessage()));
            return new ResponseEntity<StorageServiceError>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param taskID UUID of the task to cancel
     * @return <b>{@code ResponseEntity}</b> wrapping a <b>{@code StorageServiceResponse}</b> containing the appropriate <b><i>{@code StorageTreeNode(s)}</i></b> or a <b>{@code StorageServiceError}</b> containing the <b><i>{@code !error message!}</i></b>
     */
    @GetMapping("/cancel/task/{taskID}")
    public ResponseEntity<?> cancelTask(
        @PathVariable(name = "taskID", required = true) UUID taskID) {

        try {
            final StorageServiceResponse response = storageService.executeStorageTask(taskID, null, null, true);
            return new ResponseEntity<StorageServiceResponse>(response, HttpStatus.OK);
        } catch (Exception exception) {
            if (debugPrintStackTrace) {
                exception.printStackTrace();
            }

            final StorageServiceError error = new StorageServiceError("Task '%s' could not be canceled: %s".formatted(taskID.toString(), exception.getMessage()));
            return new ResponseEntity<StorageServiceError>(error, HttpStatus.INTERNAL_SERVER_ERROR);
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

        String errorMessage = null;
        StorageTreeNode returnValue = null;
        HttpStatus status = HttpStatus.OK;
        //TODO: rework!

        try {
            returnValue = storageService.downloadStorageTreeRootNode();
            return new ResponseEntity<>(returnValue, status);
        } catch (Exception rootDownloadException) {
            status = HttpStatus.EXPECTATION_FAILED;

            if (debugPrintStackTrace) {rootDownloadException.printStackTrace();}
            errorMessage = "Failed to download the *ROOT* directory of the permanent storage! Error: %s".formatted(rootDownloadException.getMessage());
            return new ResponseEntity<>(new StorageServiceResponse(errorMessage), status);
        }
    }
}