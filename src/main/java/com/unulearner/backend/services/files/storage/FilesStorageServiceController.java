package com.unulearner.backend.services.files.storage;

import java.util.List;
import java.util.stream.Collectors;

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
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import com.unulearner.backend.services.files.storage.helper.FileUploadResponseMessage;
import com.unulearner.backend.services.files.storage.helper.FileUploadDataModel;

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
            return ResponseEntity.status(HttpStatus.OK).body(new FileUploadResponseMessage(message));
        } catch (Exception e) {
            message = "Could not upload the file: " + file.getOriginalFilename() + ". Error: " + e.getMessage();
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(new FileUploadResponseMessage(message));
        }
    }

    @GetMapping("/files")
    public ResponseEntity<List<FileUploadDataModel>> getListFiles() {
        List<FileUploadDataModel> fileInfos = storageService.loadAll().map(path -> {
            String filename = path.getFileName().toString();
            String url = MvcUriComponentsBuilder
                    .fromMethodName(FilesStorageServiceController.class, "getFile", path.getFileName().toString()).build().toString();

            return new FileUploadDataModel(filename, url);
        }).collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.OK).body(fileInfos);
    }

    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> getFile(@PathVariable String filename) {
        Resource file = storageService.load(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                .body(file);
    }
}