--liquibase formatted sql

--changeset erp:002-init-seed-data
-- ============================================================
-- Seed data: Tạo Role, Permission, Tài khoản Admin & Gán quyền
-- Mật khẩu mặc định: 123456789 (BCrypt cost 12: $2a$12$RFmydSknLc2h.UowCy34yeB1vvP1Y0vTFeA6gH/se8bbwS26rGThm)
-- ============================================================

-- 1. Thêm Roles
INSERT INTO role (id, code, name, description, type, status, created_at, updated_at, created_by, updated_by)
VALUES 
    ('a0000000-0000-0000-0000-000000000001', 'ADMIN', 'System Administrator', 'Quản trị viên toàn quyền hệ thống', 'SYSTEM', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('a0000000-0000-0000-0000-000000000002', 'USER', 'Standard User', 'Người dùng thông thường', 'SYSTEM', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM')
ON CONFLICT (code) DO NOTHING;

-- 2. Thêm Permissions cơ bản
INSERT INTO permission (id, code, name, module, description, status, created_at, updated_at, created_by, updated_by)
VALUES 
    ('b0000000-0000-0000-0000-000000000001', 'USER_READ', 'Xem danh sách người dùng', 'USER_MANAGEMENT', 'Quyền xem thông tin người dùng', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('b0000000-0000-0000-0000-000000000002', 'USER_WRITE', 'Chỉnh sửa người dùng', 'USER_MANAGEMENT', 'Quyền tạo/sửa/xóa người dùng', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('b0000000-0000-0000-0000-000000000003', 'ROLE_READ', 'Xem danh sách vai trò', 'ROLE_MANAGEMENT', 'Quyền xem vai trò', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('b0000000-0000-0000-0000-000000000004', 'ROLE_WRITE', 'Chỉnh sửa vai trò', 'ROLE_MANAGEMENT', 'Quyền tạo/sửa vai trò', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('b0000000-0000-0000-0000-000000000005', 'AUDIT_READ', 'Xem nhật ký hệ thống', 'AUDIT_LOG', 'Quyền xem audit log', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM')
ON CONFLICT (code) DO NOTHING;

-- 3. Gán full permissions cho ADMIN role
INSERT INTO role_permission (role_id, permission_id)
VALUES 
    ('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001'),
    ('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000002'),
    ('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000003'),
    ('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000004'),
    ('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000005')
ON CONFLICT DO NOTHING;

-- 4. Tạo tài khoản Admin mặc định (Username: admin, Password: 123456789)
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

-- 5. Gán vai trò ADMIN cho tài khoản admin
INSERT INTO account_role_assignment (account_id, role_id, status, assigned_at)
VALUES (
    'c0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'ACTIVE',
    NOW()
)
ON CONFLICT DO NOTHING;
