package com.dataflow.backend.config;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S3ClientConfigTest {

    private final S3ClientConfig config = new S3ClientConfig();

    @Test
    void shouldUseStaticCredentialsWhenBothKeysAreConfigured() {
        FileStorageProperties properties = createProperties();

        properties.getS3().setAccessKey("test-access-key");
        properties.getS3().setSecretKey("test-secret-key");

        assertInstanceOf(
                StaticCredentialsProvider.class,
                config.credentialsProvider(properties.getS3()));
    }

    @Test
    void shouldUseDefaultCredentialsWhenKeysAreNotConfigured() {
        FileStorageProperties properties = createProperties();

        assertInstanceOf(
                DefaultCredentialsProvider.class,
                config.credentialsProvider(properties.getS3()));
    }

    @Test
    void shouldRejectAccessKeyWithoutSecretKey() {
        FileStorageProperties properties = createProperties();

        properties.getS3().setAccessKey("test-access-key");

        assertThrows(
                IllegalStateException.class,
                () -> config.credentialsProvider(properties.getS3()));
    }

    @Test
    void shouldRejectSecretKeyWithoutAccessKey() {
        FileStorageProperties properties = createProperties();

        properties.getS3().setSecretKey("test-secret-key");

        assertThrows(
                IllegalStateException.class,
                () -> config.credentialsProvider(properties.getS3()));
    }

    @Test
    void shouldRejectBlankBucket() {
        FileStorageProperties properties = createProperties();

        properties.getS3().setBucket(" ");

        assertThrows(
                IllegalStateException.class,
                () -> config.s3Client(properties));
    }

    private FileStorageProperties createProperties() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.getS3().setEnabled(true);
        properties.getS3().setBucket("dataflow-test-files");

        return properties;
    }
}