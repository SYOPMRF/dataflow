ALTER TABLE files
    ADD COLUMN storage_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

ALTER TABLE files
    ADD CONSTRAINT chk_files_storage_provider
    CHECK (storage_provider IN ('LOCAL', 'S3'));

ALTER TABLE pending_file_deletions
    ADD COLUMN storage_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

ALTER TABLE pending_file_deletions
    ADD CONSTRAINT chk_pending_file_deletions_storage_provider
    CHECK (storage_provider IN ('LOCAL', 'S3'));
