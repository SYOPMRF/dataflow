package com.dataflow.backend.config;

import com.dataflow.backend.enums.StorageProvider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileStoragePropertiesTest {

    @Test
    void validateShouldAcceptLocalStorageByDefault() {
        FileStorageProperties properties = new FileStorageProperties();

        assertDoesNotThrow(properties::validate);
    }

    @Test
    void validateShouldAcceptS3WhenEnabled() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setProvider(StorageProvider.S3);
        properties.getS3().setEnabled(true);

        assertDoesNotThrow(properties::validate);
    }

    @Test
    void validateShouldRejectS3WhenDisabled() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setProvider(StorageProvider.S3);

        assertThrows(
                IllegalStateException.class,
                properties::validate);
    }

    @Test
    void validateShouldRejectNullProvider() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setProvider(null);

        assertThrows(
                IllegalStateException.class,
                properties::validate);
    }

    @Test
    void validateShouldAllowLocalStorageWhenS3IsEnabled() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setProvider(StorageProvider.LOCAL);
        properties.getS3().setEnabled(true);

        assertDoesNotThrow(properties::validate);
    }
}