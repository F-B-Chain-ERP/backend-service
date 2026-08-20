--liquibase formatted sql

--changeset erp:003-auth-scope-foundation
CREATE TABLE scope (
    id          uuid         NOT NULL,
    scope_type  varchar(30)  NOT NULL,
    branch_id   uuid,
    status      varchar(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at  timestamp    NOT NULL,
    updated_at  timestamp    NOT NULL,
    created_by  varchar(100),
    updated_by  varchar(100),
    CONSTRAINT pk_scope PRIMARY KEY (id),
    CONSTRAINT ck_scope_type CHECK (scope_type IN ('ALL_SYSTEM', 'STORE', 'WAREHOUSE')),
    CONSTRAINT ck_scope_branch CHECK (
        (scope_type = 'ALL_SYSTEM' AND branch_id IS NULL)
        OR (scope_type IN ('STORE', 'WAREHOUSE') AND branch_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_scope_all_system ON scope (scope_type) WHERE scope_type = 'ALL_SYSTEM';
CREATE UNIQUE INDEX uq_scope_branch ON scope (scope_type, branch_id) WHERE branch_id IS NOT NULL;

INSERT INTO scope (id, scope_type, branch_id, status, created_at, updated_at, created_by, updated_by)
VALUES ('d0000000-0000-0000-0000-000000000001', 'ALL_SYSTEM', NULL, 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM');

ALTER TABLE account
    ADD COLUMN failed_login_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN locked_until timestamp,
    ADD COLUMN system_protected boolean NOT NULL DEFAULT false;

UPDATE account
SET auth_provider = 'LOCAL'
WHERE auth_provider IS NULL;

UPDATE account
SET system_protected = true
WHERE username = 'admin';

ALTER TABLE account
    ALTER COLUMN auth_provider SET DEFAULT 'LOCAL',
    ALTER COLUMN auth_provider SET NOT NULL;

ALTER TABLE account_role_assignment
    ADD COLUMN id uuid,
    ADD COLUMN scope_id uuid,
    ADD COLUMN assigned_by varchar(100),
    ADD COLUMN created_at timestamp,
    ADD COLUMN created_by varchar(100),
    ADD COLUMN updated_at timestamp,
    ADD COLUMN updated_by varchar(100);

UPDATE account_role_assignment
SET id = gen_random_uuid(),
    scope_id = 'd0000000-0000-0000-0000-000000000001',
    created_at = assigned_at,
    created_by = 'SYSTEM_MIGRATION',
    updated_at = assigned_at,
    updated_by = 'SYSTEM_MIGRATION';

ALTER TABLE account_role_assignment DROP CONSTRAINT pk_account_role_assignment;

ALTER TABLE account_role_assignment
    ALTER COLUMN id SET NOT NULL,
    ALTER COLUMN scope_id SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ADD CONSTRAINT pk_account_role_assignment PRIMARY KEY (id),
    ADD CONSTRAINT fk_account_role_scope FOREIGN KEY (scope_id) REFERENCES scope(id),
    ADD CONSTRAINT uq_account_role_scope UNIQUE (account_id, role_id, scope_id);

CREATE INDEX ix_assignment_account_effective
    ON account_role_assignment (account_id, status, expires_at);
CREATE INDEX ix_assignment_role ON account_role_assignment (role_id);
CREATE INDEX ix_assignment_scope ON account_role_assignment (scope_id);
CREATE INDEX ix_role_permission_permission ON role_permission (permission_id);
CREATE INDEX ix_audit_actor_created ON audit_log (actor_account_id, created_at);
CREATE INDEX ix_audit_action_created ON audit_log (action, created_at);

INSERT INTO role (id, code, name, description, type, status, created_at, updated_at, created_by, updated_by)
VALUES
    ('a0000000-0000-0000-0000-000000000003', 'STORE_MANAGER', 'Store Manager', 'Quản lý cửa hàng', 'SYSTEM', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('a0000000-0000-0000-0000-000000000004', 'STAFF', 'Staff', 'Nhân viên cửa hàng', 'SYSTEM', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('a0000000-0000-0000-0000-000000000005', 'INVENTORY_MANAGER', 'Inventory Manager', 'Quản lý kho và chuỗi cung ứng', 'SYSTEM', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('a0000000-0000-0000-0000-000000000006', 'PRODUCT_MANAGER', 'Product Manager', 'Quản lý sản phẩm và menu', 'SYSTEM', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
    ('a0000000-0000-0000-0000-000000000007', 'ACCOUNTANT', 'Accountant', 'Kế toán tài chính', 'SYSTEM', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permission (id, code, name, module, description, status, created_at, updated_at, created_by, updated_by)
SELECT gen_random_uuid(), code, name, module, name, 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM'
FROM (VALUES
    ('sys:account:manage', 'Quản lý tài khoản', 'SYS'),
    ('sys:role:manage', 'Quản lý vai trò', 'SYS'),
    ('sys:assignment:manage', 'Quản lý phân quyền', 'SYS'),
    ('sys:branch:manage', 'Quản lý chi nhánh', 'SYS'),
    ('sys:audit:view', 'Xem nhật ký bảo mật', 'SYS'),
    ('pos:order:create', 'Tạo đơn hàng', 'POS'),
    ('pos:order:update', 'Cập nhật đơn hàng', 'POS'),
    ('pos:order:cancel', 'Hủy đơn hàng', 'POS'),
    ('pos:payment:collect', 'Thu tiền', 'POS'),
    ('pos:kds:view', 'Xem KDS', 'POS'),
    ('pos:kds:update_status', 'Cập nhật trạng thái KDS', 'POS'),
    ('store:shift:manage', 'Quản lý ca', 'STORE'),
    ('store:stock:request', 'Yêu cầu nhập kho', 'STORE'),
    ('store:stock:approve', 'Duyệt nhập xuất kho', 'STORE'),
    ('store:report:daily', 'Xem báo cáo ngày', 'STORE'),
    ('inv:material:manage', 'Quản lý nguyên vật liệu', 'INV'),
    ('inv:transfer:manage', 'Quản lý chuyển hàng', 'INV'),
    ('inv:po:manage', 'Quản lý PO', 'INV'),
    ('inv:stock:audit', 'Kiểm kê kho', 'INV'),
    ('menu:item:manage', 'Quản lý món', 'MENU'),
    ('menu:bom:manage', 'Quản lý BOM', 'MENU'),
    ('menu:promo:manage', 'Quản lý khuyến mãi', 'MENU'),
    ('menu:loyalty:manage', 'Quản lý tích điểm', 'MENU'),
    ('fin:revenue:view_all', 'Xem doanh thu toàn hệ thống', 'FIN'),
    ('fin:cogs:calculate', 'Tính COGS', 'FIN'),
    ('fin:debt:manage', 'Quản lý công nợ', 'FIN'),
    ('fin:pnl:view', 'Xem P&L', 'FIN')
) AS source(code, name, module)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'pos:order:create','pos:order:update','pos:order:cancel','pos:payment:collect','pos:kds:view','pos:kds:update_status',
    'store:shift:manage','store:stock:request','store:stock:approve','store:report:daily'
) WHERE r.code = 'STORE_MANAGER' ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'pos:order:create','pos:order:update','pos:payment:collect','pos:kds:view','pos:kds:update_status','store:stock:request'
) WHERE r.code = 'STAFF' ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'inv:material:manage','inv:transfer:manage','inv:po:manage','inv:stock:audit'
) WHERE r.code = 'INVENTORY_MANAGER' ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'menu:item:manage','menu:bom:manage','menu:promo:manage','menu:loyalty:manage'
) WHERE r.code = 'PRODUCT_MANAGER' ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN (
    'fin:revenue:view_all','fin:cogs:calculate','fin:debt:manage','fin:pnl:view'
) WHERE r.code = 'ACCOUNTANT' ON CONFLICT DO NOTHING;
