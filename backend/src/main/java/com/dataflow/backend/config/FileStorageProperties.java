
package com.dataflow.backend.config;

import com.dataflow.backend.enums.StorageProvider;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "file.storage")
public class FileStorageProperties {

    private StorageProvider provider = StorageProvider.LOCAL;
    private String location = "uploads";
    private final S3 s3 = new S3();

    public StorageProvider getProvider() {
        return provider;
    }

    public void setProvider(StorageProvider provider) {
        this.provider = provider;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public S3 getS3() {
        return s3;
    }

    @PostConstruct
    public void validate() {
        if (provider == null) {
            throw new IllegalStateException(
                    "File storage provider must be configured");
        }

        if (provider == StorageProvider.S3 && !s3.isEnabled()) {
            throw new IllegalStateException(
                    "S3 storage provider requires file.storage.s3.enabled=true");
        }
    }

    public static class S3 {

        private String endpoint;
        private String region = "us-east-1";
        private String bucket = "dataflow-files";
        private String accessKey;
        private String secretKey;
        private boolean pathStyleAccess = true;
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public boolean isPathStyleAccess() {
            return pathStyleAccess;
        }

        public void setPathStyleAccess(boolean pathStyleAccess) {
            this.pathStyleAccess = pathStyleAccess;
        }
    }
}