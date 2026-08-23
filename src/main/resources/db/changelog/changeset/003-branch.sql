--liquibase formatted sql

--changeset erp:add-branch-table
-- ============================================================
-- Tạo bảng branch (chi nhánh / đơn vị kinh doanh) và quan hệ
-- cha-con (parent_id tự tham chiếu). Bảng này trước đây chỉ tồn
-- tại ở tầng entity/repository nhưng chưa được tạo trong schema,
-- nên app chưa thể vận hành quản lý chi nhánh và chọn đơn vị.
-- ============================================================

CREATE TABLE IF NOT EXISTS branch (
    id                          uuid         NOT NULL,
    code                        varchar(50)  NOT NULL,
    name                        varchar(150) NOT NULL,
    address                     varchar(255),
    phone                       varchar(20),
    email                       varchar(150),
    latitude                    numeric(10, 7),
    longitude                   numeric(10, 7),
    timezone                    varchar(50)  NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    supports_pickup            boolean      NOT NULL DEFAULT true,
    supports_delivery           boolean      NOT NULL DEFAULT false,
    average_preparation_minutes integer      NOT NULL DEFAULT 15,
    status                      varchar(30)  NOT NULL DEFAULT 'ACTIVE',
    parent_id                   uuid         REFERENCES branch (id) ON DELETE SET NULL,
    created_at                  timestamp    NOT NULL,
    updated_at                  timestamp    NOT NULL,
    created_by                  varchar(100),
    updated_by                  varchar(100),
    CONSTRAINT pk_branch PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_branch_code    ON branch (code);
CREATE INDEX IF NOT EXISTS        idx_branch_status ON branch (status);
CREATE INDEX IF NOT EXISTS        idx_branch_parent ON branch (parent_id);

-- Seed trụ sở chính mặc định để hệ thống có dữ liệu chi nhánh ban đầu.
INSERT INTO branch (id, code, name, timezone, status, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'HQ', 'Trụ sở chính',
        'Asia/Ho_Chi_Minh', 'ACTIVE', now(), now())
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- Các bảng phụ thuộc branch: giờ hoạt động và khung giờ pickup.
-- ============================================================

CREATE TABLE IF NOT EXISTS branch_hours (
    id           uuid        NOT NULL,
    status       varchar(30) NOT NULL DEFAULT 'ACTIVE',
    branch_id    uuid        NOT NULL,
    day_of_week  smallint    NOT NULL,
    open_time    time        NOT NULL,
    close_time   time        NOT NULL,
    is_closed    boolean     NOT NULL DEFAULT false,
    created_at   timestamp   NOT NULL,
    updated_at   timestamp   NOT NULL,
    created_by   varchar(100),
    updated_by   varchar(100),
    CONSTRAINT pk_branch_hours PRIMARY KEY (id),
    CONSTRAINT fk_branch_hours_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE CASCADE,
    CONSTRAINT ck_branch_hours_day CHECK (day_of_week BETWEEN 1 AND 7)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_branch_hours_branch_day ON branch_hours (branch_id, day_of_week);
CREATE INDEX IF NOT EXISTS        idx_branch_hours_branch     ON branch_hours (branch_id);

CREATE TABLE IF NOT EXISTS pickup_time_slot (
    id           uuid        NOT NULL,
    branch_id    uuid        NOT NULL,
    slot_code    varchar(50) NOT NULL,
    start_time   time        NOT NULL,
    end_time     time        NOT NULL,
    max_orders   integer,
    status       varchar(30) NOT NULL DEFAULT 'ACTIVE',
    created_at   timestamp   NOT NULL,
    updated_at   timestamp   NOT NULL,
    created_by   varchar(100),
    updated_by   varchar(100),
    CONSTRAINT pk_pickup_time_slot PRIMARY KEY (id),
    CONSTRAINT fk_pickup_slot_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pickup_slot_branch_code ON pickup_time_slot (branch_id, slot_code);
CREATE INDEX IF NOT EXISTS        idx_pickup_slot_branch_status ON pickup_time_slot (branch_id, status);
CREATE INDEX IF NOT EXISTS        idx_pickup_slot_time         ON pickup_time_slot (branch_id, start_time, end_time);

