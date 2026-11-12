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

    private Double q1Value;
    private Double q3Value;
    private Double iqrValue;
    private Double p5Value;
    private Double p10Value;
    private Double p90Value;
    private Double p95Value;
    private Integer outlierCount;

    @Column(name = "z_score_outlier_count")
    private Integer zScoreOutlierCount;

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

    public Double getQ1Value() {
        return q1Value;
    }

    public void setQ1Value(Double q1Value) {
        this.q1Value = q1Value;
    }

    public Double getQ3Value() {
        return q3Value;
    }

    public void setQ3Value(Double q3Value) {
        this.q3Value = q3Value;
    }

    public Double getIqrValue() {
        return iqrValue;
    }

    public void setIqrValue(Double iqrValue) {
        this.iqrValue = iqrValue;
    }

    public Double getP5Value() {
        return p5Value;
    }

    public void setP5Value(Double p5Value) {
        this.p5Value = p5Value;
    }

    public Double getP10Value() {
        return p10Value;
    }

    public void setP10Value(Double p10Value) {
        this.p10Value = p10Value;
    }

    public Double getP90Value() {
        return p90Value;
    }

    public void setP90Value(Double p90Value) {
        this.p90Value = p90Value;
    }

    public Double getP95Value() {
        return p95Value;
    }

    public void setP95Value(Double p95Value) {
        this.p95Value = p95Value;
    }

    public Integer getOutlierCount() {
        return outlierCount;
    }

    public void setOutlierCount(Integer outlierCount) {
        this.outlierCount = outlierCount;
    }

    public Integer getZScoreOutlierCount() {
        return zScoreOutlierCount;
    }

    public void setZScoreOutlierCount(Integer zScoreOutlierCount) {
        this.zScoreOutlierCount = zScoreOutlierCount;
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