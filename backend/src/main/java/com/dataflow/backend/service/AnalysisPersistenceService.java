package com.dataflow.backend.service;

import com.dataflow.backend.dto.AnalysisResult;
import com.dataflow.backend.entity.AnalysisResultEntity;
import com.dataflow.backend.entity.DataQualityIssueEntity;
import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.FileColumn;
import com.dataflow.backend.repository.AnalysisResultRepository;
import com.dataflow.backend.repository.DataQualityIssueRepository;
import com.dataflow.backend.repository.FileColumnRepository;
import com.dataflow.backend.repository.FileRepository;
import com.dataflow.backend.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AnalysisPersistenceService {

    private static final String VALUE_FORMAT_PREFIX = "DFJSON1:";

    private final FileRepository fileRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final FileColumnRepository fileColumnRepository;
    private final DataQualityIssueRepository dataQualityIssueRepository;
    private final ObjectMapper objectMapper;

    public AnalysisPersistenceService(
            FileRepository fileRepository,
            AnalysisResultRepository analysisResultRepository,
            FileColumnRepository fileColumnRepository,
            DataQualityIssueRepository dataQualityIssueRepository,
            ObjectMapper objectMapper) {

        this.fileRepository = fileRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.fileColumnRepository = fileColumnRepository;
        this.dataQualityIssueRepository = dataQualityIssueRepository;
        this.objectMapper = objectMapper;
    }

    private String serializeValue(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return VALUE_FORMAT_PREFIX + objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize column analysis value",
                    exception);
        }
    }

    @Transactional
    public void persistAnalysis(Long fileId, AnalysisResult analysisResult) {
        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

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
            columnEntity.setMinValue(serializeValue(column.minValue()));
            columnEntity.setMaxValue(serializeValue(column.maxValue()));
            columnEntity.setMeanValue(column.meanValue());
            columnEntity.setMedianValue(column.medianValue());
            columnEntity.setStdDeviation(column.stdDeviation());
            columnEntity.setZeroCount(column.zeroCount());
            columnEntity.setNegativeCount(column.negativeCount());

            fileColumnRepository.save(columnEntity);
        }

        for (var issue : analysisResult.qualityIssues()) {
            DataQualityIssueEntity issueEntity = new DataQualityIssueEntity();

            issueEntity.setFile(file);
            issueEntity.setColumnName(issue.columnName());
            issueEntity.setIssueType(issue.issueType());
            issueEntity.setSeverity(issue.severity());
            issueEntity.setMessage(issue.message());

            dataQualityIssueRepository.save(issueEntity);
        }

        file.setStatus(com.dataflow.backend.enums.FileStatus.COMPLETED);
        file.setProcessedAt(LocalDateTime.now());
        file.setProcessingStartedAt(null);
        fileRepository.save(file);
    }
}