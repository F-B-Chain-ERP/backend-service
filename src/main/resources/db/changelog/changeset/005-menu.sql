--liquibase formatted sql

--changeset erp:add-san-pham-khuyen-mai
-- ============================================================
-- MODULE: MENU — Sản phẩm & Khuyến mãi
-- Phụ thuộc: category, material, unit (005), branch (auth).
-- Ghi chú: voucher_usage (FK tới orders của POS) được tạo ở 009-pos.sql.
-- ============================================================

CREATE TABLE IF NOT EXISTS product (
    id                       uuid          NOT NULL,
    category_id             uuid          NOT NULL,
    code                    varchar(50)   NOT NULL,
    name                    varchar(150)  NOT NULL,
    description             varchar(500),
    image_url               varchar(500),
    base_price              numeric(12,2) NOT NULL,
    preparation_minutes     integer       NOT NULL DEFAULT 10,
    is_featured             boolean       NOT NULL DEFAULT false,
    is_best_seller          boolean       NOT NULL DEFAULT false,
    is_combo                boolean       NOT NULL DEFAULT false,
    available_ice_levels    varchar(50)   NOT NULL DEFAULT '0,30,50,70,100',
    available_sugar_levels  varchar(50)   NOT NULL DEFAULT '0,30,50,70,100',
    status                  varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    created_at              timestamp     NOT NULL,
    updated_at              timestamp     NOT NULL,
    created_by              varchar(100),
    updated_by              varchar(100),
    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT ck_product_price CHECK (base_price >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_code ON product (code);
CREATE INDEX IF NOT EXISTS idx_product_category_status ON product (category_id, status);
CREATE INDEX IF NOT EXISTS idx_product_status ON product (status);
CREATE INDEX IF NOT EXISTS idx_product_featured ON product (is_featured, status);

CREATE TABLE IF NOT EXISTS product_variant (
    id            uuid          NOT NULL,
    product_id    uuid          NOT NULL,
    variant_code  varchar(50)   NOT NULL,
    variant_name  varchar(100)  NOT NULL,
    size_label    varchar(30)   NOT NULL,
    price_delta   numeric(12,2)  NOT NULL DEFAULT 0,
    display_order integer       NOT NULL DEFAULT 0,
    status        varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    created_at    timestamp     NOT NULL,
    updated_at    timestamp     NOT NULL,
    created_by    varchar(100),
    updated_by    varchar(100),
    CONSTRAINT pk_product_variant PRIMARY KEY (id),
    CONSTRAINT fk_variant_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT uq_variant_product_code UNIQUE (product_id, variant_code)
);

CREATE INDEX IF NOT EXISTS idx_variant_product_status_order ON product_variant (product_id, status, display_order);
CREATE INDEX IF NOT EXISTS idx_variant_status ON product_variant (status);
CREATE INDEX IF NOT EXISTS idx_variant_display_order ON product_variant (display_order);

-- BOM: định mức nguyên liệu pha chế 1 variant (cầu nối MENU <-> INV)
CREATE TABLE IF NOT EXISTS product_recipe_item (
    id               uuid          NOT NULL,
    status           varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    variant_id       uuid          NOT NULL,
    material_id      uuid          NOT NULL,
    quantity         numeric(12,3) NOT NULL,
    unit_id          uuid          NOT NULL,
    wastage_percent  numeric(5,2)  NOT NULL DEFAULT 0,
    created_at       timestamp     NOT NULL,
    updated_at       timestamp     NOT NULL,
    created_by       varchar(100),
    updated_by       varchar(100),
    CONSTRAINT pk_product_recipe_item PRIMARY KEY (id),
    CONSTRAINT fk_recipe_variant FOREIGN KEY (variant_id) REFERENCES product_variant (id) ON DELETE CASCADE,
    CONSTRAINT fk_recipe_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_recipe_unit FOREIGN KEY (unit_id) REFERENCES unit (id),
    CONSTRAINT uq_recipe_variant_material UNIQUE (variant_id, material_id),
    CONSTRAINT ck_recipe_qty CHECK (quantity > 0),
    CONSTRAINT ck_recipe_wastage CHECK (wastage_percent >= 0 AND wastage_percent <= 100)
);

CREATE INDEX IF NOT EXISTS idx_recipe_variant ON product_recipe_item (variant_id);
CREATE INDEX IF NOT EXISTS idx_recipe_material ON product_recipe_item (material_id);

CREATE TABLE IF NOT EXISTS topping (
    id                 uuid          NOT NULL,
    code               varchar(50)   NOT NULL,
    name               varchar(150)  NOT NULL,
    price              numeric(12,2) NOT NULL,
    image_url          varchar(500),
    group_name         varchar(100),
    material_id        uuid,
    material_quantity  numeric(12,3),
    status             varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    created_at         timestamp     NOT NULL,
    updated_at         timestamp     NOT NULL,
    created_by         varchar(100),
    updated_by         varchar(100),
    CONSTRAINT pk_topping PRIMARY KEY (id),
    CONSTRAINT fk_topping_material FOREIGN KEY (material_id) REFERENCES material (id) ON DELETE SET NULL,
    CONSTRAINT ck_topping_price CHECK (price >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_topping_code ON topping (code);
CREATE INDEX IF NOT EXISTS idx_topping_status ON topping (status);

CREATE TABLE IF NOT EXISTS product_topping (
    id            uuid          NOT NULL,
    product_id    uuid          NOT NULL,
    topping_id    uuid          NOT NULL,
    is_default    boolean       NOT NULL DEFAULT false,
    max_quantity  integer       NOT NULL DEFAULT 3,
    status        varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    created_at    timestamp     NOT NULL,
    updated_at    timestamp     NOT NULL,
    created_by    varchar(100),
    updated_by    varchar(100),
    CONSTRAINT pk_product_topping PRIMARY KEY (id),
    CONSTRAINT fk_product_topping_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_topping_topping FOREIGN KEY (topping_id) REFERENCES topping (id),
    CONSTRAINT uq_product_topping UNIQUE (product_id, topping_id),
    CONSTRAINT ck_product_topping_max CHECK (max_quantity >= 1)
);

CREATE INDEX IF NOT EXISTS idx_product_topping_product_status ON product_topping (product_id, status);
CREATE INDEX IF NOT EXISTS idx_product_topping_topping ON product_topping (topping_id);

CREATE TABLE IF NOT EXISTS combo_item (
    id                  uuid      NOT NULL,
    status              varchar(30) NOT NULL DEFAULT 'ACTIVE',
    combo_product_id    uuid      NOT NULL,
    variant_id          uuid      NOT NULL,
    quantity            integer   NOT NULL DEFAULT 1,
    is_substitutable    boolean   NOT NULL DEFAULT false,
    created_at          timestamp NOT NULL,
    updated_at          timestamp NOT NULL,
    created_by          varchar(100),
    updated_by          varchar(100),
    CONSTRAINT pk_combo_item PRIMARY KEY (id),
    CONSTRAINT fk_combo_item_combo FOREIGN KEY (combo_product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT fk_combo_item_variant FOREIGN KEY (variant_id) REFERENCES product_variant (id),
    CONSTRAINT ck_combo_item_qty CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_combo_item_combo ON combo_item (combo_product_id);
CREATE INDEX IF NOT EXISTS idx_combo_item_variant ON combo_item (variant_id);

CREATE TABLE IF NOT EXISTS branch_product_availability (
    id            uuid          NOT NULL,
    status        varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    branch_id     uuid          NOT NULL,
    product_id    uuid          NOT NULL,
    is_available  boolean       NOT NULL DEFAULT true,
    sale_price    numeric(12,2),
    created_at    timestamp     NOT NULL,
    updated_at    timestamp     NOT NULL,
    created_by    varchar(100),
    updated_by    varchar(100),
    CONSTRAINT pk_branch_product_availability PRIMARY KEY (id),
    CONSTRAINT fk_branch_product_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE CASCADE,
    CONSTRAINT fk_branch_product_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT uq_branch_product UNIQUE (branch_id, product_id),
    CONSTRAINT ck_branch_product_sale CHECK (sale_price IS NULL OR sale_price >= 0)
);

CREATE INDEX IF NOT EXISTS idx_branch_product_branch_status ON branch_product_availability (branch_id, status);
CREATE INDEX IF NOT EXISTS idx_branch_product_product ON branch_product_availability (product_id);

CREATE TABLE IF NOT EXISTS branch_topping_availability (
    id           uuid        NOT NULL,
    status       varchar(30) NOT NULL DEFAULT 'ACTIVE',
    branch_id    uuid        NOT NULL,
    topping_id   uuid        NOT NULL,
    is_available boolean     NOT NULL DEFAULT true,
    created_at   timestamp   NOT NULL,
    updated_at   timestamp   NOT NULL,
    created_by   varchar(100),
    updated_by   varchar(100),
    CONSTRAINT pk_branch_topping_availability PRIMARY KEY (id),
    CONSTRAINT fk_branch_topping_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE CASCADE,
    CONSTRAINT fk_branch_topping_topping FOREIGN KEY (topping_id) REFERENCES topping (id) ON DELETE CASCADE,
    CONSTRAINT uq_branch_topping UNIQUE (branch_id, topping_id)
);

CREATE INDEX IF NOT EXISTS idx_branch_topping_branch_status ON branch_topping_availability (branch_id, status);

CREATE TABLE IF NOT EXISTS voucher (
    id                      uuid          NOT NULL,
    code                    varchar(80)   NOT NULL,
    name                    varchar(150)  NOT NULL,
    description             varchar(500),
    discount_type           varchar(30)   NOT NULL,
    discount_value          numeric(12,2) NOT NULL,
    max_discount_amount     numeric(12,2),
    min_order_amount        numeric(12,2) NOT NULL DEFAULT 0,
    usage_limit             integer,
    used_count              integer       NOT NULL DEFAULT 0,
    usage_limit_per_customer integer,
    start_at                timestamp     NOT NULL,
    end_at                  timestamp     NOT NULL,
    status                  varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    created_at              timestamp     NOT NULL,
    updated_at              timestamp     NOT NULL,
    created_by              varchar(100),
    updated_by              varchar(100),
    CONSTRAINT pk_voucher PRIMARY KEY (id),
    CONSTRAINT ck_voucher_discount CHECK (discount_value >= 0),
    CONSTRAINT ck_voucher_max CHECK (max_discount_amount IS NULL OR max_discount_amount >= 0),
    CONSTRAINT ck_voucher_min CHECK (min_order_amount >= 0),
    CONSTRAINT ck_voucher_used CHECK (used_count >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_voucher_code ON voucher (code);
CREATE INDEX IF NOT EXISTS idx_voucher_status_time ON voucher (status, start_at, end_at);

CREATE TABLE IF NOT EXISTS voucher_branch (
    id          uuid        NOT NULL,
    status      varchar(30) NOT NULL DEFAULT 'ACTIVE',
    voucher_id  uuid        NOT NULL,
    branch_id   uuid        NOT NULL,
    created_at  timestamp   NOT NULL,
    created_by  varchar(100),
    updated_at  timestamp   NOT NULL,
    updated_by  varchar(100),
    CONSTRAINT pk_voucher_branch PRIMARY KEY (id),
    CONSTRAINT fk_voucher_branch_voucher FOREIGN KEY (voucher_id) REFERENCES voucher (id) ON DELETE CASCADE,
    CONSTRAINT fk_voucher_branch_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE CASCADE,
    CONSTRAINT uq_voucher_branch UNIQUE (voucher_id, branch_id)
);
