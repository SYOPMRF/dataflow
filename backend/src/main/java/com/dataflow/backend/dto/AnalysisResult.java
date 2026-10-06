package com.dataflow.backend.dto;

import java.util.List;

public record AnalysisResult(
        int rowCount,
        int columnCount,
        int missingCount,
        int duplicateCount,
        List<ColumnAnalysis> columns,
        List<DataQualityIssue> qualityIssues
) {
}