-- liquibase formatted sql

-- changeset erp:017-report-job-reliability
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS object_key VARCHAR(255);
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS last_error TEXT;
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS attempt_count INTEGER DEFAULT 0;
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS heartbeat_at TIMESTAMPTZ;
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ;
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;
ALTER TABLE report_job ADD COLUMN IF NOT EXISTS expired_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_report_job_recovery ON report_job(status, heartbeat_at);
CREATE INDEX IF NOT EXISTS idx_report_job_expired ON report_job(status, completed_at);
CREATE INDEX IF NOT EXISTS idx_report_job_retry ON report_job(status, next_retry_at);
