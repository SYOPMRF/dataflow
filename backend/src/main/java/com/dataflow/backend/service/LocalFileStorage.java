package com.dataflow.backend.service;

import com.dataflow.backend.config.FileStorageProperties;
import com.dataflow.backend.enums.StorageProvider;
import com.dataflow.backend.exception.InvalidFileException;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path storageLocation;

    public LocalFileStorage(FileStorageProperties properties) {
        this.storageLocation = Paths.get(properties.getLocation())
                .toAbsolutePath()
                .normalize();

        try {
            Files.createDirectories(this.storageLocation);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not initialize local file storage",
                    exception);
        }
    }

    @Override
    public void store(String storedName, MultipartFile file) {
        Path targetLocation = resolveSafePath(storedName);

        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetLocation);
        } catch (IOException exception) {
            throw new RuntimeException(
                    "Could not store file",
                    exception);
        }
    }

    @Override
    public byte[] read(String storedName) {
        Path targetLocation = resolveSafePath(storedName);

        try {
            return Files.readAllBytes(targetLocation);
        } catch (IOException exception) {
            throw new RuntimeException(
                    "Could not read file",
                    exception);
        }
    }

    @Override
    public void delete(String storedName) {
        Path targetLocation = resolveSafePath(storedName);

        try {
            Files.deleteIfExists(targetLocation);
        } catch (IOException exception) {
            throw new RuntimeException(
                    "Could not delete file",
                    exception);
        }
    }

    @Override
    public StorageProvider getProvider() {
        return StorageProvider.LOCAL;
    }

    private Path resolveSafePath(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            throw new InvalidFileException("Invalid file path");
        }

        Path targetLocation = storageLocation
                .resolve(storedName)
                .normalize();

        if (!targetLocation.startsWith(storageLocation)
                || targetLocation.equals(storageLocation)) {
            throw new InvalidFileException("Invalid file path");
        }

        return targetLocation;
    }
}