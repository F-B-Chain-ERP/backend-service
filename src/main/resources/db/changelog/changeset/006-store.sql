--liquibase formatted sql

--changeset erp:add-van-hanh-cua-hang
-- ============================================================
-- MODULE: STORE — Vận hành cửa hàng
-- Phụ thuộc: branch (auth), product_variant (MENU 007), account (auth).
-- Bao gồm cả branch_variant_daily_stock và branch_variant_stock_log
-- (tồn kho sản phẩm bán ra theo chi nhánh).
-- ============================================================

CREATE TABLE IF NOT EXISTS shift (
    id           uuid        NOT NULL,
    branch_id    uuid        NOT NULL,
    shift_code   varchar(50) NOT NULL,
    shift_name   varchar(100) NOT NULL,
    start_time   time        NOT NULL,
    end_time     time        NOT NULL,
    status       varchar(30) NOT NULL DEFAULT 'ACTIVE',
    created_at   timestamp   NOT NULL,
    updated_at   timestamp   NOT NULL,
    created_by   varchar(100),
    updated_by   varchar(100),
    CONSTRAINT pk_shift PRIMARY KEY (id),
    CONSTRAINT fk_shift_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE CASCADE,
    CONSTRAINT uq_shift_branch_code UNIQUE (branch_id, shift_code)
);

CREATE INDEX IF NOT EXISTS idx_shift_branch_status ON shift (branch_id, status);

CREATE TABLE IF NOT EXISTS shift_assignment (
    id           uuid        NOT NULL,
    shift_id     uuid        NOT NULL,
    branch_id    uuid        NOT NULL,
    account_id   uuid        NOT NULL,
    work_date    date        NOT NULL,
    status       varchar(30) NOT NULL DEFAULT 'SCHEDULED',
    check_in_at  timestamp,
    check_out_at timestamp,
    note         varchar(255),
    created_at   timestamp   NOT NULL,
    created_by   varchar(100),
    updated_at   timestamp   NOT NULL,
    updated_by   varchar(100),
    CONSTRAINT pk_shift_assignment PRIMARY KEY (id),
    CONSTRAINT fk_shift_assignment_shift FOREIGN KEY (shift_id) REFERENCES shift (id) ON DELETE CASCADE,
    CONSTRAINT fk_shift_assignment_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE CASCADE,
    CONSTRAINT fk_shift_assignment_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE,
    CONSTRAINT uq_shift_assignment UNIQUE (shift_id, account_id, work_date),
    CONSTRAINT ck_shift_assignment_status CHECK (status IN ('SCHEDULED', 'CHECKED_IN', 'CHECKED_OUT', 'ABSENT', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_shift_assignment_branch_date ON shift_assignment (branch_id, work_date);
CREATE INDEX IF NOT EXISTS idx_shift_assignment_account_date ON shift_assignment (account_id, work_date);

CREATE TABLE IF NOT EXISTS store_daily_report (
    id                uuid          NOT NULL,
    branch_id         uuid          NOT NULL,
    business_date     date          NOT NULL,
    opening_cash      numeric(14,2) NOT NULL DEFAULT 0,
    closing_cash      numeric(14,2) NOT NULL DEFAULT 0,
    total_orders      integer       NOT NULL DEFAULT 0,
    gross_revenue     numeric(14,2) NOT NULL DEFAULT 0,
    discount_amount   numeric(14,2) NOT NULL DEFAULT 0,
    net_revenue       numeric(14,2) NOT NULL DEFAULT 0,
    cash_amount       numeric(14,2) NOT NULL DEFAULT 0,
    transfer_amount   numeric(14,2) NOT NULL DEFAULT 0,
    status            varchar(30)   NOT NULL DEFAULT 'OPEN',
    submitted_by      uuid,
    submitted_at      timestamp,
    created_at        timestamp     NOT NULL,
    created_by        varchar(100),
    updated_at        timestamp     NOT NULL,
    updated_by        varchar(100),
    CONSTRAINT pk_store_daily_report PRIMARY KEY (id),
    CONSTRAINT fk_store_report_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE CASCADE,
    CONSTRAINT fk_store_report_submitter FOREIGN KEY (submitted_by) REFERENCES account (id) ON DELETE SET NULL,
    CONSTRAINT uq_store_daily_report UNIQUE (branch_id, business_date),
    CONSTRAINT ck_store_report_status CHECK (status IN ('OPEN', 'SUBMITTED', 'RECONCILED'))
);

CREATE INDEX IF NOT EXISTS idx_store_report_branch_date ON store_daily_report (branch_id, business_date);

CREATE TABLE IF NOT EXISTS branch_variant_daily_stock (
    id                 uuid        NOT NULL,
    status             varchar(30) NOT NULL DEFAULT 'ACTIVE',
    branch_id          uuid        NOT NULL,
    variant_id         uuid        NOT NULL,
    business_date      date        NOT NULL,
    opening_quantity   integer     NOT NULL DEFAULT 0,
    sold_quantity      integer     NOT NULL DEFAULT 0,
    remaining_quantity integer     NOT NULL DEFAULT 0,
    created_at         timestamp   NOT NULL,
    created_by         varchar(100),
    updated_at         timestamp   NOT NULL,
    updated_by         varchar(100),
    CONSTRAINT pk_branch_variant_daily_stock PRIMARY KEY (id),
    CONSTRAINT fk_bvds_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE CASCADE,
    CONSTRAINT fk_bvds_variant FOREIGN KEY (variant_id) REFERENCES product_variant (id) ON DELETE CASCADE,
    CONSTRAINT uq_branch_variant_daily UNIQUE (branch_id, variant_id, business_date),
    CONSTRAINT ck_bvds_opening CHECK (opening_quantity >= 0),
    CONSTRAINT ck_bvds_sold CHECK (sold_quantity >= 0),
    CONSTRAINT ck_bvds_remaining CHECK (remaining_quantity >= 0)
);

CREATE INDEX IF NOT EXISTS idx_bvds_branch_date ON branch_variant_daily_stock (branch_id, business_date);

CREATE TABLE IF NOT EXISTS branch_variant_stock_log (
    id             uuid        NOT NULL,
    status         varchar(30) NOT NULL DEFAULT 'ACTIVE',
    branch_id      uuid        NOT NULL,
    variant_id     uuid        NOT NULL,
    change_type    varchar(30) NOT NULL,
    quantity_change integer    NOT NULL,
    reference_id   uuid,
    note           varchar(255),
    created_at     timestamp   NOT NULL,
    created_by     varchar(100),
    updated_at     timestamp   NOT NULL,
    updated_by     varchar(100),
    CONSTRAINT pk_branch_variant_stock_log PRIMARY KEY (id),
    CONSTRAINT fk_bvsl_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE CASCADE,
    CONSTRAINT fk_bvsl_variant FOREIGN KEY (variant_id) REFERENCES product_variant (id) ON DELETE CASCADE,
    CONSTRAINT ck_bvsl_change_type CHECK (change_type IN ('RESTOCK', 'SALE', 'ADJUSTMENT', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_bvsl_branch_variant_created ON branch_variant_stock_log (branch_id, variant_id, created_at);
