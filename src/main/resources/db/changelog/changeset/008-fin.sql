--liquibase formatted sql

--changeset erp:add-tai-chinh-bao-cao
-- ============================================================
-- MODULE: FIN — Tài chính & Báo cáo
-- Phụ thuộc: supplier/purchase_order (PROC 006), branch, orders (POS 009).
-- ============================================================

CREATE TABLE IF NOT EXISTS accounts_payable (
    id                uuid          NOT NULL,
    supplier_id       uuid          NOT NULL,
    purchase_order_id uuid,
    invoice_no        varchar(100),
    invoice_amount    numeric(14,2) NOT NULL,
    paid_amount       numeric(14,2) NOT NULL DEFAULT 0,
    due_date          date          NOT NULL,
    status            varchar(30)   NOT NULL DEFAULT 'UNPAID',
    note              varchar(500),
    created_at        timestamp     NOT NULL,
    created_by        varchar(100),
    updated_at        timestamp     NOT NULL,
    updated_by        varchar(100),
    CONSTRAINT pk_accounts_payable PRIMARY KEY (id),
    CONSTRAINT fk_payable_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT fk_payable_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_order (id) ON DELETE SET NULL,
    CONSTRAINT ck_payable_invoice CHECK (invoice_amount >= 0),
    CONSTRAINT ck_payable_paid CHECK (paid_amount >= 0),
    CONSTRAINT ck_payable_status CHECK (status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'OVERDUE'))
);

CREATE INDEX IF NOT EXISTS idx_payable_supplier_status ON accounts_payable (supplier_id, status);
CREATE INDEX IF NOT EXISTS idx_payable_due_date ON accounts_payable (due_date, status);

CREATE TABLE IF NOT EXISTS accounts_payable_payment (
    id                   uuid          NOT NULL,
    status               varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    accounts_payable_id  uuid          NOT NULL,
    payment_date         date          NOT NULL,
    amount               numeric(14,2) NOT NULL,
    payment_method       varchar(50)   NOT NULL,
    reference_no         varchar(100),
    created_at           timestamp     NOT NULL,
    created_by           varchar(100),
    updated_at           timestamp     NOT NULL,
    updated_by           varchar(100),
    CONSTRAINT pk_accounts_payable_payment PRIMARY KEY (id),
    CONSTRAINT fk_payable_payment_payable FOREIGN KEY (accounts_payable_id) REFERENCES accounts_payable (id) ON DELETE CASCADE,
    CONSTRAINT ck_payable_payment_amount CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_payable_payment_payable ON accounts_payable_payment (accounts_payable_id);

CREATE TABLE IF NOT EXISTS expense (
    id               uuid          NOT NULL,
    branch_id        uuid,
    expense_date     date          NOT NULL,
    expense_category varchar(50)   NOT NULL,
    amount           numeric(14,2) NOT NULL,
    description      varchar(500),
    status           varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    created_at       timestamp     NOT NULL,
    created_by       varchar(100),
    updated_at       timestamp     NOT NULL,
    updated_by       varchar(100),
    CONSTRAINT pk_expense PRIMARY KEY (id),
    CONSTRAINT fk_expense_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE SET NULL,
    CONSTRAINT ck_expense_amount CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_expense_branch_date ON expense (branch_id, expense_date);
CREATE INDEX IF NOT EXISTS idx_expense_category ON expense (expense_category);

CREATE TABLE IF NOT EXISTS branch_daily_financial_summary (
    id                 uuid          NOT NULL,
    branch_id          uuid          NOT NULL,
    business_date      date          NOT NULL,
    gross_revenue      numeric(14,2) NOT NULL DEFAULT 0,
    discount_amount    numeric(14,2) NOT NULL DEFAULT 0,
    net_revenue        numeric(14,2) NOT NULL DEFAULT 0,
    total_cogs         numeric(14,2) NOT NULL DEFAULT 0,
    gross_profit       numeric(14,2) NOT NULL DEFAULT 0,
    total_expense      numeric(14,2) NOT NULL DEFAULT 0,
    net_profit         numeric(14,2) NOT NULL DEFAULT 0,
    order_count        integer       NOT NULL DEFAULT 0,
    status             varchar(30)   NOT NULL DEFAULT 'DRAFT',
    created_at         timestamp     NOT NULL,
    created_by         varchar(100),
    updated_at         timestamp     NOT NULL,
    updated_by         varchar(100),
    CONSTRAINT pk_branch_daily_financial_summary PRIMARY KEY (id),
    CONSTRAINT fk_financial_summary_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE CASCADE,
    CONSTRAINT uq_branch_daily_financial UNIQUE (branch_id, business_date),
    CONSTRAINT ck_financial_summary_status CHECK (status IN ('DRAFT', 'FINALIZED'))
);

CREATE INDEX IF NOT EXISTS idx_financial_summary_date ON branch_daily_financial_summary (business_date);
