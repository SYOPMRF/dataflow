package com.dataflow.backend.service;

import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.enums.FileStatus;
import com.dataflow.backend.repository.FileRepository;
import org.springframework.stereotype.Service;
import com.dataflow.backend.exception.ResourceNotFoundException;

import java.util.List;

@Service
public class FileService {

    private final FileRepository fileRepository;

    public FileService(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
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

        return fileRepository.save(file);
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

        fileRepository.delete(file);
    }
}