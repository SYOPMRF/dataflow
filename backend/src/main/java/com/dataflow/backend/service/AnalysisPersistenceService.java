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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AnalysisPersistenceService {

    private final FileRepository fileRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final FileColumnRepository fileColumnRepository;
    private final DataQualityIssueRepository dataQualityIssueRepository;

    public AnalysisPersistenceService(
            FileRepository fileRepository,
            AnalysisResultRepository analysisResultRepository,
            FileColumnRepository fileColumnRepository,
            DataQualityIssueRepository dataQualityIssueRepository
    ) {
        this.fileRepository = fileRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.fileColumnRepository = fileColumnRepository;
        this.dataQualityIssueRepository = dataQualityIssueRepository;
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
            columnEntity.setMinValue(
                    column.minValue() != null ? column.minValue().toString() : null
            );
            columnEntity.setMaxValue(
                    column.maxValue() != null ? column.maxValue().toString() : null
            );
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
        fileRepository.save(file);
    }
}
