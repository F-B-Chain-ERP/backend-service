--liquibase formatted sql

--changeset erp:stock-transfer-request-approve
-- =============================================================================
-- MODULE: INV — YÊU CẦU + DUYỆT ĐIỀU CHUYỂN 2 PHE (QUÁN / KHO)
-- Phiên bản: 1.0.0
--
-- Mục tiêu changeset:
-- 1. Trạng thái REQUESTED cho yêu cầu xin hàng của quán (kèm số lượng).
--    Admin phe kho chỉ được DUYỆT (->PENDING) hoặc TỪ CHỐI (->CANCELLED + lý do).
-- 2. Ghi actor 2 phe: requested_by (quán), approved_by/at + dispatched_by/at (kho).
--    Người duyệt được trùng người xuất (cùng phe kho), nhưng phải khác người tạo.
-- 3. Quyền mới inv:stock_transfer:approve cho phe kho; mở create/view/update
--    transfer cho STORE_MANAGER (phe quán tạo yêu cầu + nhận hàng).
-- =============================================================================

ALTER TABLE stock_transfer
    ADD COLUMN IF NOT EXISTS requested_by UUID REFERENCES account (id) ON DELETE SET NULL;

ALTER TABLE stock_transfer
    ADD COLUMN IF NOT EXISTS approved_by UUID REFERENCES account (id) ON DELETE SET NULL;

ALTER TABLE stock_transfer
    ADD COLUMN IF NOT EXISTS approved_at timestamp;

ALTER TABLE stock_transfer
    ADD COLUMN IF NOT EXISTS dispatched_by UUID REFERENCES account (id) ON DELETE SET NULL;

ALTER TABLE stock_transfer
    ADD COLUMN IF NOT EXISTS dispatched_at timestamp;

ALTER TABLE stock_transfer DROP CONSTRAINT IF EXISTS ck_transfer_status;

ALTER TABLE stock_transfer
    ADD CONSTRAINT ck_transfer_status
    CHECK (status IN ('PENDING', 'REQUESTED', 'IN_TRANSIT', 'RECEIVED', 'CANCELLED'));

-- Quyền duyệt điều chuyển (phe kho)
INSERT INTO permission (id, code, name, module, description, status, created_at, updated_at)
VALUES (gen_random_uuid(), 'inv:stock_transfer:approve', 'Approve Stock Transfer',
    'INV', 'Approve or reject stock transfer request', 'ACTIVE', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- Phe kho: INVENTORY_MANAGER, ROLE_WAREHOUSE, ROLE_MANAGER + ADMIN/ROLE_ADMIN full
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.code IN ('ADMIN', 'ROLE_ADMIN', 'INVENTORY_MANAGER', 'ROLE_WAREHOUSE', 'ROLE_MANAGER')
  AND p.code = 'inv:stock_transfer:approve'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- Phe quán: STORE_MANAGER tạo yêu cầu + xem + nhận hàng
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.code = 'STORE_MANAGER'
  AND p.code IN ('inv:stock_transfer:create', 'inv:stock_transfer:view', 'inv:stock_transfer:update')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
