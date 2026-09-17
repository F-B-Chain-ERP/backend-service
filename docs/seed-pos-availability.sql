-- Seed availability bán hàng theo chi nhánh (chạy TRỰC TIẾP trên DB server, không qua Liquibase).
-- An toàn chạy lại nhiều lần (WHERE NOT EXISTS, không cần unique constraint).
-- Nghiệp vụ: mặc định mở bán hết SP/topping ACTIVE ở mọi chi nhánh ACTIVE,
-- giá bán = base_price (sale_price NULL), quản lý tắt món ngoại lệ sau.
-- Tồn bán trong ngày (branch_variant_daily_stock) KHÔNG seed ở đây:
-- code tự carryover ở lần check đầu tiên, ngày đầu = 0 rồi quản lý restock qua API.

-- 1. Mở bán sản phẩm
INSERT INTO branch_product_availability
    (id, branch_id, product_id, is_available, sale_price, status, created_at, updated_at, created_by, updated_by)
SELECT gen_random_uuid(), b.id, p.id, true, NULL, 'ACTIVE', now(), now(), 'system-seed', 'system-seed'
FROM branch b
CROSS JOIN product p
WHERE b.status = 'ACTIVE'
  AND p.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM branch_product_availability a
      WHERE a.branch_id = b.id AND a.product_id = p.id AND a.status = 'ACTIVE'
  );

-- 2. Mở bán topping
INSERT INTO branch_topping_availability
    (id, branch_id, topping_id, is_available, status, created_at, updated_at, created_by, updated_by)
SELECT gen_random_uuid(), b.id, t.id, true, 'ACTIVE', now(), now(), 'system-seed', 'system-seed'
FROM branch b
CROSS JOIN topping t
WHERE b.status = 'ACTIVE'
  AND t.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM branch_topping_availability a
      WHERE a.branch_id = b.id AND a.topping_id = t.id AND a.status = 'ACTIVE'
  );

-- 3. Kiểm tra sau seed
SELECT 'product_availability' AS tbl, count(*) FROM branch_product_availability WHERE status = 'ACTIVE'
UNION ALL
SELECT 'topping_availability', count(*) FROM branch_topping_availability WHERE status = 'ACTIVE';
