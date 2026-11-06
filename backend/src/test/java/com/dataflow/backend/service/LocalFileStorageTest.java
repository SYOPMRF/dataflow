package com.dataflow.backend.service;

import com.dataflow.backend.config.FileStorageProperties;
import com.dataflow.backend.enums.StorageProvider;
import com.dataflow.backend.exception.InvalidFileException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileStorage localFileStorage;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setLocation(tempDir.toString());

        localFileStorage = new LocalFileStorage(properties);
    }

    @Test
    void storeShouldCreateFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sales.csv",
                "text/csv",
                "name,age\nAlice,25".getBytes(StandardCharsets.UTF_8));

        localFileStorage.store("stored-file.csv", file);

        assertTrue(Files.exists(tempDir.resolve("stored-file.csv")));
    }

    @Test
    void readShouldReturnFileContent() throws IOException {
        byte[] expected = "name,age\nAlice,25".getBytes(StandardCharsets.UTF_8);
        Files.write(tempDir.resolve("stored-file.csv"), expected);

        byte[] actual = localFileStorage.read("stored-file.csv");

        assertArrayEquals(expected, actual);
    }

    @Test
    void readShouldFailWhenFileDoesNotExist() {
        assertThrows(
                RuntimeException.class,
                () -> localFileStorage.read("missing-file.csv"));
    }

    @Test
    void deleteShouldRemoveExistingFile() throws IOException {
        Path file = tempDir.resolve("stored-file.csv");
        Files.writeString(file, "content");

        localFileStorage.delete("stored-file.csv");

        assertFalse(Files.exists(file));
    }

    @Test
    void deleteShouldNotFailWhenFileDoesNotExist() {
        assertDoesNotThrow(
                () -> localFileStorage.delete("missing-file.csv"));
    }

    @Test
    void operationsShouldRejectPathOutsideStorageDirectory() {
        assertThrows(
                InvalidFileException.class,
                () -> localFileStorage.read("../outside.csv"));

        assertThrows(
                InvalidFileException.class,
                () -> localFileStorage.delete("../outside.csv"));
    }

    @Test
    void operationsShouldRejectBlankStoredName() {
        assertThrows(
                InvalidFileException.class,
                () -> localFileStorage.read(" "));
    }

    @Test
    void getProviderShouldReturnLocal() {
        assertEquals(StorageProvider.LOCAL, localFileStorage.getProvider());
    }
}