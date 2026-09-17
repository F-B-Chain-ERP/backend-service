--liquibase formatted sql

--changeset erp:add-order-pickup-slot
-- =============================================================================
-- MODULE: POS & ORG — KHUNG GIỜ PICKUP VÀ ĐẶT HÀNG (PICKUP SLOTS IN ORDERS)
-- Phiên bản: 1.0.0
-- Tác giả: ERP-UTT Core Architecture Team
--
-- Mục tiêu changeset:
-- 1. Bổ sung cột `pickup_time_slot_id` vào bảng `orders` tham chiếu tới `pickup_time_slot(id)`
--    để kiểm soát năng lực phục vụ, đếm số đơn theo slot và khóa chống quá tải quầy pha chế.
-- 2. Tạo index tối ưu hóa truy vấn đếm đơn theo chi nhánh, slot và thời gian.
-- =============================================================================

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS pickup_time_slot_id UUID REFERENCES pickup_time_slot(id) ON DELETE SET NULL;

COMMENT ON COLUMN orders.pickup_time_slot_id IS 'Khung giờ pickup mà khách hàng đã chọn để đến lấy món (null nếu giao hàng hoặc không chọn slot)';

CREATE INDEX IF NOT EXISTS idx_orders_branch_pickup_slot_created ON orders (branch_id, pickup_time_slot_id, created_at);
