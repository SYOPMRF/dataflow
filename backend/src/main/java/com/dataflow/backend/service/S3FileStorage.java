package com.dataflow.backend.service;

import com.dataflow.backend.config.FileStorageProperties;
import com.dataflow.backend.enums.StorageProvider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

@Component
@ConditionalOnProperty(prefix = "file.storage.s3", name = "enabled", havingValue = "true")
public class S3FileStorage implements FileStorage {

    private final S3Client s3Client;
    private final String bucket;

    public S3FileStorage(
            S3Client s3Client,
            FileStorageProperties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.getS3().getBucket();
    }

    @Override
    public void store(String storedName, MultipartFile file) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storedName)
                .contentType(file.getContentType())
                .build();

        try (var inputStream = file.getInputStream()) {
            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(
                            inputStream,
                            file.getSize()));
        } catch (IOException exception) {
            throw new RuntimeException(
                    "Could not read file for S3 storage",
                    exception);
        }
    }

    @Override
    public byte[] read(String storedName) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(storedName)
                .build();

        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);

        return response.asByteArray();
    }

    @Override
    public void delete(String storedName) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(storedName)
                .build();

        s3Client.deleteObject(request);
    }

    @Override
    public StorageProvider getProvider() {
        return StorageProvider.S3;
    }
}