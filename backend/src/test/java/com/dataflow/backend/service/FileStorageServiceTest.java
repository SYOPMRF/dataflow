package com.dataflow.backend.service;

import com.dataflow.backend.config.FileStorageProperties;
import com.dataflow.backend.enums.StorageProvider;
import com.dataflow.backend.exception.InvalidFileException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setLocation(tempDir.toString());
        properties.setProvider(StorageProvider.LOCAL);

        LocalFileStorage localFileStorage = new LocalFileStorage(properties);

        fileStorageService = new FileStorageService(
                properties,
                List.of(localFileStorage));
    }

    @Test
    void readShouldReturnFileContent() throws IOException {
        Path file = tempDir.resolve("stored-file.csv");
        byte[] expectedContent = "name,age\nAlice,25".getBytes(StandardCharsets.UTF_8);

        Files.write(file, expectedContent);

        byte[] actualContent = fileStorageService.read(
                "stored-file.csv",
                StorageProvider.LOCAL);

        assertArrayEquals(expectedContent, actualContent);
    }

    @Test
    void readShouldFailWhenFileDoesNotExist() {
        assertThrows(
                RuntimeException.class,
                () -> fileStorageService.read(
                        "missing-file.csv",
                        StorageProvider.LOCAL));
    }

    @Test
    void readShouldRejectPathOutsideStorageDirectory() {
        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.read(
                        "../outside.csv",
                        StorageProvider.LOCAL));
    }

    @Test
    void readShouldRejectUnsupportedStorageProvider() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> fileStorageService.read(
                        "stored-file.csv",
                        StorageProvider.S3));
    }

    @Test
    void storeShouldSaveFileAndReturnMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sales.csv",
                "text/csv",
                "name,age\nAlice,25".getBytes(StandardCharsets.UTF_8));

        FileStorageService.StoredFile storedFile = fileStorageService.store(file);

        assertEquals("sales.csv", storedFile.originalName());
        assertEquals("text/csv", storedFile.fileType());
        assertEquals(file.getSize(), storedFile.fileSize());
        assertEquals(StorageProvider.LOCAL, storedFile.storageProvider());
        assertTrue(storedFile.storedName().endsWith(".csv"));
        assertTrue(Files.exists(tempDir.resolve(storedFile.storedName())));
    }

    @Test
    void storeShouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sales.csv",
                "text/csv",
                new byte[0]);

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.store(file));
    }

    @Test
    void storeShouldRejectUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sales.txt",
                "text/plain",
                "content".getBytes(StandardCharsets.UTF_8));

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.store(file));
    }

    @Test
    void storeShouldRejectFileLargerThan10MB() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sales.csv",
                "text/csv",
                new byte[10 * 1024 * 1024 + 1]);

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.store(file));
    }

    @Test
    void deleteShouldRemoveExistingFile() throws IOException {
        Path file = tempDir.resolve("stored-file.csv");
        Files.writeString(file, "name,age\nAlice,25");

        fileStorageService.delete("stored-file.csv", StorageProvider.LOCAL);

        assertFalse(Files.exists(file));
    }

    @Test
    void deleteShouldNotFailWhenFileDoesNotExist() {
        assertDoesNotThrow(
                () -> fileStorageService.delete(
                        "missing-file.csv",
                        StorageProvider.LOCAL));
    }

    @Test
    void deleteShouldRejectPathOutsideStorageDirectory() {
        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.delete(
                        "../outside.csv",
                        StorageProvider.LOCAL));
    }

    @Test
    void deleteShouldRejectUnsupportedStorageProvider() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> fileStorageService.delete(
                        "stored-file.csv",
                        StorageProvider.S3));
    }

    @Test
    void storeShouldUseConfiguredS3Provider() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setProvider(StorageProvider.S3);

        FileStorage s3Storage = mock(FileStorage.class);
        when(s3Storage.getProvider()).thenReturn(StorageProvider.S3);

        FileStorageService service = new FileStorageService(
                properties,
                List.of(s3Storage));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sales.csv",
                "text/csv",
                "name,age\nAlice,25".getBytes(StandardCharsets.UTF_8));

        FileStorageService.StoredFile storedFile = service.store(file);

        assertEquals(StorageProvider.S3, storedFile.storageProvider());
        verify(s3Storage).store(
                storedFile.storedName(),
                file);
    }

    @Test
    void readShouldUseProviderAssociatedWithExistingFile() {
        FileStorageProperties properties = new FileStorageProperties();

        FileStorage localStorage = mock(FileStorage.class);
        FileStorage s3Storage = mock(FileStorage.class);

        when(localStorage.getProvider()).thenReturn(StorageProvider.LOCAL);
        when(s3Storage.getProvider()).thenReturn(StorageProvider.S3);

        byte[] expectedContent = "name,age\nAlice,25"
                .getBytes(StandardCharsets.UTF_8);

        when(s3Storage.read("stored-file.csv"))
                .thenReturn(expectedContent);

        FileStorageService service = new FileStorageService(
                properties,
                List.of(localStorage, s3Storage));

        byte[] actualContent = service.read(
                "stored-file.csv",
                StorageProvider.S3);

        assertArrayEquals(expectedContent, actualContent);
        verify(s3Storage).read("stored-file.csv");
        verify(localStorage, never()).read(anyString());
    }

    @Test
    void deleteShouldUseProviderAssociatedWithExistingFile() {
        FileStorageProperties properties = new FileStorageProperties();

        FileStorage localStorage = mock(FileStorage.class);
        FileStorage s3Storage = mock(FileStorage.class);

        when(localStorage.getProvider()).thenReturn(StorageProvider.LOCAL);
        when(s3Storage.getProvider()).thenReturn(StorageProvider.S3);

        FileStorageService service = new FileStorageService(
                properties,
                List.of(localStorage, s3Storage));

        service.delete("stored-file.csv", StorageProvider.S3);

        verify(s3Storage).delete("stored-file.csv");
        verify(localStorage, never()).delete(anyString());
    }
}