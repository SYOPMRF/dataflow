package com.dataflow.backend.dto;

public record DistributionCategory(
        Object value,
        int count,
        double percentage) {
}