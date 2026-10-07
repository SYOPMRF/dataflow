package com.dataflow.backend.service;

import com.dataflow.backend.dto.AnalysisResult;
import com.dataflow.backend.entity.AnalysisResultEntity;
import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.FileColumn;
import com.dataflow.backend.repository.AnalysisResultRepository;
import com.dataflow.backend.repository.FileColumnRepository;
import com.dataflow.backend.repository.FileRepository;
import org.springframework.stereotype.Service;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.dataflow.backend.enums.FileStatus;
import com.dataflow.backend.exception.ResourceNotFoundException;
import com.dataflow.backend.exception.FileProcessingException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FileAnalysisService {

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final FileColumnRepository fileColumnRepository;
    private final DataServiceClient dataServiceClient;
    private final FileStorageService fileStorageService;

    public FileAnalysisService(
            FileRepository fileRepository,
            UserRepository userRepository,
            AnalysisResultRepository analysisResultRepository,
            FileColumnRepository fileColumnRepository,
            DataServiceClient dataServiceClient,
            FileStorageService fileStorageService
    ) {
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.fileColumnRepository = fileColumnRepository;
        this.dataServiceClient = dataServiceClient;
        this.fileStorageService = fileStorageService;
    }

    public AnalysisResult analyzeFile(Long fileId) throws IOException {

    Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();

    String email = authentication.getName();

    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

    File file = fileRepository.findByIdAndUser(fileId, user)
            .orElseThrow(() -> new ResourceNotFoundException("File not found"));

    if (file.getStatus() == FileStatus.COMPLETED) {
        throw new IllegalArgumentException("File has already been processed");
    }

    if (file.getStatus() == FileStatus.PROCESSING) {
        throw new IllegalArgumentException("File is already being processed");
    }

    file.setStatus(FileStatus.PROCESSING);
    fileRepository.save(file);

    try {

        Path filePath = fileStorageService.getFilePath(file.getStoredName());

        byte[] fileContent = Files.readAllBytes(filePath);

        AnalysisResult analysisResult = dataServiceClient.analyzeFile(
                fileContent,
                file.getOriginalName()
        );

        AnalysisResultEntity analysisEntity = new AnalysisResultEntity();

        analysisEntity.setFile(file);
        analysisEntity.setRowCount(analysisResult.rowCount());
        analysisEntity.setColumnCount(analysisResult.columnCount());
        analysisEntity.setMissingCount(analysisResult.missingCount());
        analysisEntity.setDuplicateCount(analysisResult.duplicateCount());

        analysisResultRepository.save(analysisEntity);

        for (var column : analysisResult.columns()) {

            FileColumn columnEntity = new FileColumn();

            columnEntity.setFile(file);
            columnEntity.setName(column.name());
            columnEntity.setDataType(column.dataType());
            columnEntity.setNullCount(column.nullCount());
            columnEntity.setUniqueCount(column.uniqueCount());
            columnEntity.setUniquePercentage(column.uniquePercentage());
            columnEntity.setDuplicateCount(column.duplicateCount());
            columnEntity.setDuplicatedUniqueCount(column.duplicatedUniqueCount());
            columnEntity.setMissingPercentage(column.missingPercentage());
            columnEntity.setCompletelyEmpty(column.isCompletelyEmpty());

            columnEntity.setMinValue(
                    column.minValue() != null
                            ? column.minValue().toString()
                            : null
            );

            columnEntity.setMaxValue(
                    column.maxValue() != null
                            ? column.maxValue().toString()
                            : null
            );

            columnEntity.setMeanValue(column.meanValue());
            columnEntity.setMedianValue(column.medianValue());
            columnEntity.setStdDeviation(column.stdDeviation());
            columnEntity.setZeroCount(column.zeroCount());
            columnEntity.setNegativeCount(column.negativeCount());

            fileColumnRepository.save(columnEntity);
        }

        file.setStatus(FileStatus.COMPLETED);
        file.setProcessedAt(java.time.LocalDateTime.now());

        fileRepository.save(file);

        return analysisResult;

    } catch (Exception exception) {

        file.setStatus(FileStatus.FAILED);
        fileRepository.save(file);

        throw new FileProcessingException(
                "Failed to process file",
                exception
        );
    }
}
}