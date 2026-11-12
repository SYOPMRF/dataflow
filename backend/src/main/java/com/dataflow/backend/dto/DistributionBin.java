package com.dataflow.backend.dto;

public record DistributionBin(
        double lowerBound,
        double upperBound,
        int count,
        double percentage) {
}