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
import com.unulearner.backend.storage.responses.StorageServiceResponse;

@Controller
@RequestMapping(path = "/storage")
public class StorageServiceController {

    @Autowired
    StorageServiceImplementation storageService;

    private final Boolean debugPrintStackTrace = true;

    /**
     * @param content
     * @param description
     * @param destinationDirectoryID
     * @param onConflict
     * @return
     */
    @PostMapping("/file/add/to/{destinationDirectoryID}")
    public ResponseEntity<?> fileUpload(
        @RequestParam(name = "content") MultipartFile content,
        @RequestParam(name = "description") String description,
        @PathVariable("destinationDirectoryID") UUID destinationDirectoryID,
        @RequestParam(name = "conflict", defaultValue = "default", required = false) String onConflict) {

        String errorMessage = null;
        StorageTreeNode returnValue = null;
        HttpStatus status = HttpStatus.CREATED;

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
            errorMessage = String.format("Failed to upload file '%s' to '%s' directory! Error: %s", content.getOriginalFilename(), destinationDirectoryID.toString(), fileUploadException.getMessage());
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
        @RequestParam(name = "content", required = false) MultipartFile content,
        @PathVariable("targetFileID") UUID targetFileID,
        @RequestParam(name = "conflict", defaultValue = "default", required = false) String onConflict) {

        String errorMessage = null;
        StorageTreeNode returnValue = null;
        HttpStatus status = HttpStatus.OK;

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
            errorMessage = String.format("Failed to update '%s' file! Error: %s", targetFileID.toString(), fileUpdateException.getMessage());
            return new ResponseEntity<>(new StorageServiceResponse(errorMessage), status);
        }
    }

    /**
     * @param targetFileID
     * @param destinationDirectoryID
     * @param onConflict
     * @return
     */
    @PostMapping("/file/copy/{targetFileID}/to/{destinationDirectoryID}")
    public ResponseEntity<?> fileCopy(
        @PathVariable("targetFileID") UUID targetFileID,
        @PathVariable("destinationDirectoryID") UUID destinationDirectoryID,
        @RequestParam(name = "conflict", defaultValue = "default", required = false) String onConflict) {

        String errorMessage = null;
        StorageTreeNode returnValue = null;
        HttpStatus status = HttpStatus.OK;

        try {
            returnValue = storageService.transferFileStorageTreeNode(targetFileID, destinationDirectoryID, true, onConflict);
            return new ResponseEntity<>(returnValue, status);
        } catch (Exception fileCopyException) {
            if (fileCopyException instanceof FileAlreadyExistsException) {
                status = HttpStatus.CONFLICT;
            } else {
                status = HttpStatus.EXPECTATION_FAILED;
            }

            if (debugPrintStackTrace) {fileCopyException.printStackTrace();}
            errorMessage = String.format("Failed to copy file '%s' to '%s' directory! Error: %s", targetFileID.toString(), destinationDirectoryID.toString(), fileCopyException.getMessage());
            return new ResponseEntity<>(new StorageServiceResponse(errorMessage), status);
        }
    }

    /**
     * @param targetFileID
     * @param destinationDirectoryID
     * @param onConflict
     * @return
     */
    @PostMapping("/file/move/{targetFileID}/to/{destinationDirectoryID}")
    public ResponseEntity<?> fileMove(
        @PathVariable("targetFileID") UUID targetFileID,
        @PathVariable("destinationDirectoryID") UUID destinationDirectoryID,
        @RequestParam(name = "conflict", defaultValue = "default", required = false) String onConflict) {

        String errorMessage = null;
        StorageTreeNode returnValue = null;
        HttpStatus status = HttpStatus.OK;

        try {
            returnValue = storageService.transferFileStorageTreeNode(targetFileID, destinationDirectoryID, false, onConflict);
            return new ResponseEntity<>(returnValue, status);
        } catch (Exception fileMoveException) {
            if (fileMoveException instanceof FileAlreadyExistsException) {
                status = HttpStatus.CONFLICT;
            } else {
                status = HttpStatus.EXPECTATION_FAILED;
            }

            if (debugPrintStackTrace) {fileMoveException.printStackTrace();}
            errorMessage = String.format("Failed to move file '%s' to '%s' directory! Error: %s", targetFileID.toString(), destinationDirectoryID.toString(), fileMoveException.getMessage());
            return new ResponseEntity<>(new StorageServiceResponse(errorMessage), status);
        }
    }

    /**
     * @param targetFileID
     * @return
     */
    @ResponseBody
    @GetMapping("/file/download/{targetFileID}")
    public ResponseEntity<?> fileDownload(
        @PathVariable("targetFileID") UUID targetFileID) {

        String errorMessage = null;
        Resource returnValue = null;
        HttpStatus status = HttpStatus.OK;

        try {
            returnValue = storageService.downloadFileStorageTreeNode(targetFileID);
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + returnValue.getFilename() + "\"").body(returnValue);
        } catch (Exception fileDownloadException) {
            status = HttpStatus.EXPECTATION_FAILED;

            if (debugPrintStackTrace) {fileDownloadException.printStackTrace();}
            errorMessage = String.format("Failed to download file '%s' from the permanent storage! Error: %s", targetFileID.toString(), fileDownloadException.getMessage());
            return new ResponseEntity<>(new StorageServiceResponse(errorMessage), status);
        }
    }

    /**
     * @param targetFileID
     * @return
     */
    @DeleteMapping("/file/delete/{targetFileID}")
    public ResponseEntity<?> fileDelete(
        @PathVariable("targetFileID") UUID targetFileID) {

        String errorMessage = null;
        String successMessage = null;
        HttpStatus status = HttpStatus.OK;

        try {
            storageService.deleteFileStorageTreeNode(targetFileID);
            successMessage = String.format("File '%s' removed successfully!", targetFileID.toString());
            return new ResponseEntity<>(new StorageServiceResponse(successMessage), status);
        } catch (Exception fileDeleteException) {
            status = HttpStatus.EXPECTATION_FAILED;

            if (debugPrintStackTrace) {fileDeleteException.printStackTrace();}
            errorMessage = String.format("Failed to remove file '%s' from the permanent storage! Error: %s", targetFileID.toString(), fileDeleteException.getMessage());
            return new ResponseEntity<>(new StorageServiceResponse(errorMessage), status);
        }
    }

    /**
     * @param directory
     * @param description
     * @param destinationDirectoryID
     * @param onConflict
     * @return
     */
    @PostMapping("/directory/add/to/{destinationDirectoryID}")
    public ResponseEntity<?> directoryCreate(
        @RequestParam(name = "directory") String directory,
        @RequestParam(name = "description") String description,
        @PathVariable("destinationDirectoryID") UUID destinationDirectoryID,
        @RequestParam(name = "conflict", defaultValue = "default", required = false) String onConflict) {

        String errorMessage = null;
        StorageTreeNode returnValue = null;
        HttpStatus status = HttpStatus.CREATED;

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
            errorMessage = String.format("Failed to create new directory '%s' and add it to '%s' directory! Error: %s", directory, destinationDirectoryID.toString(), directoryCreateException.getMessage());
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
        @PathVariable(name = "targetDirectoryID") UUID targetDirectoryID,
        @RequestParam(name = "conflict", defaultValue = "default", required = false) String onConflict) {

        String errorMessage = null;
        StorageTreeNode returnValue = null;
        HttpStatus status = HttpStatus.OK;

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
            errorMessage = String.format("Failed to update '%s' directory! Error: %s", targetDirectoryID.toString(), directoryUpdateException.getMessage());
            return new ResponseEntity<>(new StorageServiceResponse(errorMessage), status);
        }
    }

    /**
     * @param targetDirectoryID
     * @param destinationDirectoryID
     * @param onConflict
     * @return
     */
    @PostMapping("/directory/copy/{targetDirectoryID}/to/{destinationDirectoryID}")
    public ResponseEntity<?> directoryCopy(
        @PathVariable("targetDirectoryID") UUID targetDirectoryID,
        @PathVariable("destinationDirectoryID") UUID destinationDirectoryID,
        @RequestParam(name = "conflict", defaultValue = "default", required = false) String onConflict) {

        String errorMessage = null;
        StorageTreeNode returnValue = null;
        HttpStatus status = HttpStatus.OK;

        try {
            returnValue = storageService.transferDirectoryStorageTreeNode(targetDirectoryID, destinationDirectoryID, true, onConflict);
            return new ResponseEntity<>(returnValue, status);
        } catch (Exception directoryCopyException) {
            if (directoryCopyException instanceof FileAlreadyExistsException) {
                status = HttpStatus.CONFLICT;
            } else {
                status = HttpStatus.EXPECTATION_FAILED;
            }

            if (debugPrintStackTrace) {directoryCopyException.printStackTrace();}
            errorMessage = String.format("Failed to copy directory '%s' to '%s' directory! Error: %s", targetDirectoryID, destinationDirectoryID, directoryCopyException.getMessage());
            return new ResponseEntity<>(new StorageServiceResponse(errorMessage), status);
        }
    }

    /**
     * @param targetDirectoryID
     * @param destinationDirectoryID
     * @param onConflict
     * @return
     */
    @PostMapping("/directory/move/{targetDirectoryID}/to/{destinationDirectoryID}")
    public ResponseEntity<?> directoryMove(
        @PathVariable("targetDirectoryID") UUID targetDirectoryID,
        @PathVariable("destinationDirectoryID") UUID destinationDirectoryID,
        @RequestParam(name = "conflict", defaultValue = "default", required = false) String onConflict) {

        String errorMessage = null;
        StorageTreeNode returnValue = null;
        HttpStatus status = HttpStatus.OK;

        try {
            returnValue = storageService.transferDirectoryStorageTreeNode(targetDirectoryID, destinationDirectoryID, false, onConflict);
            return new ResponseEntity<>(returnValue, status);
        } catch (Exception directoryMoveException) {
            if (directoryMoveException instanceof FileAlreadyExistsException) {
                status = HttpStatus.CONFLICT;
            } else {
                status = HttpStatus.EXPECTATION_FAILED;
            }

            if (debugPrintStackTrace) {directoryMoveException.printStackTrace();}
            errorMessage = String.format("Failed to move directory '%s' to '%s' directory! Error: %s", targetDirectoryID, destinationDirectoryID, directoryMoveException.getMessage());
            return new ResponseEntity<>(new StorageServiceResponse(errorMessage), status);
        }
    }

    /**
     * @param targetDirectoryID
     * @return
     */
    @GetMapping("/directory/download/{targetDirectoryID}")
    public ResponseEntity<?> directoryDownload(
        @PathVariable("targetDirectoryID") UUID targetDirectoryID) {

        String errorMessage = null;
        StorageTreeNode returnValue = null;
        HttpStatus status = HttpStatus.OK;

        try {
            returnValue = storageService.downloadDirectoryStorageTreeNode(targetDirectoryID);
            return new ResponseEntity<>(returnValue, status);
        } catch (Exception directoryDownloadException) {
            status = HttpStatus.EXPECTATION_FAILED;

            if (debugPrintStackTrace) {directoryDownloadException.printStackTrace();}
            errorMessage = String.format("Failed to download directory '%s' from the permanent storage! Error: %s", targetDirectoryID.toString(), directoryDownloadException.getMessage());
            return new ResponseEntity<>(new StorageServiceResponse(errorMessage), status);
        }
    }

    /**
     * @param targetDirectoryID
     * @return
     */
    @DeleteMapping("/directory/delete/{targetDirectoryID}")
    public ResponseEntity<StorageServiceResponse> directoryDelete(
        @PathVariable("targetDirectoryID") UUID targetDirectoryID,
        @RequestParam(name = "task", required = false) UUID taskToResumeID,
        @RequestParam(name = "onxception", required = false) String onException,
        @RequestParam(name = "onexceptionpersist", required = false) Boolean onExceptionIsPersistable) {

        HttpStatus status = null;

        try {
            final StorageServiceResponse response = storageService.deleteDirectoryStorageTreeNode(targetDirectoryID, taskToResumeID, onException, onExceptionIsPersistable);
            
            if ((status = response.getStatus()) == null) {
                status = HttpStatus.OK;
            }

            return new ResponseEntity<>(response, status);
        } catch (Exception exception) {
            if (debugPrintStackTrace) {
                exception.printStackTrace();
            }

            status = HttpStatus.INTERNAL_SERVER_ERROR;
            final String errorMessage = String.format("Directory '%s' could not be removed: %s", targetDirectoryID.toString(), exception.getMessage());
            final StorageServiceResponse error = new StorageServiceResponse(errorMessage, status, null, null, null, null, null);
            return new ResponseEntity<>(error, status);
        }
    }

    /**
     * @return
     */
    @GetMapping("/root/download")
    public ResponseEntity<?> rootDownload() {

        String errorMessage = null;
        StorageTreeNode returnValue = null;
        HttpStatus status = HttpStatus.OK;

        try {
            returnValue = storageService.downloadStorageTreeRootNode();
            return new ResponseEntity<>(returnValue, status);
        } catch (Exception rootDownloadException) {
            status = HttpStatus.EXPECTATION_FAILED;

            if (debugPrintStackTrace) {rootDownloadException.printStackTrace();}
            errorMessage = String.format("Failed to download the *ROOT* directory of the permanent storage! Error: %s", rootDownloadException.getMessage());
            return new ResponseEntity<>(new StorageServiceResponse(errorMessage), status);
        }
    }
}