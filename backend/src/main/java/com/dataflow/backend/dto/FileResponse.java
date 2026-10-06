package com.dataflow.backend.dto;

import com.dataflow.backend.entity.File;

import java.time.LocalDateTime;

public class FileResponse {

    private Long id;
    private String originalName;
    private String storedName;
    private String fileType;
    private Long fileSize;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public FileResponse(
            Long id,
            String originalName,
            String storedName,
            String fileType,
            Long fileSize,
            String status,
            LocalDateTime createdAt,
            LocalDateTime processedAt
    ) {
        this.id = id;
        this.originalName = originalName;
        this.storedName = storedName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.status = status;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    public static FileResponse fromEntity(File file) {

        return new FileResponse(
                file.getId(),
                file.getOriginalName(),
                file.getStoredName(),
                file.getFileType(),
                file.getFileSize(),
                file.getStatus().name(),
                file.getCreatedAt(),
                file.getProcessedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public String getFileType() {
        return fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}