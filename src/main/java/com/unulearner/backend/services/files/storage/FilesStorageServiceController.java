package com.unulearner.backend.services.files.storage;

import java.io.IOException;

//import org.springframework.web.bind.annotation.CrossOrigin;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;

import com.unulearner.backend.services.files.storage.helper.FileUploadResponseMessage;
import com.unulearner.backend.services.files.storage.tree.TreeRoot;

@Controller
public class FilesStorageServiceController {

    @Autowired
    FilesStorageServiceImplementation storageService;

    @PostMapping("/files/upload")
    public ResponseEntity<FileUploadResponseMessage> uploadFile(
                @RequestParam("file") MultipartFile file,
                @RequestParam("dir") String dir
            ) {
        String message = "";
        try {
            storageService.save(file, dir);

            message = "Uploaded the file successfully: " + file.getOriginalFilename();
            return new ResponseEntity<>(new FileUploadResponseMessage(message), HttpStatus.OK);
        } catch (Exception e) {
            message = "Could not upload the file: " + file.getOriginalFilename() + ". Error: " + e.getMessage();
            return new ResponseEntity<>(new FileUploadResponseMessage(message), HttpStatus.EXPECTATION_FAILED);
        }
    }

    @GetMapping("/files")
    public ResponseEntity<TreeRoot> getListFiles(
        @RequestParam(name="root", required = false, defaultValue = "/") String rootPath
    ) {
        try {
            TreeRoot root = storageService.loadAll(rootPath);

            return new ResponseEntity<>(root, HttpStatus.OK);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> getFile(@PathVariable String filename) {
        /* TODO: get the heads and tails of headers! */
        Resource file = storageService.load(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                .body(file);
    }
}