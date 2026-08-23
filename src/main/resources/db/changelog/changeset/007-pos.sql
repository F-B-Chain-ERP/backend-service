--liquibase formatted sql

--changeset erp:add-ban-hang
-- ============================================================
-- MODULE: POS — Bán hàng
-- Phụ thuộc: branch, customer (auth), product/product_variant/topping (MENU 007).
-- voucher_usage (MENU) được tạo ở đây vì cần FK tới orders.
-- refund.transaction_id GIỮ NGUYÊN cột nhưng KHÔNG có FK, vì bảng
-- `transaction` chưa được định nghĩa trong schema hiện tại.
-- ============================================================

CREATE TABLE IF NOT EXISTS cart (
    id              uuid          NOT NULL,
    status          varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    branch_id       uuid          NOT NULL,
    customer_id     uuid,
    session_token   varchar(150),
    subtotal_amount numeric(12,2) NOT NULL DEFAULT 0,
    created_at      timestamp     NOT NULL,
    created_by      varchar(100),
    updated_at      timestamp     NOT NULL,
    updated_by      varchar(100),
    CONSTRAINT pk_cart PRIMARY KEY (id),
    CONSTRAINT fk_cart_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT fk_cart_customer FOREIGN KEY (customer_id) REFERENCES customer (id) ON DELETE SET NULL,
    CONSTRAINT ck_cart_subtotal CHECK (subtotal_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_cart_customer_status ON cart (customer_id, status);
CREATE INDEX IF NOT EXISTS idx_cart_session ON cart (session_token);

CREATE TABLE IF NOT EXISTS cart_item (
    id           uuid          NOT NULL,
    status       varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    cart_id      uuid          NOT NULL,
    product_id   uuid          NOT NULL,
    variant_id   uuid,
    quantity     integer       NOT NULL DEFAULT 1,
    sugar_level  varchar(30)   NOT NULL DEFAULT 'NORMAL',
    ice_level    varchar(30)   NOT NULL DEFAULT 'NORMAL',
    note         varchar(255),
    unit_price   numeric(12,2) NOT NULL,
    total_price  numeric(12,2) NOT NULL,
    created_at   timestamp     NOT NULL,
    created_by   varchar(100),
    updated_at   timestamp     NOT NULL,
    updated_by   varchar(100),
    CONSTRAINT pk_cart_item PRIMARY KEY (id),
    CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id) REFERENCES cart (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_item_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_cart_item_variant FOREIGN KEY (variant_id) REFERENCES product_variant (id),
    CONSTRAINT ck_cart_item_qty CHECK (quantity > 0),
    CONSTRAINT ck_cart_item_unit CHECK (unit_price >= 0),
    CONSTRAINT ck_cart_item_total CHECK (total_price >= 0)
);

CREATE INDEX IF NOT EXISTS idx_cart_item_cart ON cart_item (cart_id);

CREATE TABLE IF NOT EXISTS cart_item_topping (
    id           uuid          NOT NULL,
    status       varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    cart_item_id uuid          NOT NULL,
    topping_id   uuid          NOT NULL,
    quantity     integer       NOT NULL DEFAULT 1,
    unit_price   numeric(12,2) NOT NULL,
    total_price  numeric(12,2) NOT NULL,
    created_at   timestamp     NOT NULL,
    created_by   varchar(100),
    updated_at   timestamp     NOT NULL,
    updated_by   varchar(100),
    CONSTRAINT pk_cart_item_topping PRIMARY KEY (id),
    CONSTRAINT fk_cart_item_topping_item FOREIGN KEY (cart_item_id) REFERENCES cart_item (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_item_topping_topping FOREIGN KEY (topping_id) REFERENCES topping (id),
    CONSTRAINT ck_cart_item_topping_qty CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_cart_item_topping_item ON cart_item_topping (cart_item_id);

CREATE TABLE IF NOT EXISTS orders (
    id                  uuid          NOT NULL,
    order_code          varchar(50)   NOT NULL,
    branch_id           uuid          NOT NULL,
    customer_id         uuid,
    customer_name       varchar(150)  NOT NULL,
    customer_phone      varchar(20)   NOT NULL,
    customer_email      varchar(150),
    order_type          varchar(30)   NOT NULL DEFAULT 'PICKUP',
    status              varchar(30)   NOT NULL DEFAULT 'PENDING',
    payment_method      varchar(50),
    payment_status      varchar(30)   NOT NULL DEFAULT 'UNPAID',
    subtotal_amount     numeric(12,2) NOT NULL DEFAULT 0,
    discount_amount     numeric(12,2) NOT NULL DEFAULT 0,
    delivery_fee        numeric(12,2) NOT NULL DEFAULT 0,
    total_amount        numeric(12,2) NOT NULL DEFAULT 0,
    total_cogs_amount   numeric(12,2) NOT NULL DEFAULT 0,
    pickup_time         timestamp,
    delivery_address    varchar(255),
    note                varchar(500),
    confirmed_at        timestamp,
    prepared_at         timestamp,
    ready_at            timestamp,
    delivering_at       timestamp,
    delivered_at        timestamp,
    completed_at        timestamp,
    cancelled_at        timestamp,
    rejected_at         timestamp,
    cancel_reason       varchar(255),
    created_at          timestamp     NOT NULL,
    created_by          varchar(100),
    updated_at          timestamp     NOT NULL,
    updated_by          varchar(100),
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT fk_orders_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customer (id) ON DELETE SET NULL,
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'PREPARING', 'READY', 'DELIVERING', 'COMPLETED', 'CANCELLED', 'REJECTED')),
    CONSTRAINT ck_orders_payment_method CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'COD', 'VNPAY', 'MOMO', 'BANK_TRANSFER')),
    CONSTRAINT ck_orders_subtotal CHECK (subtotal_amount >= 0),
    CONSTRAINT ck_orders_discount CHECK (discount_amount >= 0),
    CONSTRAINT ck_orders_delivery_fee CHECK (delivery_fee >= 0),
    CONSTRAINT ck_orders_total CHECK (total_amount >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_code ON orders (order_code);
CREATE INDEX IF NOT EXISTS idx_orders_customer_created ON orders (customer_id, created_at);
CREATE INDEX IF NOT EXISTS idx_orders_branch_created ON orders (branch_id, created_at);
CREATE INDEX IF NOT EXISTS idx_orders_branch_status_created ON orders (branch_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_orders_branch_type_status_created ON orders (branch_id, order_type, status, created_at);
CREATE INDEX IF NOT EXISTS idx_orders_branch_payment_created ON orders (branch_id, payment_method, created_at);
CREATE INDEX IF NOT EXISTS idx_orders_branch_payment_status_created ON orders (branch_id, payment_status, created_at);

CREATE TABLE IF NOT EXISTS order_delivery (
    id             uuid        NOT NULL,
    order_id       uuid        NOT NULL,
    shipper_id     uuid,
    receiver_name  varchar(150) NOT NULL,
    receiver_phone varchar(20)  NOT NULL,
    delivery_address varchar(255) NOT NULL,
    delivery_note  varchar(500),
    delivery_fee   numeric(12,2) NOT NULL DEFAULT 0,
    status         varchar(30)  NOT NULL DEFAULT 'PENDING',
    assigned_at    timestamp,
    picked_up_at   timestamp,
    delivered_at   timestamp,
    failed_at      timestamp,
    fail_reason    varchar(255),
    created_at     timestamp    NOT NULL,
    created_by     varchar(100),
    updated_at     timestamp    NOT NULL,
    updated_by     varchar(100),
    CONSTRAINT pk_order_delivery PRIMARY KEY (id),
    CONSTRAINT fk_order_delivery_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_delivery_shipper FOREIGN KEY (shipper_id) REFERENCES account (id) ON DELETE SET NULL,
    CONSTRAINT uq_order_delivery_order UNIQUE (order_id),
    CONSTRAINT ck_order_delivery_fee CHECK (delivery_fee >= 0),
    CONSTRAINT ck_order_delivery_status CHECK (status IN ('PENDING', 'ASSIGNED', 'PICKED_UP', 'DELIVERING', 'DELIVERED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_order_delivery_shipper_status_created ON order_delivery (shipper_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_order_delivery_status_created ON order_delivery (status, created_at);

CREATE TABLE IF NOT EXISTS order_item (
    id                uuid          NOT NULL,
    status            varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    order_id          uuid          NOT NULL,
    product_id        uuid,
    variant_id        uuid,
    product_code      varchar(50)   NOT NULL,
    product_name      varchar(150)  NOT NULL,
    variant_name      varchar(100),
    quantity          integer       NOT NULL,
    sugar_level       varchar(30)   NOT NULL DEFAULT 'NORMAL',
    ice_level         varchar(30)   NOT NULL DEFAULT 'NORMAL',
    note              varchar(255),
    unit_price        numeric(12,2) NOT NULL,
    total_price       numeric(12,2) NOT NULL,
    unit_cogs_amount  numeric(12,2) NOT NULL DEFAULT 0,
    created_at        timestamp     NOT NULL,
    created_by        varchar(100),
    updated_at        timestamp     NOT NULL,
    updated_by        varchar(100),
    CONSTRAINT pk_order_item PRIMARY KEY (id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE SET NULL,
    CONSTRAINT fk_order_item_variant FOREIGN KEY (variant_id) REFERENCES product_variant (id) ON DELETE SET NULL,
    CONSTRAINT ck_order_item_qty CHECK (quantity > 0),
    CONSTRAINT ck_order_item_unit CHECK (unit_price >= 0),
    CONSTRAINT ck_order_item_total CHECK (total_price >= 0)
);

CREATE INDEX IF NOT EXISTS idx_order_item_order ON order_item (order_id);
CREATE INDEX IF NOT EXISTS idx_order_item_product ON order_item (product_id);

CREATE TABLE IF NOT EXISTS order_item_topping (
    id             uuid          NOT NULL,
    status         varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    order_item_id  uuid          NOT NULL,
    topping_id     uuid,
    topping_code   varchar(50)   NOT NULL,
    topping_name   varchar(150)  NOT NULL,
    quantity       integer       NOT NULL DEFAULT 1,
    unit_price     numeric(12,2) NOT NULL,
    total_price    numeric(12,2) NOT NULL,
    created_at     timestamp     NOT NULL,
    created_by     varchar(100),
    updated_at     timestamp     NOT NULL,
    updated_by     varchar(100),
    CONSTRAINT pk_order_item_topping PRIMARY KEY (id),
    CONSTRAINT fk_order_item_topping_item FOREIGN KEY (order_item_id) REFERENCES order_item (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_topping_topping FOREIGN KEY (topping_id) REFERENCES topping (id) ON DELETE SET NULL,
    CONSTRAINT ck_order_item_topping_qty CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_order_item_topping_item ON order_item_topping (order_item_id);

CREATE TABLE IF NOT EXISTS order_status_history (
    id           uuid        NOT NULL,
    status       varchar(30) NOT NULL DEFAULT 'ACTIVE',
    order_id     uuid        NOT NULL,
    old_status   varchar(30),
    new_status   varchar(30) NOT NULL,
    reason       varchar(255),
    changed_by   uuid,
    changed_at   timestamp   NOT NULL DEFAULT now(),
    created_at   timestamp   NOT NULL,
    created_by   varchar(100),
    updated_at   timestamp   NOT NULL,
    updated_by   varchar(100),
    CONSTRAINT pk_order_status_history PRIMARY KEY (id),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_status_history_actor FOREIGN KEY (changed_by) REFERENCES account (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_order_status_history_order_changed ON order_status_history (order_id, changed_at);
CREATE INDEX IF NOT EXISTS idx_order_status_history_status ON order_status_history (new_status);

CREATE TABLE IF NOT EXISTS payment_intent (
    id                uuid        NOT NULL,
    order_id          uuid        NOT NULL,
    provider          varchar(50) NOT NULL,
    amount            numeric(12,2) NOT NULL,
    currency          varchar(10) NOT NULL DEFAULT 'VND',
    status            varchar(30) NOT NULL DEFAULT 'PENDING',
    request_payload   jsonb,
    response_payload  jsonb,
    expires_at        timestamp,
    created_at        timestamp   NOT NULL,
    updated_at        timestamp   NOT NULL,
    created_by        varchar(100),
    updated_by        varchar(100),
    CONSTRAINT pk_payment_intent PRIMARY KEY (id),
    CONSTRAINT fk_payment_intent_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT uq_payment_intent_order_provider UNIQUE (order_id, provider),
    CONSTRAINT ck_payment_intent_amount CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_payment_intent_status_created ON payment_intent (status, created_at);

CREATE TABLE IF NOT EXISTS kds_ticket (
    id           uuid        NOT NULL,
    order_id     uuid        NOT NULL,
    branch_id    uuid        NOT NULL,
    station      varchar(30) NOT NULL DEFAULT 'BAR',
    queue_no     integer     NOT NULL,
    status       varchar(30) NOT NULL DEFAULT 'QUEUED',
    started_at   timestamp,
    ready_at     timestamp,
    served_at    timestamp,
    created_at   timestamp   NOT NULL,
    created_by   varchar(100),
    updated_at   timestamp   NOT NULL,
    updated_by   varchar(100),
    CONSTRAINT pk_kds_ticket PRIMARY KEY (id),
    CONSTRAINT fk_kds_ticket_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_kds_ticket_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE CASCADE,
    CONSTRAINT ck_kds_ticket_status CHECK (status IN ('QUEUED', 'PREPARING', 'READY', 'SERVED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_kds_ticket_branch_station_status ON kds_ticket (branch_id, station, status);
CREATE INDEX IF NOT EXISTS idx_kds_ticket_order ON kds_ticket (order_id);

CREATE TABLE IF NOT EXISTS kds_ticket_item (
    id                 uuid        NOT NULL,
    status             varchar(30) NOT NULL DEFAULT 'QUEUED',
    kds_ticket_id      uuid        NOT NULL,
    order_item_id      uuid        NOT NULL,
    prepared_quantity  integer     NOT NULL DEFAULT 0,
    created_at         timestamp   NOT NULL,
    created_by         varchar(100),
    updated_at         timestamp   NOT NULL,
    updated_by         varchar(100),
    CONSTRAINT pk_kds_ticket_item PRIMARY KEY (id),
    CONSTRAINT fk_kds_ticket_item_ticket FOREIGN KEY (kds_ticket_id) REFERENCES kds_ticket (id) ON DELETE CASCADE,
    CONSTRAINT fk_kds_ticket_item_order_item FOREIGN KEY (order_item_id) REFERENCES order_item (id) ON DELETE CASCADE,
    CONSTRAINT ck_kds_ticket_item_prepared CHECK (prepared_quantity >= 0),
    CONSTRAINT ck_kds_ticket_item_status CHECK (status IN ('QUEUED', 'PREPARING', 'READY', 'SERVED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_kds_ticket_item_ticket ON kds_ticket_item (kds_ticket_id);

-- refund: transaction_id GIỮ cột, KHÔNG có FK (bảng `transaction` chưa tồn tại)
CREATE TABLE IF NOT EXISTS refund (
    id               uuid          NOT NULL,
    order_id         uuid          NOT NULL,
    transaction_id   uuid,
    refund_code      varchar(100)  NOT NULL,
    amount           numeric(12,2) NOT NULL,
    reason           varchar(255),
    status           varchar(30)   NOT NULL DEFAULT 'PENDING',
    processed_at     timestamp,
    processed_by     uuid,
    created_at       timestamp     NOT NULL,
    created_by       varchar(100),
    updated_at       timestamp     NOT NULL,
    updated_by       varchar(100),
    CONSTRAINT pk_refund PRIMARY KEY (id),
    CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_refund_processor FOREIGN KEY (processed_by) REFERENCES account (id) ON DELETE SET NULL,
    CONSTRAINT uq_refund_code UNIQUE (refund_code),
    CONSTRAINT ck_refund_amount CHECK (amount >= 0),
    CONSTRAINT ck_refund_status CHECK (status IN ('PENDING', 'APPROVED', 'PROCESSED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_refund_order ON refund (order_id);
CREATE INDEX IF NOT EXISTS idx_refund_status_created ON refund (status, created_at);

-- voucher_usage (thuộc MENU) — tạo ở đây vì cần FK tới orders
CREATE TABLE IF NOT EXISTS voucher_usage (
    id             uuid          NOT NULL,
    status         varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    voucher_id     uuid          NOT NULL,
    order_id       uuid          NOT NULL,
    customer_id    uuid,
    discount_amount numeric(12,2) NOT NULL,
    used_at        timestamp     NOT NULL DEFAULT now(),
    created_by     varchar(100),
    updated_at     timestamp     NOT NULL,
    updated_by     varchar(100),
    CONSTRAINT pk_voucher_usage PRIMARY KEY (id),
    CONSTRAINT fk_usage_voucher FOREIGN KEY (voucher_id) REFERENCES voucher (id),
    CONSTRAINT fk_usage_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_usage_customer FOREIGN KEY (customer_id) REFERENCES customer (id) ON DELETE SET NULL,
    CONSTRAINT uq_usage_voucher_order UNIQUE (voucher_id, order_id),
    CONSTRAINT ck_usage_discount CHECK (discount_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_usage_customer_voucher ON voucher_usage (customer_id, voucher_id);
CREATE INDEX IF NOT EXISTS idx_usage_voucher_used ON voucher_usage (voucher_id, used_at);
