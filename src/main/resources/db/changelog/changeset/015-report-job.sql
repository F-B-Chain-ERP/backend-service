-- liquibase formatted sql

-- changeset erp:015-report-job-table
CREATE TABLE IF NOT EXISTS report_job (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module          VARCHAR(30)  NOT NULL,
    report_type     VARCHAR(50)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    format          VARCHAR(10)  NOT NULL DEFAULT 'EXCEL',
    requested_by    UUID         NOT NULL,
    branch_id       UUID,
    request_params  TEXT,
    estimated_rows  INTEGER      DEFAULT 0,
    file_url        TEXT,
    error_message   TEXT,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_report_job_requested_by ON report_job(requested_by);
CREATE INDEX IF NOT EXISTS idx_report_job_status ON report_job(status);
CREATE INDEX IF NOT EXISTS idx_report_job_created_at ON report_job(created_at DESC);
