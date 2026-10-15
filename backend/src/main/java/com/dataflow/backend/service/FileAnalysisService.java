package com.dataflow.backend.service;

import com.dataflow.backend.dto.AnalysisResult;
import com.dataflow.backend.dto.ColumnAnalysis;
import com.dataflow.backend.dto.DataQualityIssue;
import com.dataflow.backend.entity.AnalysisResultEntity;
import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.FileColumn;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.enums.FileStatus;
import com.dataflow.backend.exception.FileProcessingException;
import com.dataflow.backend.exception.ConflictException;
import com.dataflow.backend.exception.ResourceNotFoundException;
import com.dataflow.backend.repository.AnalysisResultRepository;
import com.dataflow.backend.repository.DataQualityIssueRepository;
import com.dataflow.backend.repository.FileColumnRepository;
import com.dataflow.backend.repository.FileRepository;
import com.dataflow.backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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
        private final DataQualityIssueRepository dataQualityIssueRepository;
        private final AnalysisPersistenceService analysisPersistenceService;

        public FileAnalysisService(
                        FileRepository fileRepository,
                        UserRepository userRepository,
                        AnalysisResultRepository analysisResultRepository,
                        FileColumnRepository fileColumnRepository,
                        DataServiceClient dataServiceClient,
                        FileStorageService fileStorageService,
                        DataQualityIssueRepository dataQualityIssueRepository,
                        AnalysisPersistenceService analysisPersistenceService) {
                this.fileRepository = fileRepository;
                this.userRepository = userRepository;
                this.analysisResultRepository = analysisResultRepository;
                this.fileColumnRepository = fileColumnRepository;
                this.dataServiceClient = dataServiceClient;
                this.fileStorageService = fileStorageService;
                this.dataQualityIssueRepository = dataQualityIssueRepository;
                this.analysisPersistenceService = analysisPersistenceService;
        }

        public AnalysisResult analyzeFile(Long fileId) throws IOException {

                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                String email = authentication.getName();

                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                File file = fileRepository.findByIdAndUser(fileId, user)
                                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

                int claimedRows = fileRepository.claimForProcessing(
                                fileId,
                                user,
                                FileStatus.PROCESSING,
                                java.util.List.of(FileStatus.UPLOADED, FileStatus.FAILED));

                if (claimedRows == 0) {
                        if (file.getStatus() == FileStatus.COMPLETED) {
                                throw new ConflictException("File has already been processed");
                        }

                        throw new ConflictException("File is already being processed");
                }

                try {
                        Path filePath = fileStorageService.getFilePath(file.getStoredName());
                        byte[] fileContent = Files.readAllBytes(filePath);

                        AnalysisResult analysisResult = dataServiceClient.analyzeFile(
                                        fileContent,
                                        file.getOriginalName());

                        analysisPersistenceService.persistAnalysis(fileId, analysisResult);
                        return analysisResult;

                } catch (Exception exception) {
                        fileRepository.markAsFailedIfProcessing(
                                        fileId,
                                        FileStatus.PROCESSING,
                                        FileStatus.FAILED);

                        throw new FileProcessingException(
                                        "Failed to process file",
                                        exception);
                }
        }

        public AnalysisResult getAnalysis(Long fileId) {

                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                String email = authentication.getName();

                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                File file = fileRepository.findByIdAndUser(fileId, user)
                                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

                AnalysisResultEntity analysisEntity = analysisResultRepository.findByFileId(fileId)
                                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found"));

                var columns = fileColumnRepository.findAllByFileId(fileId)
                                .stream()
                                .map(column -> new ColumnAnalysis(
                                                column.getName(),
                                                column.getDataType(),
                                                column.getNullCount(),
                                                column.getUniqueCount(),
                                                column.getUniquePercentage(),
                                                column.getDuplicateCount(),
                                                column.getDuplicatedUniqueCount(),
                                                column.getMissingPercentage(),
                                                column.isCompletelyEmpty(),
                                                column.getMinValue(),
                                                column.getMaxValue(),
                                                column.getMeanValue(),
                                                column.getMedianValue(),
                                                column.getStdDeviation(),
                                                column.getZeroCount(),
                                                column.getNegativeCount()))
                                .toList();

                var qualityIssues = dataQualityIssueRepository.findAllByFileId(fileId)
                                .stream()
                                .map(issue -> new DataQualityIssue(
                                                issue.getColumnName(),
                                                issue.getIssueType(),
                                                issue.getSeverity(),
                                                issue.getMessage()))
                                .toList();

                return new AnalysisResult(
                                analysisEntity.getRowCount(),
                                analysisEntity.getColumnCount(),
                                analysisEntity.getMissingCount(),
                                analysisEntity.getDuplicateCount(),
                                columns,
                                qualityIssues);
        }
}
