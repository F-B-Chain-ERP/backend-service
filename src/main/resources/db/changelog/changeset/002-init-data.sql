--liquibase formatted sql

--changeset erp:baseline-data
-- ============================================================
-- Baseline seed data (consolidated từ changeset 002-003).
-- Idempotent: mọi INSERT đều có ON CONFLICT DO NOTHING / WHERE NOT EXISTS
-- nên có thể chạy an toàn nhiều lần.
-- Mật khẩu admin mặc định: 123456789
-- (BCrypt cost 12: $2a$12$RFmydSknLc2h.UowCy34yeB1vvP1Y0vTFeA6gH/se8bbwS26rGThm)
-- ============================================================

-- 1. Scope mặc định (toàn hệ thống)
INSERT INTO scope (id, scope_type, branch_id, status, created_at, updated_at, created_by, updated_by)
VALUES ('d0000000-0000-0000-0000-000000000001', 'ALL_SYSTEM', NULL, 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM')
ON CONFLICT DO NOTHING;

-- 2. Roles
INSERT INTO role (id, code, name, description, type, status, created_at, updated_at, created_by, updated_by)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'ADMIN',           'System Administrator', 'Quản trị viên toàn quyền hệ thống', 'SYSTEM', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('a0000000-0000-0000-0000-000000000002', 'USER',            'Standard User',         'Người dùng thông thường',          'SYSTEM', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('a0000000-0000-0000-0000-000000000003', 'STORE_MANAGER',   'Store Manager',         'Quản lý cửa hàng',                'SYSTEM', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('a0000000-0000-0000-0000-000000000004', 'STAFF',           'Staff',                 'Nhân viên cửa hàng',              'SYSTEM', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('a0000000-0000-0000-0000-000000000005', 'INVENTORY_MANAGER','Inventory Manager',    'Quản lý kho và chuỗi cung ứng',    'SYSTEM', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('a0000000-0000-0000-0000-000000000006', 'PRODUCT_MANAGER', 'Product Manager',       'Quản lý sản phẩm và menu',        'SYSTEM', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('a0000000-0000-0000-0000-000000000007', 'ACCOUNTANT',      'Accountant',            'Kế toán tài chính',               'SYSTEM', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM')
ON CONFLICT (code) DO NOTHING;

-- 3. Permissions cơ bản
INSERT INTO permission (id, code, name, module, description, status, created_at, updated_at, created_by, updated_by)
VALUES
    ('b0000000-0000-0000-0000-000000000001', 'USER_READ',    'Xem danh sách người dùng',      'USER_MANAGEMENT', 'Quyền xem thông tin người dùng',  'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('b0000000-0000-0000-0000-000000000002', 'USER_WRITE',   'Chỉnh sửa người dùng',          'USER_MANAGEMENT', 'Quyền tạo/sửa/xóa người dùng',    'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('b0000000-0000-0000-0000-000000000003', 'ROLE_READ',    'Xem danh sách vai trò',         'ROLE_MANAGEMENT', 'Quyền xem vai trò',               'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('b0000000-0000-0000-0000-000000000004', 'ROLE_WRITE',   'Chỉnh sửa vai trò',             'ROLE_MANAGEMENT', 'Quyền tạo/sửa vai trò',           'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('b0000000-0000-0000-0000-000000000005', 'AUDIT_READ',   'Xem nhật ký hệ thống',          'AUDIT_LOG',       'Quyền xem audit log',             'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM')
ON CONFLICT (code) DO NOTHING;

-- 3b. Permissions mở rộng (auth/scope foundation)
INSERT INTO permission (id, code, name, module, description, status, created_at, updated_at, created_by, updated_by)
SELECT gen_random_uuid(), code, name, module, name, 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'
FROM (VALUES
    ('sys:account:manage',  'Quản lý tài khoản',     'SYS'),
    ('sys:role:manage',     'Quản lý vai trò',       'SYS'),
    ('sys:assignment:manage','Quản lý phân quyền',   'SYS'),
    ('sys:branch:manage',   'Quản lý chi nhánh',     'SYS'),
    ('sys:audit:view',      'Xem nhật ký bảo mật',   'SYS'),
    ('pos:order:create',    'Tạo đơn hàng',          'POS'),
    ('pos:order:update',    'Cập nhật đơn hàng',     'POS'),
    ('pos:order:cancel',    'Hủy đơn hàng',          'POS'),
    ('pos:payment:collect', 'Thu tiền',              'POS'),
    ('pos:kds:view',        'Xem KDS',               'POS'),
    ('pos:kds:update_status','Cập nhật trạng thái KDS','POS'),
    ('store:shift:manage',  'Quản lý ca',            'STORE'),
    ('store:stock:request', 'Yêu cầu nhập kho',      'STORE'),
    ('store:stock:approve', 'Duyệt nhập xuất kho',   'STORE'),
    ('store:report:daily',  'Xem báo cáo ngày',      'STORE'),
    ('inv:material:manage', 'Quản lý nguyên vật liệu','INV'),
    ('inv:transfer:manage', 'Quản lý chuyển hàng',   'INV'),
    ('inv:po:manage',       'Quản lý PO',            'INV'),
    ('inv:stock:audit',     'Kiểm kê kho',           'INV'),
    ('menu:item:manage',    'Quản lý món',           'MENU'),
    ('menu:bom:manage',     'Quản lý BOM',           'MENU'),
    ('menu:promo:manage',   'Quản lý khuyến mãi',    'MENU'),
    ('menu:loyalty:manage', 'Quản lý tích điểm',     'MENU'),
    ('fin:revenue:view_all','Xem doanh thu toàn hệ thống','FIN'),
    ('fin:cogs:calculate',  'Tính COGS',             'FIN'),
    ('fin:debt:manage',     'Quản lý công nợ',       'FIN'),
    ('fin:pnl:view',        'Xem P&L',               'FIN')
) AS source(code, name, module)
ON CONFLICT (code) DO NOTHING;

-- 4. Gán quyền cho các vai trò
INSERT INTO role_permission (role_id, permission_id)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001'),
    ('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000002'),
    ('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000003'),
    ('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000004'),
    ('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000005')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'pos:order:create','pos:order:update','pos:order:cancel','pos:payment:collect','pos:kds:view','pos:kds:update_status',
    'store:shift:manage','store:stock:request','store:stock:approve','store:report:daily'
) WHERE r.code = 'STORE_MANAGER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'pos:order:create','pos:order:update','pos:payment:collect','pos:kds:view','pos:kds:update_status','store:stock:request'
) WHERE r.code = 'STAFF'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'inv:material:manage','inv:transfer:manage','inv:po:manage','inv:stock:audit'
) WHERE r.code = 'INVENTORY_MANAGER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'menu:item:manage','menu:bom:manage','menu:promo:manage','menu:loyalty:manage'
) WHERE r.code = 'PRODUCT_MANAGER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'fin:revenue:view_all','fin:cogs:calculate','fin:debt:manage','fin:pnl:view'
) WHERE r.code = 'ACCOUNTANT'
ON CONFLICT DO NOTHING;

-- 5. Tài khoản Admin mặc định (username: admin, password: 123456789)
INSERT INTO account (id, username, password, full_name, email, phone, status, auth_provider, created_at, updated_at, created_by, updated_by)
VALUES (
    'c0000000-0000-0000-0000-000000000001',
    'admin',
    '$2a$12$RFmydSknLc2h.UowCy34yeB1vvP1Y0vTFeA6gH/se8bbwS26rGThm',
    'System Administrator',
    'admin@erp.utt.edu.vn',
    '0901234567',
    'ACTIVE',
    'LOCAL',
    NOW(),
    NOW(),
    'SYSTEM',
    'SYSTEM'
)
ON CONFLICT (username) DO NOTHING;

-- 6. Gán vai trò ADMIN + scope ALL_SYSTEM cho tài khoản admin
INSERT INTO account_role_assignment (id, account_id, role_id, scope_id, status, assigned_at, created_at, created_by, updated_at, updated_by)
VALUES (
    gen_random_uuid(),
    'c0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'd0000000-0000-0000-0000-000000000001',
    'ACTIVE',
    NOW(),
    NOW(),
    'SYSTEM',
    NOW(),
    'SYSTEM'
)
ON CONFLICT DO NOTHING;
