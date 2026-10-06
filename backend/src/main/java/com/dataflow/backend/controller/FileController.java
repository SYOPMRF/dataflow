package com.dataflow.backend.controller;

import com.dataflow.backend.dto.FileResponse;
import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.service.FileService;
import com.dataflow.backend.service.FileStorageService;
import com.dataflow.backend.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final FileStorageService fileStorageService;
    private final UserService userService;

    public FileController(
            FileService fileService,
            FileStorageService fileStorageService,
            UserService userService
    ) {
        this.fileService = fileService;
        this.fileStorageService = fileStorageService;
        this.userService = userService;
    }

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse uploadFile(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);

        FileStorageService.StoredFile storedFile =
                fileStorageService.store(file);

        File savedFile = fileService.createFile(
                user,
                storedFile.originalName(),
                storedFile.storedName(),
                storedFile.fileType(),
                storedFile.fileSize()
        );

        return FileResponse.fromEntity(savedFile);
    }

    @GetMapping
    public List<FileResponse> getFiles(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);

        return fileService.getUserFiles(user)
                .stream()
                .map(FileResponse::fromEntity)
                .toList();
    }

    @GetMapping("/{id}")
    public FileResponse getFile(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);

        File file = fileService.getUserFile(id, user);

        return FileResponse.fromEntity(file);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFile(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);

        fileService.deleteFile(id, user);
    }

    private User getAuthenticatedUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}