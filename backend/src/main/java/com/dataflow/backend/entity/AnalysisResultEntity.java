package com.dataflow.backend.entity;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_results", uniqueConstraints = {
        @UniqueConstraint(columnNames = "file_id")
})
public class AnalysisResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false, unique = true)
    private File file;

    @Column(nullable = false)
    private int rowCount;

    @Column(nullable = false)
    private int columnCount;

    @Column(nullable = false)
    private int missingCount;

    @Column(nullable = false)
    private int duplicateCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "duplicate_analysis", columnDefinition = "jsonb")
    private JsonNode duplicateAnalysis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "distribution_analysis", columnDefinition = "jsonb")
    private JsonNode distributionAnalysis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correlation_analysis", columnDefinition = "jsonb")
    private JsonNode correlationAnalysis;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public int getColumnCount() {
        return columnCount;
    }

    public void setColumnCount(int columnCount) {
        this.columnCount = columnCount;
    }

    public int getMissingCount() {
        return missingCount;
    }

    public void setMissingCount(int missingCount) {
        this.missingCount = missingCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(int duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public JsonNode getDuplicateAnalysis() {
        return duplicateAnalysis;
    }

    public void setDuplicateAnalysis(JsonNode duplicateAnalysis) {
        this.duplicateAnalysis = duplicateAnalysis;
    }

    public JsonNode getDistributionAnalysis() {
        return distributionAnalysis;
    }

    public void setDistributionAnalysis(JsonNode distributionAnalysis) {
        this.distributionAnalysis = distributionAnalysis;
    }

    public JsonNode getCorrelationAnalysis() {
        return correlationAnalysis;
    }

    public void setCorrelationAnalysis(JsonNode correlationAnalysis) {
        this.correlationAnalysis = correlationAnalysis;
    }
}