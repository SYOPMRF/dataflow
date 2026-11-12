package com.dataflow.backend.dto;

import java.util.List;

public record ColumnDistribution(
        String column,
        String distributionType,
        int validCount,
        int nullCount,
        Integer binCount,
        List<DistributionBin> bins,
        Integer categoryCount,
        List<DistributionCategory> categories,
        boolean categoriesTruncated) {

    public ColumnDistribution {
        bins = bins == null ? List.of() : List.copyOf(bins);
        categories = categories == null ? List.of() : List.copyOf(categories);
    }
}