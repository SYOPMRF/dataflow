package com.dataflow.backend.service;

import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.enums.FileStatus;
import com.dataflow.backend.exception.ResourceNotFoundException;
import com.dataflow.backend.repository.AnalysisResultRepository;
import com.dataflow.backend.repository.DataQualityIssueRepository;
import com.dataflow.backend.repository.FileColumnRepository;
import com.dataflow.backend.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);
    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final AnalysisResultRepository analysisResultRepository;
    private final FileColumnRepository fileColumnRepository;
    private final DataQualityIssueRepository dataQualityIssueRepository;

    public FileService(
            FileRepository fileRepository,
            FileStorageService fileStorageService,
            AnalysisResultRepository analysisResultRepository,
            FileColumnRepository fileColumnRepository,
            DataQualityIssueRepository dataQualityIssueRepository) {
        this.fileRepository = fileRepository;
        this.fileStorageService = fileStorageService;
        this.analysisResultRepository = analysisResultRepository;
        this.fileColumnRepository = fileColumnRepository;
        this.dataQualityIssueRepository = dataQualityIssueRepository;
    }

    public File createFile(
            User user,
            String originalName,
            String storedName,
            String fileType,
            Long fileSize) {
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
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
    }

    @Transactional
    public void deleteFile(Long fileId, User user) {
        File file = getUserFile(fileId, user);
        String storedName = file.getStoredName();

        dataQualityIssueRepository.deleteAllByFileId(fileId);
        fileColumnRepository.deleteAllByFileId(fileId);
        analysisResultRepository.deleteAllByFileId(fileId);

        fileRepository.delete(file);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            fileStorageService.delete(storedName);
                        } catch (RuntimeException exception) {
                            logger.error(
                                    "File record was deleted, but physical file cleanup failed. Stored name: {}",
                                    storedName,
                                    exception);
                        }
                    }
                });
    }
}
