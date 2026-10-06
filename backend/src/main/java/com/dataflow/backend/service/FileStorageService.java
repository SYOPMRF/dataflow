package com.dataflow.backend.service;

import com.dataflow.backend.config.FileStorageProperties;
import com.dataflow.backend.exception.InvalidFileException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final Path storageLocation;

    public FileStorageService(FileStorageProperties properties) {
        this.storageLocation = Paths.get(properties.getLocation())
                .toAbsolutePath()
                .normalize();

        try {
            Files.createDirectories(this.storageLocation);
        } catch (IOException exception) {
            throw new RuntimeException(
                    "Could not initialize file storage",
                    exception
            );
        }
    }

    public StoredFile store(MultipartFile file) {

        validateFile(file);

        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename()
        );

        String extension = getExtension(originalName);

        String storedName = UUID.randomUUID() + extension;

        Path targetLocation = storageLocation
                .resolve(storedName)
                .normalize();

        if (!targetLocation.getParent().equals(storageLocation)) {
            throw new InvalidFileException("Invalid file path");
        }

        try {
            Files.copy(file.getInputStream(), targetLocation);
        } catch (IOException exception) {
            throw new RuntimeException(
                    "Could not store file",
                    exception
            );
        }

        return new StoredFile(
                originalName,
                storedName,
                getFileType(extension),
                file.getSize()
        );
    }

    public void delete(String storedName) {

    Path targetLocation = storageLocation
            .resolve(storedName)
            .normalize();

    if (!targetLocation.getParent().equals(storageLocation)) {
        throw new InvalidFileException("Invalid file path");
    }

    try {
        Files.deleteIfExists(targetLocation);
    } catch (IOException exception) {
        throw new RuntimeException(
                "Could not delete file",
                exception
        );
    }
    }

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileException(
                    "File size cannot exceed 10 MB"
            );
        }

        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            throw new InvalidFileException(
                    "File must have a valid name"
            );
        }

        String extension = getExtension(originalName);

        if (!extension.equals(".csv") &&
                !extension.equals(".xlsx")) {

            throw new InvalidFileException(
                    "Only CSV and XLSX files are supported"
            );
        }
    }

    private String getExtension(String fileName) {

        int lastDot = fileName.lastIndexOf('.');

        if (lastDot == -1) {
            return "";
        }

        return fileName.substring(lastDot).toLowerCase();
    }

    private String getFileType(String extension) {

    return switch (extension) {
        case ".csv" -> "text/csv";
        case ".xlsx" ->
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        default -> "application/octet-stream";
    };
    }

    public record StoredFile(
            String originalName,
            String storedName,
            String fileType,
            long fileSize
    ) {
    }
}