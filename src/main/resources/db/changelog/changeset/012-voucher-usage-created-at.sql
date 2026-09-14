--liquibase formatted sql

--changeset erp:voucher-usage-created-at
-- Bổ sung cột created_at (audit của BaseAuditingEntity) còn thiếu cho bảng voucher_usage.
-- Dùng DEFAULT only để backfill dữ liệu cũ, sau đó gỡ default cho đồng bộ với các bảng khác.
ALTER TABLE voucher_usage
    ADD COLUMN created_at timestamp NOT NULL DEFAULT now();

ALTER TABLE voucher_usage
    ALTER COLUMN created_at DROP DEFAULT;