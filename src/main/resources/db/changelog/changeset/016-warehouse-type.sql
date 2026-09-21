--liquibase formatted sql

--changeset erp:restrict-warehouse-type
-- ============================================================
-- S3-01: kho chỉ có 2 loại CENTRAL/BRANCH.
-- Bảng warehouse cũ được tạo từ schema chưa có CHECK constraint
-- nên cần chuẩn hóa dữ liệu legacy (MAIN/VIRTUAL, CENTRAL có chi
-- nhánh) trước khi thêm lại ràng buộc ở tầng DB.
-- ============================================================

-- 1) Legacy: MAIN -> BRANCH (giữ branch_id có sẵn).
UPDATE warehouse SET warehouse_type = 'BRANCH' WHERE warehouse_type = 'MAIN';

-- 2) Legacy: VIRTUAL -> BRANCH và đóng kho test lại (giữ lịch sử).
UPDATE warehouse SET warehouse_type = 'BRANCH', status = 'INACTIVE' WHERE warehouse_type = 'VIRTUAL';

-- 3) Legacy: CENTRAL không được gắn chi nhánh.
UPDATE warehouse SET branch_id = NULL WHERE warehouse_type = 'CENTRAL' AND branch_id IS NOT NULL;

-- 4) Re-add CHECK constraints (tầng DB bảo vệ tuyệt đối).
ALTER TABLE warehouse ADD CONSTRAINT ck_warehouse_type CHECK (warehouse_type IN ('CENTRAL', 'BRANCH'));
ALTER TABLE warehouse ADD CONSTRAINT ck_warehouse_branch CHECK (
    (warehouse_type = 'CENTRAL' AND branch_id IS NULL)
    OR (warehouse_type = 'BRANCH' AND branch_id IS NOT NULL)
);