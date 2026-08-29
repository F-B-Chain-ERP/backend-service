--liquibase formatted sql

--changeset erp:add-permission-seed
-- ============================================================
-- (1) BOOTSTRAP TỐI THIỂU — tách ra từ 002-init-data.sql.
--     Đảm bảo hệ thống có đủ:
--       - scope ALL_SYSTEM
--       - role ADMIN
--       - tài khoản admin (mật khẩu: 123456789)
--       - gán admin -> ADMIN @ ALL_SYSTEM
--     để phần seed permission bên dưới (và controller phân quyền)
--     có thể vận hành. Idempotent (ON CONFLICT DO NOTHING).
-- ============================================================

INSERT INTO scope (id, scope_type, branch_id, status, created_at, updated_at, created_by, updated_by)
VALUES ('d0000000-0000-0000-0000-000000000001', 'ALL_SYSTEM', NULL, 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM')
ON CONFLICT DO NOTHING;

INSERT INTO role (id, code, name, description, type, status, created_at, updated_at, created_by, updated_by)
VALUES ('a0000000-0000-0000-0000-000000000001', 'ADMIN', 'System Administrator', 'Quản trị viên toàn quyền hệ thống', 'SYSTEM', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM')
ON CONFLICT (code) DO NOTHING;

INSERT INTO account (id, username, password, full_name, email, phone, status, auth_provider, has_local_password, primary_branch_id, created_at, updated_at, created_by, updated_by)
VALUES (
    'c0000000-0000-0000-0000-000000000001',
    'admin',
    '$2a$12$RFmydSknLc2h.UowCy34yeB1vvP1Y0vTFeA6gH/se8bbwS26rGThm',
    'System Administrator',
    'admin@erp.utt.edu.vn',
    '0901234567',
    'ACTIVE',
    'LOCAL',
    true,
    NULL,
    NOW(),
    NOW(),
    'SYSTEM',
    'SYSTEM'
)
ON CONFLICT (username) DO NOTHING;

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

-- ============================================================
-- (2) SEED PERMISSION — reset permission data: xoa sach role_permission
--     & permission, sau do seed 199 quyen moi va gan toan bo 199
--     quyen cho role ADMIN (full access) de he thong hoat dong
--     binh thuong (controller phan quyen su dung cac ma nay).
-- ============================================================

DELETE FROM role_permission;
DELETE FROM permission;

INSERT INTO permission (
    id,
    code,
    name,
    module,
    description,
    status,
    created_at,
    updated_at
)
VALUES
-- =========================================================
-- SYS
-- =========================================================
(gen_random_uuid(), 'sys:account:create', 'Create Account', 'SYS', 'Create internal account', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:account:view', 'View Account', 'SYS', 'View internal account', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:account:update', 'Update Account', 'SYS', 'Update internal account', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:account:delete', 'Delete Account', 'SYS', 'Delete internal account', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'sys:role:create', 'Create Role', 'SYS', 'Create role', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:role:view', 'View Role', 'SYS', 'View role', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:role:update', 'Update Role', 'SYS', 'Update role', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:role:delete', 'Delete Role', 'SYS', 'Delete role', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'sys:permission:view', 'View Permission', 'SYS', 'View permission', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'sys:role_permission:create', 'Create Role Permission', 'SYS', 'Assign permission to role', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:role_permission:delete', 'Delete Role Permission', 'SYS', 'Remove permission from role', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'sys:role_assignment:create', 'Create Role Assignment', 'SYS', 'Assign role to account', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:role_assignment:view', 'View Role Assignment', 'SYS', 'View account role assignments', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:role_assignment:update', 'Update Role Assignment', 'SYS', 'Update account role assignment', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:role_assignment:delete', 'Delete Role Assignment', 'SYS', 'Remove role assignment', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'sys:scope:create', 'Create Scope', 'SYS', 'Create scope', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:scope:view', 'View Scope', 'SYS', 'View scope', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:scope:update', 'Update Scope', 'SYS', 'Update scope', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:scope:delete', 'Delete Scope', 'SYS', 'Delete scope', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'sys:branch:create', 'Create Branch', 'SYS', 'Create branch', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:branch:view', 'View Branch', 'SYS', 'View branch', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:branch:update', 'Update Branch', 'SYS', 'Update branch', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:branch:delete', 'Delete Branch', 'SYS', 'Delete branch', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'sys:branch_hours:create', 'Create Branch Hours', 'SYS', 'Create branch operating hours', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:branch_hours:view', 'View Branch Hours', 'SYS', 'View branch operating hours', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:branch_hours:update', 'Update Branch Hours', 'SYS', 'Update branch operating hours', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:branch_hours:delete', 'Delete Branch Hours', 'SYS', 'Delete branch operating hours', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'sys:pickup_slot:create', 'Create Pickup Slot', 'SYS', 'Create pickup time slot', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:pickup_slot:view', 'View Pickup Slot', 'SYS', 'View pickup time slot', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:pickup_slot:update', 'Update Pickup Slot', 'SYS', 'Update pickup time slot', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:pickup_slot:delete', 'Delete Pickup Slot', 'SYS', 'Delete pickup time slot', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'sys:setting:create', 'Create Setting', 'SYS', 'Create system setting', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:setting:view', 'View Setting', 'SYS', 'View system setting', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:setting:update', 'Update Setting', 'SYS', 'Update system setting', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:setting:delete', 'Delete Setting', 'SYS', 'Delete system setting', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'sys:audit:view', 'View Audit Log', 'SYS', 'View audit logs', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'sys:session:view', 'View Session', 'SYS', 'View account sessions', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'sys:session:delete', 'Delete Session', 'SYS', 'Revoke account session', 'ACTIVE', NOW(), NOW()),

-- =========================================================
-- CUSTOMER
-- =========================================================
(gen_random_uuid(), 'customer:customer:create', 'Create Customer', 'CUSTOMER', 'Create customer', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'customer:customer:view', 'View Customer', 'CUSTOMER', 'View customer', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'customer:customer:update', 'Update Customer', 'CUSTOMER', 'Update customer', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'customer:customer:delete', 'Delete Customer', 'CUSTOMER', 'Delete customer', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'customer:address:create', 'Create Customer Address', 'CUSTOMER', 'Create customer address', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'customer:address:view', 'View Customer Address', 'CUSTOMER', 'View customer address', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'customer:address:update', 'Update Customer Address', 'CUSTOMER', 'Update customer address', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'customer:address:delete', 'Delete Customer Address', 'CUSTOMER', 'Delete customer address', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'customer:loyalty:create', 'Create Loyalty Account', 'CUSTOMER', 'Create customer loyalty account', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'customer:loyalty:view', 'View Loyalty Account', 'CUSTOMER', 'View customer loyalty account', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'customer:loyalty:update', 'Update Loyalty Account', 'CUSTOMER', 'Update customer loyalty account', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'customer:loyalty:delete', 'Delete Loyalty Account', 'CUSTOMER', 'Delete customer loyalty account', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'customer:loyalty_history:view', 'View Loyalty History', 'CUSTOMER', 'View loyalty point history', 'ACTIVE', NOW(), NOW()),

-- =========================================================
-- MENU
-- =========================================================
(gen_random_uuid(), 'menu:category:create', 'Create Category', 'MENU', 'Create product or material category', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:category:view', 'View Category', 'MENU', 'View category', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:category:update', 'Update Category', 'MENU', 'Update category', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:category:delete', 'Delete Category', 'MENU', 'Delete category', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'menu:unit:create', 'Create Unit', 'MENU', 'Create unit', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:unit:view', 'View Unit', 'MENU', 'View unit', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:unit:update', 'Update Unit', 'MENU', 'Update unit', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:unit:delete', 'Delete Unit', 'MENU', 'Delete unit', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'menu:product:create', 'Create Product', 'MENU', 'Create product', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:product:view', 'View Product', 'MENU', 'View product', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:product:update', 'Update Product', 'MENU', 'Update product', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:product:delete', 'Delete Product', 'MENU', 'Delete product', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'menu:variant:create', 'Create Variant', 'MENU', 'Create product variant', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:variant:view', 'View Variant', 'MENU', 'View product variant', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:variant:update', 'Update Variant', 'MENU', 'Update product variant', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:variant:delete', 'Delete Variant', 'MENU', 'Delete product variant', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'menu:bom:create', 'Create BOM', 'MENU', 'Create product recipe', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:bom:view', 'View BOM', 'MENU', 'View product recipe', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:bom:update', 'Update BOM', 'MENU', 'Update product recipe', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:bom:delete', 'Delete BOM', 'MENU', 'Delete product recipe', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'menu:topping:create', 'Create Topping', 'MENU', 'Create topping', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:topping:view', 'View Topping', 'MENU', 'View topping', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:topping:update', 'Update Topping', 'MENU', 'Update topping', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:topping:delete', 'Delete Topping', 'MENU', 'Delete topping', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'menu:product_topping:create', 'Create Product Topping', 'MENU', 'Assign topping to product', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:product_topping:view', 'View Product Topping', 'MENU', 'View product toppings', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:product_topping:update', 'Update Product Topping', 'MENU', 'Update product topping configuration', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:product_topping:delete', 'Delete Product Topping', 'MENU', 'Remove topping from product', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'menu:combo:create', 'Create Combo', 'MENU', 'Create combo', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:combo:view', 'View Combo', 'MENU', 'View combo', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:combo:update', 'Update Combo', 'MENU', 'Update combo', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:combo:delete', 'Delete Combo', 'MENU', 'Delete combo', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'menu:product_availability:create', 'Create Product Availability', 'MENU', 'Create branch product availability configuration', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:product_availability:view', 'View Product Availability', 'MENU', 'View branch product availability', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:product_availability:update', 'Update Product Availability', 'MENU', 'Update branch product availability', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:product_availability:delete', 'Delete Product Availability', 'MENU', 'Delete branch product availability', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'menu:topping_availability:create', 'Create Topping Availability', 'MENU', 'Create branch topping availability configuration', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:topping_availability:view', 'View Topping Availability', 'MENU', 'View branch topping availability', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:topping_availability:update', 'Update Topping Availability', 'MENU', 'Update branch topping availability', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:topping_availability:delete', 'Delete Topping Availability', 'MENU', 'Delete branch topping availability', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'menu:voucher:create', 'Create Voucher', 'MENU', 'Create voucher', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:voucher:view', 'View Voucher', 'MENU', 'View voucher', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:voucher:update', 'Update Voucher', 'MENU', 'Update voucher', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:voucher:delete', 'Delete Voucher', 'MENU', 'Delete voucher', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'menu:voucher_branch:view', 'View Voucher Branch', 'MENU', 'View voucher branch assignments', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:voucher_branch:create', 'Create Voucher Branch', 'MENU', 'Assign voucher to branch', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'menu:voucher_branch:delete', 'Delete Voucher Branch', 'MENU', 'Remove voucher from branch', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'menu:voucher_usage:view', 'View Voucher Usage', 'MENU', 'View voucher usage history', 'ACTIVE', NOW(), NOW()),

-- =========================================================
-- PROC
-- =========================================================
(gen_random_uuid(), 'proc:supplier:create', 'Create Supplier', 'PROC', 'Create supplier', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'proc:supplier:view', 'View Supplier', 'PROC', 'View supplier', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'proc:supplier:update', 'Update Supplier', 'PROC', 'Update supplier', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'proc:supplier:delete', 'Delete Supplier', 'PROC', 'Delete supplier', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'proc:supplier_material:create', 'Create Supplier Material', 'PROC', 'Create supplier material relationship', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'proc:supplier_material:view', 'View Supplier Material', 'PROC', 'View supplier material relationship', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'proc:supplier_material:update', 'Update Supplier Material', 'PROC', 'Update supplier material relationship', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'proc:supplier_material:delete', 'Delete Supplier Material', 'PROC', 'Delete supplier material relationship', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'proc:purchase_order:create', 'Create Purchase Order', 'PROC', 'Create purchase order', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'proc:purchase_order:view', 'View Purchase Order', 'PROC', 'View purchase order', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'proc:purchase_order:update', 'Update Purchase Order', 'PROC', 'Update purchase order', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'proc:purchase_order:delete', 'Delete Purchase Order', 'PROC', 'Delete purchase order', 'ACTIVE', NOW(), NOW()),

-- =========================================================
-- INV
-- =========================================================
(gen_random_uuid(), 'inv:material:create', 'Create Material', 'INV', 'Create material', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:material:view', 'View Material', 'INV', 'View material', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:material:update', 'Update Material', 'INV', 'Update material', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:material:delete', 'Delete Material', 'INV', 'Delete material', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'inv:warehouse:create', 'Create Warehouse', 'INV', 'Create warehouse', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:warehouse:view', 'View Warehouse', 'INV', 'View warehouse', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:warehouse:update', 'Update Warehouse', 'INV', 'Update warehouse', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:warehouse:delete', 'Delete Warehouse', 'INV', 'Delete warehouse', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'inv:stock_balance:view', 'View Stock Balance', 'INV', 'View material stock balance', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'inv:stock_in:create', 'Create Stock In', 'INV', 'Create stock receipt', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:stock_in:view', 'View Stock In', 'INV', 'View stock receipt', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:stock_in:update', 'Update Stock In', 'INV', 'Update stock receipt', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:stock_in:delete', 'Delete Stock In', 'INV', 'Delete stock receipt', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'inv:stock_out:create', 'Create Stock Out', 'INV', 'Create stock issue', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:stock_out:view', 'View Stock Out', 'INV', 'View stock issue', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:stock_out:update', 'Update Stock Out', 'INV', 'Update stock issue', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:stock_out:delete', 'Delete Stock Out', 'INV', 'Delete stock issue', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'inv:stock_transfer:create', 'Create Stock Transfer', 'INV', 'Create stock transfer', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:stock_transfer:view', 'View Stock Transfer', 'INV', 'View stock transfer', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:stock_transfer:update', 'Update Stock Transfer', 'INV', 'Update stock transfer', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:stock_transfer:delete', 'Delete Stock Transfer', 'INV', 'Delete stock transfer', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'inv:stock_count:create', 'Create Stock Count', 'INV', 'Create stock count', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:stock_count:view', 'View Stock Count', 'INV', 'View stock count', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:stock_count:update', 'Update Stock Count', 'INV', 'Update stock count', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'inv:stock_count:delete', 'Delete Stock Count', 'INV', 'Delete stock count', 'ACTIVE', NOW(), NOW()),

-- =========================================================
-- STORE
-- =========================================================
(gen_random_uuid(), 'store:shift:create', 'Create Shift', 'STORE', 'Create store shift', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'store:shift:view', 'View Shift', 'STORE', 'View store shift', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'store:shift:update', 'Update Shift', 'STORE', 'Update store shift', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'store:shift:delete', 'Delete Shift', 'STORE', 'Delete store shift', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'store:shift_assignment:create', 'Create Shift Assignment', 'STORE', 'Assign employee to shift', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'store:shift_assignment:view', 'View Shift Assignment', 'STORE', 'View shift assignments', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'store:shift_assignment:update', 'Update Shift Assignment', 'STORE', 'Update shift assignment', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'store:shift_assignment:delete', 'Delete Shift Assignment', 'STORE', 'Delete shift assignment', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'store:daily_report:create', 'Create Daily Report', 'STORE', 'Create store daily report', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'store:daily_report:view', 'View Daily Report', 'STORE', 'View store daily report', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'store:daily_report:update', 'Update Daily Report', 'STORE', 'Update store daily report', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'store:daily_report:delete', 'Delete Daily Report', 'STORE', 'Delete store daily report', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'store:product_stock:view', 'View Product Stock', 'STORE', 'View branch product stock', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'store:product_stock_history:view', 'View Product Stock History', 'STORE', 'View branch product stock history', 'ACTIVE', NOW(), NOW()),

-- =========================================================
-- POS
-- =========================================================
(gen_random_uuid(), 'pos:order:create', 'Create Order', 'POS', 'Create order', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'pos:order:view', 'View Order', 'POS', 'View order', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'pos:order:update', 'Update Order', 'POS', 'Update order', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'pos:order:delete', 'Delete Order', 'POS', 'Delete order', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'pos:delivery:create', 'Create Delivery', 'POS', 'Create order delivery', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'pos:delivery:view', 'View Delivery', 'POS', 'View order delivery', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'pos:delivery:update', 'Update Delivery', 'POS', 'Update order delivery', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'pos:delivery:delete', 'Delete Delivery', 'POS', 'Delete order delivery', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'pos:order_status_history:view', 'View Order Status History', 'POS', 'View order status history', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'pos:payment_intent:create', 'Create Payment Intent', 'POS', 'Create payment intent', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'pos:payment_intent:view', 'View Payment Intent', 'POS', 'View payment intent', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'pos:payment_intent:update', 'Update Payment Intent', 'POS', 'Update payment intent', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'pos:transaction:create', 'Create Transaction', 'POS', 'Create payment transaction', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'pos:transaction:view', 'View Transaction', 'POS', 'View payment transaction', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'pos:transaction:update', 'Update Transaction', 'POS', 'Update payment transaction', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'pos:refund:create', 'Create Refund', 'POS', 'Create refund request', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'pos:refund:view', 'View Refund', 'POS', 'View refund', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'pos:refund:update', 'Update Refund', 'POS', 'Update refund', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'pos:refund:delete', 'Delete Refund', 'POS', 'Delete refund', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'pos:kds_ticket:view', 'View KDS Ticket', 'POS', 'View kitchen display tickets', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'pos:kds_ticket:update', 'Update KDS Ticket', 'POS', 'Update kitchen display ticket status', 'ACTIVE', NOW(), NOW()),

-- =========================================================
-- FIN
-- =========================================================
(gen_random_uuid(), 'fin:payable:create', 'Create Payable', 'FIN', 'Create accounts payable record', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'fin:payable:view', 'View Payable', 'FIN', 'View accounts payable', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'fin:payable:update', 'Update Payable', 'FIN', 'Update accounts payable', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'fin:payable:delete', 'Delete Payable', 'FIN', 'Delete accounts payable', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'fin:payable_payment:create', 'Create Payable Payment', 'FIN', 'Create payable payment', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'fin:payable_payment:view', 'View Payable Payment', 'FIN', 'View payable payment', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'fin:payable_payment:update', 'Update Payable Payment', 'FIN', 'Update payable payment', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'fin:payable_payment:delete', 'Delete Payable Payment', 'FIN', 'Delete payable payment', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'fin:expense:create', 'Create Expense', 'FIN', 'Create expense', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'fin:expense:view', 'View Expense', 'FIN', 'View expense', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'fin:expense:update', 'Update Expense', 'FIN', 'Update expense', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'fin:expense:delete', 'Delete Expense', 'FIN', 'Delete expense', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'fin:financial_summary:view', 'View Financial Summary', 'FIN', 'View branch financial summary', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'fin:financial_summary:update', 'Update Financial Summary', 'FIN', 'Update financial summary', 'ACTIVE', NOW(), NOW()),

-- =========================================================
-- PLATFORM
-- =========================================================
(gen_random_uuid(), 'platform:notification_template:create', 'Create Notification Template', 'PLATFORM', 'Create notification template', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'platform:notification_template:view', 'View Notification Template', 'PLATFORM', 'View notification template', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'platform:notification_template:update', 'Update Notification Template', 'PLATFORM', 'Update notification template', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'platform:notification_template:delete', 'Delete Notification Template', 'PLATFORM', 'Delete notification template', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'platform:notification:create', 'Create Notification', 'PLATFORM', 'Create notification', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'platform:notification:view', 'View Notification', 'PLATFORM', 'View notification', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'platform:notification:update', 'Update Notification', 'PLATFORM', 'Update notification', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'platform:chat_room:create', 'Create Chat Room', 'PLATFORM', 'Create chat room', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'platform:chat_room:view', 'View Chat Room', 'PLATFORM', 'View chat room', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'platform:chat_room:update', 'Update Chat Room', 'PLATFORM', 'Update chat room', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'platform:chat_message:create', 'Create Chat Message', 'PLATFORM', 'Create chat message', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'platform:chat_message:view', 'View Chat Message', 'PLATFORM', 'View chat message', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'platform:chat_message:update', 'Update Chat Message', 'PLATFORM', 'Update chat message', 'ACTIVE', NOW(), NOW()),

(gen_random_uuid(), 'platform:export_request:create', 'Create Export Request', 'PLATFORM', 'Create export request', 'ACTIVE', NOW(), NOW()),
(gen_random_uuid(), 'platform:export_request:view', 'View Export Request', 'PLATFORM', 'View export request', 'ACTIVE', NOW(), NOW())

ON CONFLICT (code) DO NOTHING;

-- Gan toan bo 199 quyen cho role ADMIN de he thong hoat dong binh thuong (full access).
-- Role ADMIN duoc tao boi phan bootstrap o dau file (code = 'ADMIN').
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;
