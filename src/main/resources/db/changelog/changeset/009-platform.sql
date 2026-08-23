--liquibase formatted sql

--changeset erp:add-nen-tang-chung
-- ============================================================
-- PLATFORM / SHARED — Thông báo, Idempotency
-- Phụ thuộc: customer, account (auth).
-- ============================================================

CREATE TABLE IF NOT EXISTS notification_template (
    id             uuid        NOT NULL,
    code           varchar(80) NOT NULL,
    channel        varchar(30) NOT NULL,
    title_template varchar(255) NOT NULL,
    body_template  varchar(2000) NOT NULL,
    status         varchar(30) NOT NULL DEFAULT 'ACTIVE',
    created_at     timestamp   NOT NULL,
    created_by     varchar(100),
    updated_at     timestamp   NOT NULL,
    updated_by     varchar(100),
    CONSTRAINT pk_notification_template PRIMARY KEY (id),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('PUSH', 'EMAIL', 'SMS', 'IN_APP'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_template_code ON notification_template (code);

CREATE TABLE IF NOT EXISTS notification (
    id             uuid        NOT NULL,
    template_id    uuid,
    recipient_type varchar(20) NOT NULL,
    customer_id    uuid,
    account_id     uuid,
    channel        varchar(30) NOT NULL,
    title          varchar(255) NOT NULL,
    body           varchar(2000) NOT NULL,
    status         varchar(30) NOT NULL DEFAULT 'PENDING',
    sent_at        timestamp,
    read_at        timestamp,
    created_at     timestamp   NOT NULL,
    created_by     varchar(100),
    updated_at     timestamp   NOT NULL,
    updated_by     varchar(100),
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT fk_notification_template FOREIGN KEY (template_id) REFERENCES notification_template (id) ON DELETE SET NULL,
    CONSTRAINT fk_notification_customer FOREIGN KEY (customer_id) REFERENCES customer (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE,
    CONSTRAINT ck_notification_recipient CHECK (
        (recipient_type = 'CUSTOMER' AND customer_id IS NOT NULL)
        OR (recipient_type = 'ACCOUNT' AND account_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_notification_customer_status ON notification (customer_id, status);
CREATE INDEX IF NOT EXISTS idx_notification_account_status ON notification (account_id, status);

CREATE TABLE IF NOT EXISTS idempotency_key (
    id                uuid        NOT NULL,
    idempotency_key   varchar(150) NOT NULL,
    request_hash      varchar(255) NOT NULL,
    response_payload  jsonb,
    status            varchar(30) NOT NULL DEFAULT 'PROCESSING',
    created_at        timestamp   NOT NULL,
    expires_at        timestamp   NOT NULL,
    updated_at        timestamp   NOT NULL,
    updated_by        varchar(100),
    CONSTRAINT pk_idempotency_key PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_idempotency_expires ON idempotency_key (expires_at);
