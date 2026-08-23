--liquibase formatted sql

--changeset erp:test-login-data
-- ============================================================
-- Dữ liệu TEST phục vụ FE kiểm thử luồng đăng nhập.
-- Chạy sau 001-init-schema, 002-init-data, 003-branch (idempotent).
--
-- Toàn bộ mật khẩu bên dưới là: 123456789
-- (BCrypt cost 12, prefix $2b$ tương thích với BCryptPasswordEncoder)
--
-- TÀI KHOẢN (ACCOUNT - nhân viên/quản trị nội bộ)
-- ------------------------------------------------------------------
-- | username   | password    | vai trò        | scope            | ghi chú
-- | manager01  | 123456789   | STORE_MANAGER | STORE (HN01)     | quản lý 1 chi nhánh cụ thể
-- | staff01    | 123456789   | STAFF         | STORE (HN01)     | nhân viên cửa hàng
-- | user01     | 123456789   | USER          | ALL_SYSTEM       | quyền toàn hệ thống
-- | admin      | 123456789   | ADMIN         | ALL_SYSTEM       | đã seed ở 002-init-data
--
-- KHÁCH HÀNG (CUSTOMER - đăng nhập bằng email/phone)
-- ------------------------------------------------------------------
-- | email                 | password    | email_verified | ghi chú
-- | customer01@erp.utt.vn | 123456789   | TRUE           | login trả về token ngay
-- | customer02@erp.utt.vn | 123456789   | FALSE          | login trả verifyToken (cần xác thực OTP)
--
-- CHI NHÁNH
-- ------------------------------------------------------------------
-- | code | name               | id
-- | HQ   | Trụ sở chính        | 00000000-0000-0000-0000-000000000001 (đã seed)
-- | HN01 | Chi nhánh Hà Nội    | 00000000-0000-0000-0000-000000000002 (mới)
-- ============================================================

