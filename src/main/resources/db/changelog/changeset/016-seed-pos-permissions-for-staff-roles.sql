--liquibase formatted sql
--changeset erp:seed-pos-permissions-for-staff-roles dbms:mysql
-- =============================================================================
-- MODULE: POS / IAM — CẤP QUYỀN POS CHO CÁC ROLE VẬN HÀNH TRÊN MYSQL PRODUCTION
-- Phiên bản: 1.0.0
-- Tác giả: ERP-UTT Core Architecture Team
--
-- Mục tiêu:
-- 1. Cấp quyền POS cho các role thực tế trên database (ROLE_BARISTA, ROLE_USER, STAFF, ROLE_CASHIER, ROLE_MANAGER).
-- 2. Đảm bảo nhân viên pha chế, thu ngân và quản lý chi nhánh có đủ quyền xem/hủy/cập nhật đơn hàng.
-- 3. Idempotent: Dùng INSERT IGNORE (MySQL), không ghi đè hoặc gây lỗi nếu đã tồn tại.
-- =============================================================================

-- ============================================================
-- 1. ROLE_BARISTA: Nhân viên pha chế — xem + hủy đơn
-- ============================================================
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.code = 'ROLE_BARISTA'
  AND p.code IN (
    'pos:order:view',
    'pos:order:cancel'
  );

-- ============================================================
-- 2. ROLE_USER: Người dùng hệ thống — quyền tối thiểu
-- ============================================================
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.code = 'ROLE_USER'
  AND p.code IN (
    'pos:order:view',
    'pos:order:cancel'
  );

-- ============================================================
-- 3. STAFF: Nhân viên cửa hàng — tương tự ROLE_USER
-- ============================================================
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.code = 'STAFF'
  AND p.code IN (
    'pos:order:view',
    'pos:order:cancel'
  );

-- ============================================================
-- 4. ROLE_CASHIER: Thu ngân POS — quản lý đơn + cập nhật trạng thái + xem giao hàng
-- ============================================================
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.code = 'ROLE_CASHIER'
  AND p.code IN (
    'pos:order:view',
    'pos:order:update',
    'pos:order:cancel',
    'pos:delivery:view'
  );

-- ============================================================
-- 5. ROLE_MANAGER: Quản lý chi nhánh — full quyền POS trong chi nhánh
-- ============================================================
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.code = 'ADMIN'
  AND p.code IN (
    'pos:order:view',
    'pos:order:update',
    'pos:order:cancel',
    'pos:order:delete',
    'pos:delivery:view',
    'pos:delivery:update',
    'pos:order_status_history:view'
  );

--rollback DELETE rp FROM role_permission rp
--rollback JOIN role r ON r.id = rp.role_id
--rollback JOIN permission p ON p.id = rp.permission_id
--rollback WHERE r.code IN ('ROLE_BARISTA','ROLE_USER','STAFF','ROLE_CASHIER','ROLE_MANAGER')
--rollback   AND p.code IN ('pos:order:view','pos:order:update','pos:order:cancel','pos:order:delete','pos:delivery:view','pos:delivery:update','pos:order_status_history:view');
