package com.dataflow.backend.service;

import com.dataflow.backend.dto.AnalysisResult;
import com.dataflow.backend.dto.ColumnAnalysis;
import com.dataflow.backend.dto.DataQualityIssue;
import com.dataflow.backend.dto.DuplicateAnalysis;
import com.dataflow.backend.dto.DistributionAnalysis;
import com.dataflow.backend.dto.CorrelationAnalysis;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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

        private static final String VALUE_FORMAT_PREFIX = "DFJSON1:";
        private static final Logger logger = LoggerFactory.getLogger(FileAnalysisService.class);

        private final ObjectMapper objectMapper;

        public FileAnalysisService(
                        FileRepository fileRepository,
                        UserRepository userRepository,
                        AnalysisResultRepository analysisResultRepository,
                        FileColumnRepository fileColumnRepository,
                        DataServiceClient dataServiceClient,
                        FileStorageService fileStorageService,
                        DataQualityIssueRepository dataQualityIssueRepository,
                        AnalysisPersistenceService analysisPersistenceService,
                        ObjectMapper objectMapper) {

                this.fileRepository = fileRepository;
                this.userRepository = userRepository;
                this.analysisResultRepository = analysisResultRepository;
                this.fileColumnRepository = fileColumnRepository;
                this.dataServiceClient = dataServiceClient;
                this.fileStorageService = fileStorageService;
                this.dataQualityIssueRepository = dataQualityIssueRepository;
                this.analysisPersistenceService = analysisPersistenceService;
                this.objectMapper = objectMapper;
        }

        private Object deserializeValue(String value) {
                if (value == null) {
                        return null;
                }

                if (!value.startsWith(VALUE_FORMAT_PREFIX)) {
                        return value;
                }

                String jsonValue = value.substring(VALUE_FORMAT_PREFIX.length());

                try {
                        return objectMapper.readValue(jsonValue, Object.class);
                } catch (JsonProcessingException exception) {
                        throw new IllegalStateException(
                                        "Failed to deserialize column analysis value",
                                        exception);
                }
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
                        byte[] fileContent = fileStorageService.read(
                                        file.getStoredName(),
                                        file.getStorageProvider());

                        AnalysisResult analysisResult = dataServiceClient.analyzeFile(
                                        fileContent,
                                        file.getOriginalName());

                        analysisPersistenceService.persistAnalysis(fileId, analysisResult);
                        return analysisResult;

                } catch (Exception exception) {
                        logger.error("Failed to process file with id {}", fileId, exception);

                        fileRepository.markAsFailedIfProcessing(
                                        fileId,
                                        FileStatus.PROCESSING,
                                        FileStatus.FAILED);

                        throw new FileProcessingException(
                                        "Failed to process file",
                                        exception);
                }
        }

        private DuplicateAnalysis deserializeDuplicateAnalysis(
                        AnalysisResultEntity analysisEntity) {

                if (analysisEntity.getDuplicateAnalysis() == null) {
                        return DuplicateAnalysis.empty();
                }

                try {
                        return objectMapper.treeToValue(
                                        analysisEntity.getDuplicateAnalysis(),
                                        DuplicateAnalysis.class);
                } catch (JsonProcessingException exception) {
                        throw new IllegalStateException(
                                        "Failed to deserialize duplicate analysis",
                                        exception);
                }
        }

        private DistributionAnalysis deserializeDistributionAnalysis(
                        AnalysisResultEntity analysisEntity) {
                if (analysisEntity.getDistributionAnalysis() == null) {
                        return DistributionAnalysis.empty();
                }
                try {
                        return objectMapper.treeToValue(
                                        analysisEntity.getDistributionAnalysis(),
                                        DistributionAnalysis.class);
                } catch (JsonProcessingException exception) {
                        throw new IllegalStateException(
                                        "Failed to deserialize distribution analysis",
                                        exception);
                }
        }

        private CorrelationAnalysis deserializeCorrelationAnalysis(
                        AnalysisResultEntity analysisEntity) {
                if (analysisEntity.getCorrelationAnalysis() == null) {
                        return CorrelationAnalysis.empty();
                }
                try {
                        return objectMapper.treeToValue(
                                        analysisEntity.getCorrelationAnalysis(),
                                        CorrelationAnalysis.class);
                } catch (JsonProcessingException exception) {
                        throw new IllegalStateException(
                                        "Failed to deserialize correlation analysis",
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
                                                deserializeValue(column.getMinValue()),
                                                deserializeValue(column.getMaxValue()),
                                                column.getMeanValue(),
                                                column.getMedianValue(),
                                                column.getStdDeviation(),
                                                column.getZeroCount(),
                                                column.getNegativeCount(),
                                                column.getQ1Value(),
                                                column.getQ3Value(),
                                                column.getIqrValue(),
                                                column.getP5Value(),
                                                column.getP10Value(),
                                                column.getP90Value(),
                                                column.getP95Value(),
                                                column.getOutlierCount(),
                                                column.getZScoreOutlierCount()))
                                .toList();

                var qualityIssues = dataQualityIssueRepository.findAllByFileId(fileId)
                                .stream()
                                .map(issue -> new DataQualityIssue(
                                                issue.getColumnName(),
                                                issue.getIssueType(),
                                                issue.getSeverity(),
                                                issue.getMessage(),
                                                issue.getRelatedColumns(),
                                                issue.getAffectedRows()))
                                .toList();

                return new AnalysisResult(
                                analysisEntity.getRowCount(),
                                analysisEntity.getColumnCount(),
                                analysisEntity.getMissingCount(),
                                analysisEntity.getDuplicateCount(),
                                columns,
                                qualityIssues,
                                deserializeDuplicateAnalysis(analysisEntity),
                                deserializeDistributionAnalysis(analysisEntity),
                                deserializeCorrelationAnalysis(analysisEntity));
        }
}
