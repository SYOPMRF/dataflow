package com.dataflow.backend.dto;

import java.util.List;

public record DuplicateGroup(
        List<String> columns,
        int groupCount,
        int duplicateCount,
        int maxFrequency,
        List<DuplicateValueExample> examples) {
    public DuplicateGroup {
        columns = columns == null ? List.of() : List.copyOf(columns);
        examples = examples == null ? List.of() : List.copyOf(examples);
    }
}