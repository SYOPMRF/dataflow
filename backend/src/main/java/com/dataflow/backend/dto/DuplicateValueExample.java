package com.dataflow.backend.dto;

import java.util.List;

public record DuplicateValueExample(
        List<Object> values,
        int frequency) {
    public DuplicateValueExample {
        values = values == null
                ? List.of()
                : java.util.Collections.unmodifiableList(
                        new java.util.ArrayList<>(values));
    }
}