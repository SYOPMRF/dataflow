package com.dataflow.backend.dto;

import java.util.List;

public record AnalysisResult(
                int rowCount,
                int columnCount,
                int missingCount,
                int duplicateCount,
                List<ColumnAnalysis> columns,
                List<DataQualityIssue> qualityIssues,
                DuplicateAnalysis duplicateAnalysis,
                DistributionAnalysis distributionAnalysis,
                CorrelationAnalysis correlationAnalysis) {

        public AnalysisResult {
                columns = columns == null ? List.of() : List.copyOf(columns);

                qualityIssues = qualityIssues == null
                                ? List.of()
                                : List.copyOf(qualityIssues);

                duplicateAnalysis = duplicateAnalysis == null
                                ? DuplicateAnalysis.empty()
                                : duplicateAnalysis;

                distributionAnalysis = distributionAnalysis == null
                                ? DistributionAnalysis.empty()
                                : distributionAnalysis;

                correlationAnalysis = correlationAnalysis == null
                                ? CorrelationAnalysis.empty()
                                : correlationAnalysis;
        }

        public AnalysisResult(
                        int rowCount,
                        int columnCount,
                        int missingCount,
                        int duplicateCount,
                        List<ColumnAnalysis> columns,
                        List<DataQualityIssue> qualityIssues) {

                this(
                                rowCount,
                                columnCount,
                                missingCount,
                                duplicateCount,
                                columns,
                                qualityIssues,
                                DuplicateAnalysis.empty(),
                                DistributionAnalysis.empty(),
                                CorrelationAnalysis.empty());
        }

        public AnalysisResult(
                        int rowCount,
                        int columnCount,
                        int missingCount,
                        int duplicateCount,
                        List<ColumnAnalysis> columns,
                        List<DataQualityIssue> qualityIssues,
                        DuplicateAnalysis duplicateAnalysis) {

                this(
                                rowCount,
                                columnCount,
                                missingCount,
                                duplicateCount,
                                columns,
                                qualityIssues,
                                duplicateAnalysis,
                                DistributionAnalysis.empty(),
                                CorrelationAnalysis.empty());
        }
}