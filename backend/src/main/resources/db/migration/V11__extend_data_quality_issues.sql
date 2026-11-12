ALTER TABLE data_quality_issues
    ADD COLUMN related_columns JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN affected_rows INTEGER NOT NULL DEFAULT 0;

ALTER TABLE data_quality_issues
    ADD CONSTRAINT chk_data_quality_issues_affected_rows
    CHECK (affected_rows >= 0);