-- 1. Chi nhánh test (STORE scope sẽ trỏ vào đây)
INSERT INTO branch (id, code, name, address, phone, timezone, status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    'HN01',
    'Chi nhánh Hà Nội',
    '123 Phố Huế, Hà Nội',
    '0900000002',
    'Asia/Ho_Chi_Minh',
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (code) DO NOTHING;

-- 2. Scope STORE gắn với chi nhánh HN01
INSERT INTO scope (id, scope_type, branch_id, status, created_at, updated_at, created_by, updated_by)
VALUES (
    'd0000000-0000-0000-0000-000000000002',
    'STORE',
    '00000000-0000-0000-0000-000000000002',
    'ACTIVE',
    NOW(),
    NOW(),
    'TEST',
    'TEST'
)
ON CONFLICT DO NOTHING;

-- 3. Tài khoản test (ACCOUNT)
INSERT INTO account (id, username, password, full_name, email, phone, status, auth_provider, has_local_password, primary_branch_id, created_at, updated_at, created_by, updated_by)
VALUES
    ('c0000000-0000-0000-0000-000000000011', 'manager01', '$2b$12$zANfZ7hVoF/s1JVWnqlQ8OLBKH8y56Y6UtEMTwztGr3e7qGk19DG2', 'Trần Thị Quản Lý', 'manager01@erp.utt.vn', '0911111111', 'ACTIVE', 'LOCAL', true, '00000000-0000-0000-0000-000000000001', NOW(), NOW(), 'TEST', 'TEST'),
    ('c0000000-0000-0000-0000-000000000012', 'staff01',    '$2b$12$zANfZ7hVoF/s1JVWnqlQ8OLBKH8y56Y6UtEMTwztGr3e7qGk19DG2', 'Lê Văn Nhân Viên', 'staff01@erp.utt.vn',    '0922222222', 'ACTIVE', 'LOCAL', true, '00000000-0000-0000-0000-000000000002', NOW(), NOW(), 'TEST', 'TEST'),
    ('c0000000-0000-0000-0000-000000000013', 'user01',     '$2b$12$zANfZ7hVoF/s1JVWnqlQ8OLBKH8y56Y6UtEMTwztGr3e7qGk19DG2', 'Phạm Thị Người Dùng','user01@erp.utt.vn',     '0933333333', 'ACTIVE', 'LOCAL', true, '00000000-0000-0000-0000-000000000001', NOW(), NOW(), 'TEST', 'TEST')
ON CONFLICT (username) DO NOTHING;

-- 4. Gán vai trò + scope cho từng tài khoản
-- manager01 -> STORE_MANAGER @ STORE(HN01)
INSERT INTO account_role_assignment (id, account_id, role_id, scope_id, status, assigned_at, created_at, created_by, updated_at, updated_by)
VALUES (
    gen_random_uuid(),
    'c0000000-0000-0000-0000-000000000011',
    'a0000000-0000-0000-0000-000000000003',
    'd0000000-0000-0000-0000-000000000002',
    'ACTIVE',
    NOW(),
    NOW(),
    'TEST',
    NOW(),
    'TEST'
)
ON CONFLICT DO NOTHING;

-- staff01 -> STAFF @ STORE(HN01)
INSERT INTO account_role_assignment (id, account_id, role_id, scope_id, status, assigned_at, created_at, created_by, updated_at, updated_by)
VALUES (
    gen_random_uuid(),
    'c0000000-0000-0000-0000-000000000012',
    'a0000000-0000-0000-0000-000000000004',
    'd0000000-0000-0000-0000-000000000002',
    'ACTIVE',
    NOW(),
    NOW(),
    'TEST',
    NOW(),
    'TEST'
)
ON CONFLICT DO NOTHING;

-- user01 -> USER @ ALL_SYSTEM
INSERT INTO account_role_assignment (id, account_id, role_id, scope_id, status, assigned_at, created_at, created_by, updated_at, updated_by)
VALUES (
    gen_random_uuid(),
    'c0000000-0000-0000-0000-000000000013',
    'a0000000-0000-0000-0000-000000000002',
    'd0000000-0000-0000-0000-000000000001',
    'ACTIVE',
    NOW(),
    NOW(),
    'TEST',
    NOW(),
    'TEST'
)
ON CONFLICT DO NOTHING;

-- 5. Khách hàng test (CUSTOMER)
INSERT INTO customer (id, customer_code, full_name, phone, email, password, auth_provider, has_local_password, email_verified, status, created_at, updated_at)
VALUES
    ('e0000000-0000-0000-0000-000000000021', 'CUS00021', 'Nguyễn Văn Khách', '0944444444', 'customer01@erp.utt.vn', '$2b$12$zANfZ7hVoF/s1JVWnqlQ8OLBKH8y56Y6UtEMTwztGr3e7qGk19DG2', 'LOCAL', true, true,  'ACTIVE', NOW(), NOW()),
    ('e0000000-0000-0000-0000-000000000022', 'CUS00022', 'Trần Thị Chưa Xác Thực', '0955555555', 'customer02@erp.utt.vn', '$2b$12$zANfZ7hVoF/s1JVWnqlQ8OLBKH8y56Y6UtEMTwztGr3e7qGk19DG2', 'LOCAL', true, false, 'ACTIVE', NOW(), NOW())
ON CONFLICT (customer_code) DO NOTHING;

--changeset erp:test-login-data-branch2
-- ============================================================
-- Bổ sung để TEST luồng CHỌN CHI NHÁNH (requiresScopeAssignment):
-- tài khoản manager01 được gán THÊM 1 scope STORE chi nhánh thứ 2,
-- tổng 2 scope -> login bắt buộc chọn đơn vị trước khi vào hệ thống.
-- Mật khẩu toàn bộ: 123456789
-- ============================================================

-- Chi nhánh thứ 2
INSERT INTO branch (id, code, name, address, phone, timezone, status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000003',
    'HN02',
    'Chi nhánh Hà Nội 2',
    '456 Tràng Tiền, Hà Nội',
    '0900000003',
    'Asia/Ho_Chi_Minh',
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (code) DO NOTHING;

-- Scope STORE gắn với HN02
INSERT INTO scope (id, scope_type, branch_id, status, created_at, updated_at, created_by, updated_by)
VALUES (
    'd0000000-0000-0000-0000-000000000003',
    'STORE',
    '00000000-0000-0000-0000-000000000003',
    'ACTIVE',
    NOW(),
    NOW(),
    'TEST',
    'TEST'
)
ON CONFLICT DO NOTHING;

-- Gán manager01 thêm scope STORE(HN02) -> tổng 2 scope -> bắt chọn chi nhánh
INSERT INTO account_role_assignment (id, account_id, role_id, scope_id, status, assigned_at, created_at, created_by, updated_at, updated_by)
VALUES (
    gen_random_uuid(),
    'c0000000-0000-0000-0000-000000000011',
    'a0000000-0000-0000-0000-000000000003',
    'd0000000-0000-0000-0000-000000000003',
    'ACTIVE',
    NOW(),
    NOW(),
    'TEST',
    NOW(),
    'TEST'
)
ON CONFLICT DO NOTHING;
