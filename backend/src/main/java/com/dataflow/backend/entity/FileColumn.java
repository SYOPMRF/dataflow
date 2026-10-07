package com.dataflow.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "file_columns")
public class FileColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private File file;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String dataType;

    @Column(nullable = false)
    private int nullCount;

    @Column(nullable = false)
    private int uniqueCount;

    @Column(nullable = false)
    private double uniquePercentage;

    @Column(nullable = false)
    private int duplicateCount;

    @Column(nullable = false)
    private int duplicatedUniqueCount;

    @Column(nullable = false)
    private double missingPercentage;

    @Column(nullable = false)
    private boolean completelyEmpty;

    private String minValue;

    private String maxValue;

    private Double meanValue;

    private Double medianValue;

    private Double stdDeviation;

    @Column(nullable = false)
    private int zeroCount;

    @Column(nullable = false)
    private int negativeCount;

    public Long getId() {
        return id;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public int getNullCount() {
        return nullCount;
    }

    public void setNullCount(int nullCount) {
        this.nullCount = nullCount;
    }

    public int getUniqueCount() {
        return uniqueCount;
    }

    public void setUniqueCount(int uniqueCount) {
        this.uniqueCount = uniqueCount;
    }

    public double getUniquePercentage() {
        return uniquePercentage;
    }

    public void setUniquePercentage(double uniquePercentage) {
        this.uniquePercentage = uniquePercentage;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(int duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    public int getDuplicatedUniqueCount() {
        return duplicatedUniqueCount;
    }

    public void setDuplicatedUniqueCount(int duplicatedUniqueCount) {
        this.duplicatedUniqueCount = duplicatedUniqueCount;
    }

    public double getMissingPercentage() {
        return missingPercentage;
    }

    public void setMissingPercentage(double missingPercentage) {
        this.missingPercentage = missingPercentage;
    }

    public boolean isCompletelyEmpty() {
        return completelyEmpty;
    }

    public void setCompletelyEmpty(boolean completelyEmpty) {
        this.completelyEmpty = completelyEmpty;
    }

    public String getMinValue() {
        return minValue;
    }

    public void setMinValue(String minValue) {
        this.minValue = minValue;
    }

    public String getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(String maxValue) {
        this.maxValue = maxValue;
    }

    public Double getMeanValue() {
        return meanValue;
    }

    public void setMeanValue(Double meanValue) {
        this.meanValue = meanValue;
    }

    public Double getMedianValue() {
        return medianValue;
    }

    public void setMedianValue(Double medianValue) {
        this.medianValue = medianValue;
    }

    public Double getStdDeviation() {
        return stdDeviation;
    }

    public void setStdDeviation(Double stdDeviation) {
        this.stdDeviation = stdDeviation;
    }

    public int getZeroCount() {
        return zeroCount;
    }

    public void setZeroCount(int zeroCount) {
        this.zeroCount = zeroCount;
    }

    public int getNegativeCount() {
        return negativeCount;
    }

    public void setNegativeCount(int negativeCount) {
        this.negativeCount = negativeCount;
    }
}