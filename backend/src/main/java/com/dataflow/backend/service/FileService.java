package com.dataflow.backend.service;

import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.enums.FileStatus;
import com.dataflow.backend.repository.FileRepository;
import org.springframework.stereotype.Service;
import com.dataflow.backend.exception.ResourceNotFoundException;
import com.dataflow.backend.service.FileStorageService;

import java.util.List;

@Service
public class FileService {

    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;

    public FileService(
            FileRepository fileRepository,
            FileStorageService fileStorageService
    ) {
        this.fileRepository = fileRepository;
        this.fileStorageService = fileStorageService;
    }

    public File createFile(
            User user,
            String originalName,
            String storedName,
            String fileType,
            Long fileSize
    ) {
        File file = new File();

        file.setUser(user);
        file.setOriginalName(originalName);
        file.setStoredName(storedName);
        file.setFileType(fileType);
        file.setFileSize(fileSize);
        file.setStatus(FileStatus.UPLOADED);

        try {
            return fileRepository.save(file);
        } catch (RuntimeException exception) {
            try {
                fileStorageService.delete(storedName);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            throw exception;
        }
    }

    public List<File> getUserFiles(User user) {
        return fileRepository.findAllByUserOrderByCreatedAtDesc(user);
    }

    public File getUserFile(Long fileId, User user) {

        return fileRepository.findByIdAndUser(fileId, user)
            .orElseThrow(() ->
                    new ResourceNotFoundException("File not found")
            );
    }

    public void deleteFile(Long fileId, User user) {
        File file = getUserFile(fileId, user);

        fileStorageService.delete(file.getStoredName());

        fileRepository.delete(file);
    }
}