package com.dataflow.backend.config;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@ConditionalOnProperty(prefix = "file.storage.s3", name = "enabled", havingValue = "true")
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(FileStorageProperties properties) {
        FileStorageProperties.S3 s3 = properties.getS3();

        if (s3.getBucket() == null || s3.getBucket().isBlank()) {
            throw new IllegalStateException(
                    "S3 bucket must be configured when S3 storage is enabled");
        }

        var builder = S3Client.builder()
                .region(Region.of(s3.getRegion()))
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(s3.isPathStyleAccess())
                                .build());

        if (s3.getEndpoint() != null && !s3.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.getEndpoint()));
        }

        builder.credentialsProvider(credentialsProvider(s3));

        return builder.build();
    }

    AwsCredentialsProvider credentialsProvider(FileStorageProperties.S3 s3) {
        boolean hasAccessKey = s3.getAccessKey() != null
                && !s3.getAccessKey().isBlank();

        boolean hasSecretKey = s3.getSecretKey() != null
                && !s3.getSecretKey().isBlank();

        if (hasAccessKey != hasSecretKey) {
            throw new IllegalStateException(
                    "Both S3 access key and secret key must be configured together");
        }

        if (hasAccessKey) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            s3.getAccessKey(),
                            s3.getSecretKey()));
        }

        return DefaultCredentialsProvider.create();
    }
}