package com.dataflow.backend.service;

import com.dataflow.backend.config.FileStorageProperties;
import com.dataflow.backend.enums.StorageProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3FileStorageTest {

    private static final String BUCKET = "dataflow-test-files";

    @Mock
    private S3Client s3Client;

    private S3FileStorage s3FileStorage;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.getS3().setBucket(BUCKET);

        s3FileStorage = new S3FileStorage(s3Client, properties);
    }

    @Test
    void storeShouldUploadFileWithCorrectMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sales.csv",
                "text/csv",
                "name,age\nAlice,25".getBytes(StandardCharsets.UTF_8));

        s3FileStorage.store("stored-file.csv", file);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);

        verify(s3Client).putObject(
                requestCaptor.capture(),
                any(RequestBody.class));

        PutObjectRequest request = requestCaptor.getValue();

        assertEquals(BUCKET, request.bucket());
        assertEquals("stored-file.csv", request.key());
        assertEquals("text/csv", request.contentType());
    }

    @Test
    void readShouldReturnObjectContent() {
        byte[] expectedContent = "name,age\nAlice,25".getBytes(StandardCharsets.UTF_8);

        GetObjectResponse response = GetObjectResponse.builder().build();

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        response,
                        expectedContent));

        byte[] actualContent = s3FileStorage.read("stored-file.csv");

        assertArrayEquals(expectedContent, actualContent);

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);

        verify(s3Client).getObjectAsBytes(requestCaptor.capture());

        GetObjectRequest request = requestCaptor.getValue();

        assertEquals(BUCKET, request.bucket());
        assertEquals("stored-file.csv", request.key());
    }

    @Test
    void deleteShouldRemoveObjectFromCorrectBucket() {
        s3FileStorage.delete("stored-file.csv");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);

        verify(s3Client).deleteObject(requestCaptor.capture());

        DeleteObjectRequest request = requestCaptor.getValue();

        assertEquals(BUCKET, request.bucket());
        assertEquals("stored-file.csv", request.key());
    }

    @Test
    void getProviderShouldReturnS3() {
        assertEquals(StorageProvider.S3, s3FileStorage.getProvider());
    }

    @Test
    void storeShouldUploadOriginalFileContent() throws IOException {
        byte[] expectedContent = "name,age\nAlice,25".getBytes(StandardCharsets.UTF_8);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sales.csv",
                "text/csv",
                expectedContent);

        s3FileStorage.store("stored-file.csv", file);

        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);

        verify(s3Client).putObject(
                any(PutObjectRequest.class),
                bodyCaptor.capture());

        try (var inputStream = bodyCaptor.getValue()
                .contentStreamProvider()
                .newStream()) {

            assertArrayEquals(expectedContent, inputStream.readAllBytes());
        }
    }

    @Test
    void storeShouldThrowRuntimeExceptionWhenInputStreamCannotBeRead()
            throws IOException {

        MultipartFile file = mock(MultipartFile.class);

        when(file.getContentType()).thenReturn("text/csv");
        when(file.getInputStream())
                .thenThrow(new IOException("Simulated read failure"));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> s3FileStorage.store("stored-file.csv", file));

        assertEquals(
                "Could not read file for S3 storage",
                exception.getMessage());
    }
}