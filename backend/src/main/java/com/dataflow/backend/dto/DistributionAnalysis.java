package com.dataflow.backend.dto;

import java.util.List;

public record DistributionAnalysis(
        List<ColumnDistribution> columns) {

    public DistributionAnalysis {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    public static DistributionAnalysis empty() {
        return new DistributionAnalysis(List.of());
    }
}