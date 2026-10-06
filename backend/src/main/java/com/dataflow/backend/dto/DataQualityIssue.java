package com.dataflow.backend.dto;

public record DataQualityIssue(
        String columnName,
        String issueType,
        String severity,
        String message
) {
}