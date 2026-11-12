package com.dataflow.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "data_quality_issues")
public class DataQualityIssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private File file;

    @Column(nullable = false)
    private String columnName;

    @Column(nullable = false)
    private String issueType;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_columns", columnDefinition = "jsonb", nullable = false)
    private List<String> relatedColumns = List.of();

    @Column(name = "affected_rows", nullable = false)
    private int affectedRows;

    public Long getId() {
        return id;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getRelatedColumns() {
        return relatedColumns;
    }

    public void setRelatedColumns(List<String> relatedColumns) {
        this.relatedColumns = relatedColumns == null
                ? List.of()
                : List.copyOf(relatedColumns);
    }

    public int getAffectedRows() {
        return affectedRows;
    }

    public void setAffectedRows(int affectedRows) {
        if (affectedRows < 0) {
            throw new IllegalArgumentException(
                    "affectedRows cannot be negative"
            );
        }

        this.affectedRows = affectedRows;
    }
}
