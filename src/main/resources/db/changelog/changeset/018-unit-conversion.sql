--liquibase formatted sql

--changeset erp:unit-conversion
-- =============================================================================
-- MODULE: INV — BẢNG QUY ĐỔI ĐƠN VỊ (UNIT CONVERSION)
-- Phiên bản: 1.0.0
--
-- Mục tiêu changeset:
-- 1. Bảng `unit_conversion`: 1 chiều quy đổi giữa 2 đơn vị cùng nhóm
--    (khối lượng/thể tích/đếm), chiều ngược code tự đảo 1/factor.
-- 2. Bổ sung `pack_unit_id` + `pack_to_base_factor` cho `material` (quy đổi đóng
--    gói theo từng NVL: bao/thùng -> đơn vị gốc).
-- 3. Seed các cặp phổ quát (KG->G, L->ML, LIT->ML).
-- =============================================================================

CREATE TABLE IF NOT EXISTS unit_conversion (
    id             uuid           NOT NULL,
    status         varchar(30)    NOT NULL DEFAULT 'ACTIVE',
    from_unit_id   uuid           NOT NULL,
    to_unit_id     uuid           NOT NULL,
    factor         numeric(18, 6) NOT NULL,
    created_at     timestamp      NOT NULL,
    created_by     varchar(100),
    updated_at     timestamp      NOT NULL,
    updated_by     varchar(100),
    CONSTRAINT pk_unit_conversion PRIMARY KEY (id),
    CONSTRAINT fk_uc_from_unit FOREIGN KEY (from_unit_id) REFERENCES unit (id),
    CONSTRAINT fk_uc_to_unit FOREIGN KEY (to_unit_id) REFERENCES unit (id),
    CONSTRAINT uq_unit_conversion_pair UNIQUE (from_unit_id, to_unit_id),
    CONSTRAINT ck_uc_factor CHECK (factor > 0),
    CONSTRAINT ck_uc_pair CHECK (from_unit_id <> to_unit_id)
);

CREATE INDEX IF NOT EXISTS idx_unit_conversion_from ON unit_conversion (from_unit_id);

ALTER TABLE material
    ADD COLUMN IF NOT EXISTS pack_unit_id UUID REFERENCES unit (id) ON DELETE SET NULL;

ALTER TABLE material
    ADD COLUMN IF NOT EXISTS pack_to_base_factor numeric(18, 6);

-- Seed cặp phổ quát (id đơn vị lấy theo seed chuẩn; bỏ qua nếu đã tồn tại).
INSERT INTO unit_conversion (id, status, from_unit_id, to_unit_id, factor, created_at, updated_at)
SELECT gen_random_uuid(), 'ACTIVE',
    (SELECT id FROM unit WHERE code = 'KG' LIMIT 1),
    (SELECT id FROM unit WHERE code = 'G' LIMIT 1),
    1000, NOW(), NOW()
WHERE EXISTS (SELECT 1 FROM unit WHERE code = 'KG')
  AND EXISTS (SELECT 1 FROM unit WHERE code = 'G')
  AND NOT EXISTS (
    SELECT 1 FROM unit_conversion uc
    JOIN unit uf ON uf.id = uc.from_unit_id AND uf.code = 'KG'
    JOIN unit ut ON ut.id = uc.to_unit_id AND ut.code = 'G');

INSERT INTO unit_conversion (id, status, from_unit_id, to_unit_id, factor, created_at, updated_at)
SELECT gen_random_uuid(), 'ACTIVE',
    (SELECT id FROM unit WHERE code = 'L' LIMIT 1),
    (SELECT id FROM unit WHERE code = 'ML' LIMIT 1),
    1000, NOW(), NOW()
WHERE EXISTS (SELECT 1 FROM unit WHERE code = 'L')
  AND EXISTS (SELECT 1 FROM unit WHERE code = 'ML')
  AND NOT EXISTS (
    SELECT 1 FROM unit_conversion uc
    JOIN unit uf ON uf.id = uc.from_unit_id AND uf.code = 'L'
    JOIN unit ut ON ut.id = uc.to_unit_id AND ut.code = 'ML');

INSERT INTO unit_conversion (id, status, from_unit_id, to_unit_id, factor, created_at, updated_at)
SELECT gen_random_uuid(), 'ACTIVE',
    (SELECT id FROM unit WHERE code = 'LIT' LIMIT 1),
    (SELECT id FROM unit WHERE code = 'ML' LIMIT 1),
    1000, NOW(), NOW()
WHERE EXISTS (SELECT 1 FROM unit WHERE code = 'LIT')
  AND EXISTS (SELECT 1 FROM unit WHERE code = 'ML')
  AND NOT EXISTS (
    SELECT 1 FROM unit_conversion uc
    JOIN unit uf ON uf.id = uc.from_unit_id AND uf.code = 'LIT'
    JOIN unit ut ON ut.id = uc.to_unit_id AND ut.code = 'ML');
