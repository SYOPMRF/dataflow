ALTER TABLE pending_file_deletions
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP(6) WITHOUT TIME ZONE;

UPDATE pending_file_deletions
SET next_attempt_at = COALESCE(next_attempt_at, created_at, CURRENT_TIMESTAMP)
WHERE next_attempt_at IS NULL;

ALTER TABLE pending_file_deletions
    ALTER COLUMN next_attempt_at SET NOT NULL;

ALTER TABLE pending_file_deletions
    ADD COLUMN IF NOT EXISTS claim_token UUID;

ALTER TABLE pending_file_deletions
    ADD COLUMN IF NOT EXISTS claimed_until TIMESTAMP(6) WITHOUT TIME ZONE;