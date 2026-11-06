package com.dataflow.backend.service;

import com.dataflow.backend.config.FileStorageProperties;
import com.dataflow.backend.enums.StorageProvider;
import com.dataflow.backend.exception.InvalidFileException;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final StorageProvider defaultProvider;
    private final Map<StorageProvider, FileStorage> storages;

    public FileStorageService(
            FileStorageProperties properties,
            List<FileStorage> storageImplementations) {

        this.defaultProvider = properties.getProvider();
        this.storages = new EnumMap<>(StorageProvider.class);

        for (FileStorage storage : storageImplementations) {
            this.storages.put(storage.getProvider(), storage);
        }
    }

    public StoredFile store(MultipartFile file) {
        validateFile(file);

        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename());

        String extension = getExtension(originalName);
        String storedName = UUID.randomUUID() + extension;

        FileStorage storage = getStorage(defaultProvider);
        storage.store(storedName, file);

        return new StoredFile(
                originalName,
                storedName,
                getFileType(extension),
                file.getSize(),
                storage.getProvider());
    }

    public byte[] read(
            String storedName,
            StorageProvider storageProvider) {

        return getStorage(storageProvider).read(storedName);
    }

    public void delete(
            String storedName,
            StorageProvider storageProvider) {

        getStorage(storageProvider).delete(storedName);
    }

    private FileStorage getStorage(StorageProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException(
                    "Storage provider cannot be null");
        }

        FileStorage storage = storages.get(provider);

        if (storage == null) {
            throw new UnsupportedOperationException(
                    "Storage provider is not configured: " + provider);
        }

        return storage;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileException(
                    "File size cannot exceed 10 MB");
        }

        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            throw new InvalidFileException(
                    "File must have a valid name");
        }

        String extension = getExtension(originalName);

        if (!extension.equals(".csv")
                && !extension.equals(".xlsx")) {
            throw new InvalidFileException(
                    "Only CSV and XLSX files are supported");
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
            long fileSize,
            StorageProvider storageProvider) {
    }
}