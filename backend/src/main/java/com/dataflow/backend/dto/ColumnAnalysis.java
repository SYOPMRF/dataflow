package com.dataflow.backend.dto;

public record ColumnAnalysis(
                String name,
                String dataType,
                int nullCount,
                int uniqueCount,
                double uniquePercentage,
                int duplicateCount,
                int duplicatedUniqueCount,
                double missingPercentage,
                boolean isCompletelyEmpty,
                Object minValue,
                Object maxValue,
                Double meanValue,
                Double medianValue,
                Double stdDeviation,
                int zeroCount,
                int negativeCount,
                Double q1Value,
                Double q3Value,
                Double iqrValue,
                Double p5Value,
                Double p10Value,
                Double p90Value,
                Double p95Value,
                Integer outlierCount,
                Integer zScoreOutlierCount) {
}