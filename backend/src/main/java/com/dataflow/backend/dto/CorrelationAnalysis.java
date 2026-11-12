package com.dataflow.backend.dto;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public record CorrelationAnalysis(
        List<String> columns,
        List<List<Double>> pearson,
        List<List<Double>> spearman,
        List<List<Integer>> observationCounts,
        boolean truncated) {

    public CorrelationAnalysis {
        columns = columns == null ? List.of() : List.copyOf(columns);
        pearson = copyMatrix(pearson);
        spearman = copyMatrix(spearman);
        observationCounts = copyMatrix(observationCounts);
    }

    private static <T> List<List<T>> copyMatrix(List<List<T>> matrix) {
        if (matrix == null) {
            return List.of();
        }

        return matrix.stream()
                .map(row -> row == null
                        ? List.<T>of()
                        : Collections.unmodifiableList(new ArrayList<>(row)))
                .toList();
    }

    public static CorrelationAnalysis empty() {
        return new CorrelationAnalysis(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false);
    }
}