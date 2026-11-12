package com.dataflow.backend.dto;

import java.util.List;

public record DataQualityIssue(
        String columnName,
        String issueType,
        String severity,
        String message,
        List<String> relatedColumns,
        int affectedRows
) {

    public DataQualityIssue {
        relatedColumns = relatedColumns == null
                ? List.of()
                : List.copyOf(relatedColumns);

        if (affectedRows < 0) {
            throw new IllegalArgumentException(
                    "affectedRows cannot be negative"
            );
        }
    }

    public DataQualityIssue(
            String columnName,
            String issueType,
            String severity,
            String message
    ) {
        this(
                columnName,
                issueType,
                severity,
                message,
                List.of(),
                0
        );
    }
}
