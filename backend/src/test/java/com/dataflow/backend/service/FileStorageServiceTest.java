package com.dataflow.backend.service;

import com.dataflow.backend.config.FileStorageProperties;
import com.dataflow.backend.exception.InvalidFileException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setLocation(tempDir.toString());

        fileStorageService = new FileStorageService(properties);
    }

    @Test
    void deleteShouldRemoveExistingFile() throws IOException {
        Path file = tempDir.resolve("stored-file.csv");
        Files.writeString(file, "name,age\nAlice,25");

        fileStorageService.delete("stored-file.csv");

        assertFalse(Files.exists(file));
    }

    @Test
    void deleteShouldNotFailWhenFileDoesNotExist() {
        assertDoesNotThrow(
                () -> fileStorageService.delete("missing-file.csv"));
    }

    @Test
    void deleteShouldRejectPathOutsideStorageDirectory() {
        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.delete("../outside.csv"));
    }
}
