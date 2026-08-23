--liquibase formatted sql

--changeset erp:add-mua-hang-nha-cung-cap
-- ============================================================
-- MODULE: PROC — Mua hàng & Nhà cung cấp
-- Phụ thuộc: material, unit, warehouse (INV, 005), account, branch (auth).
-- Sau khi tạo purchase_order_item, bổ sung FK từ stock_in_item (INV)
-- trỏ ngược về PO để đối chiếu số lượng nhận hàng.
-- ============================================================

CREATE TABLE IF NOT EXISTS supplier (
    id                uuid          NOT NULL,
    code              varchar(50)   NOT NULL,
    name              varchar(200)  NOT NULL,
    tax_code          varchar(30),
    contact_name      varchar(150),
    phone             varchar(20),
    email             varchar(150),
    address           varchar(255),
    payment_term_days integer       NOT NULL DEFAULT 0,
    status            varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    created_at        timestamp     NOT NULL,
    updated_at        timestamp     NOT NULL,
    created_by        varchar(100),
    updated_by        varchar(100),
    CONSTRAINT pk_supplier PRIMARY KEY (id),
    CONSTRAINT ck_supplier_term CHECK (payment_term_days >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_supplier_code ON supplier (code);
CREATE INDEX IF NOT EXISTS idx_supplier_status ON supplier (status);

CREATE TABLE IF NOT EXISTS supplier_material (
    id               uuid          NOT NULL,
    status           varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    supplier_id      uuid          NOT NULL,
    material_id      uuid          NOT NULL,
    supplier_sku     varchar(100),
    purchase_price   numeric(12,2) NOT NULL,
    lead_time_days   integer       NOT NULL DEFAULT 1,
    is_preferred     boolean       NOT NULL DEFAULT false,
    created_at       timestamp     NOT NULL,
    updated_at       timestamp     NOT NULL,
    updated_by       varchar(100),
    created_by       varchar(100),
    CONSTRAINT pk_supplier_material PRIMARY KEY (id),
    CONSTRAINT fk_supplier_material_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id) ON DELETE CASCADE,
    CONSTRAINT fk_supplier_material_material FOREIGN KEY (material_id) REFERENCES material (id) ON DELETE CASCADE,
    CONSTRAINT uq_supplier_material UNIQUE (supplier_id, material_id),
    CONSTRAINT ck_supplier_material_price CHECK (purchase_price >= 0),
    CONSTRAINT ck_supplier_material_lead CHECK (lead_time_days >= 0)
);

CREATE INDEX IF NOT EXISTS idx_supplier_material_material ON supplier_material (material_id);

CREATE TABLE IF NOT EXISTS purchase_order (
    id                  uuid          NOT NULL,
    po_code             varchar(50)   NOT NULL,
    supplier_id         uuid          NOT NULL,
    warehouse_id        uuid          NOT NULL,
    status              varchar(30)   NOT NULL DEFAULT 'DRAFT',
    order_date          date          NOT NULL,
    expected_date       date,
    subtotal_amount     numeric(14,2) NOT NULL DEFAULT 0,
    total_amount        numeric(14,2) NOT NULL DEFAULT 0,
    note                varchar(500),
    submitted_at        timestamp,
    approved_by         uuid,
    approved_at         timestamp,
    cancelled_at        timestamp,
    cancel_reason       varchar(255),
    created_at          timestamp     NOT NULL,
    created_by          varchar(100),
    updated_at          timestamp     NOT NULL,
    updated_by          varchar(100),
    CONSTRAINT pk_purchase_order PRIMARY KEY (id),
    CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT fk_po_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_po_approver FOREIGN KEY (approved_by) REFERENCES account (id) ON DELETE SET NULL,
    CONSTRAINT ck_po_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED')),
    CONSTRAINT ck_po_subtotal CHECK (subtotal_amount >= 0),
    CONSTRAINT ck_po_total CHECK (total_amount >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_purchase_order_code ON purchase_order (po_code);
CREATE INDEX IF NOT EXISTS idx_po_supplier_status ON purchase_order (supplier_id, status);
CREATE INDEX IF NOT EXISTS idx_po_warehouse_status ON purchase_order (warehouse_id, status);
CREATE INDEX IF NOT EXISTS idx_po_status_order_date ON purchase_order (status, order_date);

CREATE TABLE IF NOT EXISTS purchase_order_item (
    id                uuid          NOT NULL,
    status            varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    purchase_order_id uuid          NOT NULL,
    material_id       uuid          NOT NULL,
    quantity          numeric(14,3) NOT NULL,
    unit_id           uuid          NOT NULL,
    unit_price        numeric(12,2) NOT NULL,
    total_price       numeric(14,2) NOT NULL,
    received_quantity numeric(14,3) NOT NULL DEFAULT 0,
    created_at        timestamp     NOT NULL,
    created_by        varchar(100),
    updated_at        timestamp     NOT NULL,
    updated_by        varchar(100),
    CONSTRAINT pk_purchase_order_item PRIMARY KEY (id),
    CONSTRAINT fk_po_item_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_po_item_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_po_item_unit FOREIGN KEY (unit_id) REFERENCES unit (id),
    CONSTRAINT ck_po_item_qty CHECK (quantity > 0),
    CONSTRAINT ck_po_item_price CHECK (unit_price >= 0),
    CONSTRAINT ck_po_item_received CHECK (received_quantity >= 0)
);

CREATE INDEX IF NOT EXISTS idx_po_item_po ON purchase_order_item (purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_po_item_material ON purchase_order_item (material_id);

-- Liên kết ngược: stock_in_item (INV) <-> purchase_order_item (PROC)
ALTER TABLE stock_in_item
    ADD CONSTRAINT fk_stock_in_item_po_item
    FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_item (id) ON DELETE SET NULL;
