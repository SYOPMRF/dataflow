package com.dataflow.backend.dto;

public record CandidateKey(
        java.util.List<String> columns,
        String keyType,
        int evaluatedRowCount,
        int uniqueCount,
        double uniquenessPercentage) {
    public CandidateKey {
        columns = columns == null ? java.util.List.of() : java.util.List.copyOf(columns);
    }
}