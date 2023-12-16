package com.unulearner.backend.services.files.storage;

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
import org.springframework.beans.factory.annotation.Autowired;

import com.unulearner.backend.services.files.storage.responses.StorageControllerResponseMessage;

@Controller
@RequestMapping(path="/storage")
public class FilesStorageServiceController {

    @Autowired
    FilesStorageServiceImplementation storageService;

    @PostMapping("/file/add")
    public ResponseEntity<?> uploadFile(
            @RequestParam("parent") Long parentNodeId,
            @RequestParam("content") MultipartFile content,
            @RequestParam("description") String description) {

        String message = "";
        try {
            FilesStorageNode response = storageService.saveFile(content, parentNodeId, description);

            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            message = "Could not upload the file: " + content.getOriginalFilename() + ". Error: " + e.getMessage();
            return new ResponseEntity<>(new StorageControllerResponseMessage(message), HttpStatus.EXPECTATION_FAILED);
        }
    }

    @GetMapping("/file/get/{fileId}")
    @ResponseBody
    public ResponseEntity<?> getFile(@PathVariable Long fileId) {

        String message = "";
        try {
            Resource file = storageService.getFile(fileId);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                    .body(file);
        } catch (Exception e) {
            message = "Could not get the file: " + fileId + ". Error: " + e.getMessage();
            return new ResponseEntity<>(new StorageControllerResponseMessage(message), HttpStatus.EXPECTATION_FAILED);
        }
    }

    @DeleteMapping("/file/delete/{fileId}")
    public ResponseEntity<StorageControllerResponseMessage> deleteFile(@PathVariable Long fileId) {
        
        String message = "";
        try {
            if (fileId == null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            storageService.deleteFile(fileId);

            message = "Removed the file successfully: " + fileId;
            return new ResponseEntity<>(new StorageControllerResponseMessage(message), HttpStatus.OK);
        } catch (Exception e) {
            message = "Could not remove the file: " + fileId + ". Error: " + e.getMessage();
            return new ResponseEntity<>(new StorageControllerResponseMessage(message), HttpStatus.EXPECTATION_FAILED);
        }
    }

    @PostMapping("/directory/add")
    public ResponseEntity<?> addDirectory(
            @RequestParam("parent") Long parentNodeId,
            @RequestParam("directory") String directoryName,
            @RequestParam("description") String description) {

        String message = "";
        try {
            FilesStorageNode response = storageService.saveDirectory(parentNodeId, directoryName, description);

            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            message = "Could not create the directory: " + directoryName + ". Error: " + e.getMessage();
            return new ResponseEntity<>(new StorageControllerResponseMessage(message), HttpStatus.EXPECTATION_FAILED);
        }
    }

    @GetMapping("/directory/get/root")
    public ResponseEntity<?> getDirectory(
        @RequestParam(name = "diagnostics", defaultValue = "false", required = false) Boolean diagnostics,
        @RequestParam(name = "recovery", defaultValue = "false", required = false) Boolean recovery
    ) {
    
        String message = "";
        try {
            FilesStorageNode response = storageService.getDirectory(null, diagnostics, recovery);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            message = "Could not get the root directory. Error: " + e.getMessage();
            return new ResponseEntity<>(new StorageControllerResponseMessage(message), HttpStatus.EXPECTATION_FAILED);
        }
    }

    @GetMapping("/directory/get/{directoryId}")
    public ResponseEntity<?> getRootDirectory(@PathVariable Long directoryId,
        @RequestParam(name = "diagnostics", defaultValue = "false", required = false) Boolean diagnostics,
        @RequestParam(name = "recovery", defaultValue = "false", required = false) Boolean recovery
    ) {
    
        String message = "";
        try {
            if (directoryId == null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            FilesStorageNode response = storageService.getDirectory(directoryId, diagnostics, recovery);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            message = "Could not get the directory: " + directoryId + ". Error: " + e.getMessage();
            return new ResponseEntity<>(new StorageControllerResponseMessage(message), HttpStatus.EXPECTATION_FAILED);
        }
    }

    @DeleteMapping("/directory/delete/{directoryId}")
    public ResponseEntity<StorageControllerResponseMessage> deleteDirectory(@PathVariable Long directoryId) {
        
        String message = "";
        try {
            if (directoryId == null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            storageService.deleteDirectory(directoryId);

            message = "Removed the directory successfully: " + directoryId;
            return new ResponseEntity<>(new StorageControllerResponseMessage(message), HttpStatus.OK);
        } catch (Exception e) {
            message = "Could not remove the directory: " + directoryId + ". Error: " + e.getMessage();
            return new ResponseEntity<>(new StorageControllerResponseMessage(message), HttpStatus.EXPECTATION_FAILED);
        }
    }
}