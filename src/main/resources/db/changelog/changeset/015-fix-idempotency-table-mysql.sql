--liquibase formatted sql
--changeset erp:create-idempotency-key-table-mysql dbms:mysql
-- =============================================================================
-- MODULE: PLATFORM — TẠO BẢNG IDEMPOTENCY_KEY CHO MYSQL PRODUCTION
-- Phiên bản: 1.0.0
-- Tác giả: ERP-UTT Core Architecture Team
--
-- Mục tiêu:
-- 1. Bổ sung bảng idempotency_key chuẩn MySQL (InnoDB) với unique constraint
--    uk_idempotency_key để chống duplicate orders & race condition khi khách hàng tạo đơn.
-- 2. Đảm bảo idempotent: nếu bảng chưa có thì tạo mới.
-- =============================================================================

CREATE TABLE IF NOT EXISTS idempotency_key (
    id              CHAR(36)        NOT NULL PRIMARY KEY,
    idempotency_key VARCHAR(150)    NOT NULL,
    request_hash    VARCHAR(255)    NOT NULL,
    response_payload JSON           NULL,
    status          VARCHAR(30)     NOT NULL DEFAULT 'PROCESSING',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      DATETIME        NOT NULL,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_idempotency_key (idempotency_key),
    INDEX idx_idempotency_expires (expires_at),
    CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--rollback DROP TABLE IF EXISTS idempotency_key;
