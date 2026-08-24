--liquibase formatted sql

--changeset erp:add-kho-nguyen-lieu
-- ============================================================
-- MODULE: INV — Kho & Ton kho (phần nền: unit, category, material,
-- warehouse + chứng từ nhập/xuất/chuyển/kiểm kê).
-- Chạy SAU auth/branch (đã có) và TRƯỚC proc/menu/pos.
-- Lưu ý: stock_in_item có FK tới purchase_order_item (proc) nên FK này
-- được thêm ở changeset 006-proc.sql (ALTER TABLE).
-- ============================================================

-- ---- Foundation: unit, category (dùng chung PRODUCT & MATERIAL) ----

CREATE TABLE IF NOT EXISTS unit (
    id          uuid        NOT NULL,
    code        varchar(20) NOT NULL,
    name        varchar(50) NOT NULL,
    unit_type   varchar(30) NOT NULL DEFAULT 'COUNT',
    status      varchar(30) NOT NULL DEFAULT 'ACTIVE',
    created_at  timestamp   NOT NULL,
    updated_at  timestamp   NOT NULL,
    created_by  varchar(100),
    updated_by  varchar(100),
    CONSTRAINT pk_unit PRIMARY KEY (id),
    CONSTRAINT ck_unit_type CHECK (unit_type IN ('COUNT', 'WEIGHT', 'VOLUME'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_unit_code ON unit (code);

CREATE TABLE IF NOT EXISTS category (
    id            uuid        NOT NULL,
    category_type varchar(20) NOT NULL DEFAULT 'PRODUCT',
    code          varchar(50) NOT NULL,
    name          varchar(150) NOT NULL,
    description   varchar(255),
    image_url     varchar(500),
    display_order integer     NOT NULL DEFAULT 0,
    status        varchar(30) NOT NULL DEFAULT 'ACTIVE',
    created_at    timestamp   NOT NULL,
    updated_at    timestamp   NOT NULL,
    created_by    varchar(100),
    updated_by    varchar(100),
    CONSTRAINT pk_category PRIMARY KEY (id),
    CONSTRAINT ck_category_type CHECK (category_type IN ('PRODUCT', 'MATERIAL'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_category_type_code ON category (category_type, code);
CREATE INDEX IF NOT EXISTS idx_category_type_status_order ON category (category_type, status, display_order);

-- ---- material, warehouse ----

CREATE TABLE IF NOT EXISTS material (
    id               uuid          NOT NULL,
    category_id      uuid          NOT NULL,
    code             varchar(50)   NOT NULL,
    name             varchar(150)  NOT NULL,
    base_unit_id     uuid          NOT NULL,
    min_stock_alert  numeric(14,3) NOT NULL DEFAULT 0,
    shelf_life_days  integer,
    is_perishable    boolean       NOT NULL DEFAULT false,
    status           varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    created_at       timestamp     NOT NULL,
    updated_at       timestamp     NOT NULL,
    created_by       varchar(100),
    updated_by       varchar(100),
    CONSTRAINT pk_material PRIMARY KEY (id),
    CONSTRAINT fk_material_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_material_unit FOREIGN KEY (base_unit_id) REFERENCES unit (id),
    CONSTRAINT ck_material_min_stock CHECK (min_stock_alert >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_material_code ON material (code);
CREATE INDEX IF NOT EXISTS idx_material_category_status ON material (category_id, status);
CREATE INDEX IF NOT EXISTS idx_material_status ON material (status);

CREATE TABLE IF NOT EXISTS warehouse (
    id              uuid        NOT NULL,
    code            varchar(50) NOT NULL,
    name            varchar(150) NOT NULL,
    warehouse_type  varchar(20) NOT NULL DEFAULT 'BRANCH',
    branch_id       uuid,
    address         varchar(255),
    status          varchar(30) NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamp   NOT NULL,
    updated_at      timestamp   NOT NULL,
    created_by      varchar(100),
    updated_by      varchar(100),
    CONSTRAINT pk_warehouse PRIMARY KEY (id),
    CONSTRAINT fk_warehouse_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE CASCADE,
    CONSTRAINT ck_warehouse_type CHECK (warehouse_type IN ('CENTRAL', 'BRANCH')),
    CONSTRAINT ck_warehouse_branch CHECK (
        (warehouse_type = 'CENTRAL' AND branch_id IS NULL)
        OR (warehouse_type = 'BRANCH' AND branch_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_code ON warehouse (code);
CREATE INDEX IF NOT EXISTS idx_warehouse_branch_status ON warehouse (branch_id, status);

-- ---- tồn kho ----

CREATE TABLE IF NOT EXISTS material_stock_balance (
    id                 uuid          NOT NULL,
    warehouse_id       uuid          NOT NULL,
    material_id        uuid          NOT NULL,
    quantity_on_hand   numeric(14,3) NOT NULL DEFAULT 0,
    quantity_reserved  numeric(14,3) NOT NULL DEFAULT 0,
    created_at         timestamp     NOT NULL,
    created_by         varchar(100),
    updated_at         timestamp     NOT NULL,
    updated_by         varchar(100),
    CONSTRAINT pk_material_stock_balance PRIMARY KEY (id),
    CONSTRAINT fk_stock_balance_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_balance_material FOREIGN KEY (material_id) REFERENCES material (id) ON DELETE CASCADE,
    CONSTRAINT ck_msb_on_hand CHECK (quantity_on_hand >= 0),
    CONSTRAINT ck_msb_reserved CHECK (quantity_reserved >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_balance_warehouse_material ON material_stock_balance (warehouse_id, material_id);
CREATE INDEX IF NOT EXISTS idx_stock_balance_material ON material_stock_balance (material_id);

-- ---- stock_in (nhận hàng: PURCHASE / TRANSFER_IN / ADJUSTMENT / RETURN) ----

CREATE TABLE IF NOT EXISTS stock_in (
    id                  uuid      NOT NULL,
    code                varchar(50) NOT NULL,
    warehouse_id        uuid      NOT NULL,
    source_type         varchar(30) NOT NULL,
    source_reference_id uuid,
    in_date             date      NOT NULL,
    note                varchar(500),
    status              varchar(30) NOT NULL DEFAULT 'DRAFT',
    received_by         uuid,
    posted_at           timestamp,
    created_at          timestamp NOT NULL,
    created_by          varchar(100),
    updated_at          timestamp NOT NULL,
    updated_by          varchar(100),
    CONSTRAINT pk_stock_in PRIMARY KEY (id),
    CONSTRAINT fk_stock_in_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_stock_in_receiver FOREIGN KEY (received_by) REFERENCES account (id) ON DELETE SET NULL,
    CONSTRAINT ck_stock_in_source CHECK (source_type IN ('PURCHASE', 'TRANSFER_IN', 'ADJUSTMENT', 'RETURN')),
    CONSTRAINT ck_stock_in_status CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_in_code ON stock_in (code);
CREATE INDEX IF NOT EXISTS idx_stock_in_warehouse_date ON stock_in (warehouse_id, in_date);
CREATE INDEX IF NOT EXISTS idx_stock_in_source ON stock_in (source_type, source_reference_id);
CREATE INDEX IF NOT EXISTS idx_stock_in_status ON stock_in (status);

CREATE TABLE IF NOT EXISTS stock_in_item (
    id                       uuid          NOT NULL,
    status                   varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    stock_in_id              uuid          NOT NULL,
    purchase_order_item_id   uuid,
    material_id              uuid          NOT NULL,
    quantity                 numeric(14,3) NOT NULL,
    unit_price               numeric(12,2) NOT NULL DEFAULT 0,
    batch_no                 varchar(80),
    expiry_date              date,
    created_at               timestamp     NOT NULL,
    created_by               varchar(100),
    updated_at               timestamp     NOT NULL,
    updated_by               varchar(100),
    CONSTRAINT pk_stock_in_item PRIMARY KEY (id),
    CONSTRAINT fk_stock_in_item_header FOREIGN KEY (stock_in_id) REFERENCES stock_in (id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_in_item_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT ck_stock_in_item_qty CHECK (quantity > 0),
    CONSTRAINT ck_stock_in_item_price CHECK (unit_price >= 0)
);

CREATE INDEX IF NOT EXISTS idx_stock_in_item_header ON stock_in_item (stock_in_id);
CREATE INDEX IF NOT EXISTS idx_stock_in_item_material ON stock_in_item (material_id);
CREATE INDEX IF NOT EXISTS idx_stock_in_item_po_item ON stock_in_item (purchase_order_item_id);

-- ---- stock_out ----

CREATE TABLE IF NOT EXISTS stock_out (
    id                  uuid      NOT NULL,
    code                varchar(50) NOT NULL,
    warehouse_id        uuid      NOT NULL,
    destination_type    varchar(30) NOT NULL,
    destination_reference_id uuid,
    out_date            date      NOT NULL,
    note                varchar(500),
    status              varchar(30) NOT NULL DEFAULT 'DRAFT',
    issued_by           uuid,
    posted_at           timestamp,
    created_at          timestamp NOT NULL,
    created_by          varchar(100),
    updated_at          timestamp NOT NULL,
    updated_by          varchar(100),
    CONSTRAINT pk_stock_out PRIMARY KEY (id),
    CONSTRAINT fk_stock_out_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_stock_out_issuer FOREIGN KEY (issued_by) REFERENCES account (id) ON DELETE SET NULL,
    CONSTRAINT ck_stock_out_destination CHECK (destination_type IN ('PRODUCTION_ISSUE', 'TRANSFER_OUT', 'ADJUSTMENT', 'WASTAGE', 'BRANCH_ISSUE')),
    CONSTRAINT ck_stock_out_status CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_out_code ON stock_out (code);
CREATE INDEX IF NOT EXISTS idx_stock_out_warehouse_date ON stock_out (warehouse_id, out_date);
CREATE INDEX IF NOT EXISTS idx_stock_out_destination ON stock_out (destination_type, destination_reference_id);
CREATE INDEX IF NOT EXISTS idx_stock_out_status ON stock_out (status);

CREATE TABLE IF NOT EXISTS stock_out_item (
    id           uuid          NOT NULL,
    status       varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    stock_out_id uuid          NOT NULL,
    material_id  uuid          NOT NULL,
    quantity     numeric(14,3) NOT NULL,
    unit_price   numeric(12,2) NOT NULL DEFAULT 0,
    batch_no     varchar(80),
    created_at   timestamp     NOT NULL,
    created_by   varchar(100),
    updated_at   timestamp     NOT NULL,
    updated_by   varchar(100),
    CONSTRAINT pk_stock_out_item PRIMARY KEY (id),
    CONSTRAINT fk_stock_out_item_header FOREIGN KEY (stock_out_id) REFERENCES stock_out (id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_out_item_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT ck_stock_out_item_qty CHECK (quantity > 0),
    CONSTRAINT ck_stock_out_item_price CHECK (unit_price >= 0)
);

CREATE INDEX IF NOT EXISTS idx_stock_out_item_header ON stock_out_item (stock_out_id);
CREATE INDEX IF NOT EXISTS idx_stock_out_item_material ON stock_out_item (material_id);

-- ---- stock_transfer ----

CREATE TABLE IF NOT EXISTS stock_transfer (
    id               uuid      NOT NULL,
    code             varchar(50) NOT NULL,
    from_warehouse_id uuid     NOT NULL,
    to_warehouse_id  uuid      NOT NULL,
    transfer_date    date      NOT NULL,
    status           varchar(30) NOT NULL DEFAULT 'PENDING',
    note             varchar(500),
    received_by      uuid,
    received_at      timestamp,
    created_at       timestamp NOT NULL,
    created_by       varchar(100),
    updated_at       timestamp NOT NULL,
    updated_by       varchar(100),
    CONSTRAINT pk_stock_transfer PRIMARY KEY (id),
    CONSTRAINT fk_transfer_from_warehouse FOREIGN KEY (from_warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_transfer_to_warehouse FOREIGN KEY (to_warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_transfer_receiver FOREIGN KEY (received_by) REFERENCES account (id) ON DELETE SET NULL,
    CONSTRAINT ck_transfer_diff_wh CHECK (from_warehouse_id <> to_warehouse_id),
    CONSTRAINT ck_transfer_status CHECK (status IN ('PENDING', 'IN_TRANSIT', 'RECEIVED', 'CANCELLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_transfer_code ON stock_transfer (code);
CREATE INDEX IF NOT EXISTS idx_transfer_from_status ON stock_transfer (from_warehouse_id, status);
CREATE INDEX IF NOT EXISTS idx_transfer_to_status ON stock_transfer (to_warehouse_id, status);

CREATE TABLE IF NOT EXISTS stock_transfer_item (
    id                 uuid          NOT NULL,
    status             varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    stock_transfer_id  uuid          NOT NULL,
    material_id        uuid          NOT NULL,
    quantity           numeric(14,3) NOT NULL,
    received_quantity  numeric(14,3) NOT NULL DEFAULT 0,
    unit_price         numeric(12,2) NOT NULL DEFAULT 0,
    created_at         timestamp     NOT NULL,
    created_by         varchar(100),
    updated_at         timestamp     NOT NULL,
    updated_by         varchar(100),
    CONSTRAINT pk_stock_transfer_item PRIMARY KEY (id),
    CONSTRAINT fk_transfer_item_header FOREIGN KEY (stock_transfer_id) REFERENCES stock_transfer (id) ON DELETE CASCADE,
    CONSTRAINT fk_transfer_item_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT ck_transfer_item_qty CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_transfer_item_header ON stock_transfer_item (stock_transfer_id);
CREATE INDEX IF NOT EXISTS idx_transfer_item_material ON stock_transfer_item (material_id);

-- ---- stock_count (kiểm kê) ----

CREATE TABLE IF NOT EXISTS stock_count (
    id          uuid      NOT NULL,
    code        varchar(50) NOT NULL,
    warehouse_id uuid      NOT NULL,
    count_date  date      NOT NULL,
    status      varchar(30) NOT NULL DEFAULT 'DRAFT',
    note        varchar(500),
    created_at  timestamp NOT NULL,
    created_by  varchar(100),
    updated_at  timestamp NOT NULL,
    updated_by  varchar(100),
    CONSTRAINT pk_stock_count PRIMARY KEY (id),
    CONSTRAINT fk_stock_count_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT ck_stock_count_status CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED', 'ADJUSTED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_count_code ON stock_count (code);
CREATE INDEX IF NOT EXISTS idx_stock_count_warehouse_date ON stock_count (warehouse_id, count_date);
CREATE INDEX IF NOT EXISTS idx_stock_count_status ON stock_count (status);

CREATE TABLE IF NOT EXISTS stock_count_item (
    id                uuid          NOT NULL,
    status            varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    stock_count_id    uuid          NOT NULL,
    material_id       uuid          NOT NULL,
    system_quantity   numeric(14,3) NOT NULL,
    counted_quantity  numeric(14,3) NOT NULL,
    variance_quantity numeric(14,3) NOT NULL,
    note              varchar(255),
    created_at        timestamp     NOT NULL,
    created_by        varchar(100),
    updated_at        timestamp     NOT NULL,
    updated_by        varchar(100),
    CONSTRAINT pk_stock_count_item PRIMARY KEY (id),
    CONSTRAINT fk_count_item_header FOREIGN KEY (stock_count_id) REFERENCES stock_count (id) ON DELETE CASCADE,
    CONSTRAINT fk_count_item_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT uq_count_item_header_material UNIQUE (stock_count_id, material_id)
);

CREATE INDEX IF NOT EXISTS idx_count_item_material ON stock_count_item (material_id);
