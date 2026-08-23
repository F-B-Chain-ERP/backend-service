--liquibase formatted sql

--changeset erp:baseline-schema
-- ============================================================
-- Baseline schema (consolidated từ changeset 001-005).
-- Sử dụng IF NOT EXISTS để có thể chạy an toàn trên DB đã tồn tại
-- (sau khi đã xoá lịch sử migration) hoặc trên DB mới hoàn toàn.
--   account, role, permission, role_permission, account_role_assignment,
--   audit_log, scope, customer, customer_address, refresh_token
-- ============================================================

CREATE TABLE IF NOT EXISTS account (
    id                    uuid         NOT NULL,
    username              varchar(100) NOT NULL,
    password              varchar(255),
    full_name             varchar(150) NOT NULL,
    email                 varchar(150),
    phone                 varchar(20),
    avatar_url            varchar(500),
    status                varchar(30)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at         timestamp,
    auth_provider         varchar(50)  NOT NULL DEFAULT 'LOCAL',
    provider_id           varchar(255),
    has_local_password    boolean      NOT NULL DEFAULT true,
    primary_branch_id     uuid,
    failed_login_attempts integer      NOT NULL DEFAULT 0,
    locked_until          timestamp,
    system_protected      boolean      NOT NULL DEFAULT false,
    created_at            timestamp    NOT NULL,
    updated_at            timestamp    NOT NULL,
    created_by            varchar(100),
    updated_by            varchar(100),
    CONSTRAINT pk_account PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_account_username ON account (username);
CREATE UNIQUE INDEX IF NOT EXISTS uq_account_email    ON account (email);
CREATE INDEX IF NOT EXISTS idx_account_phone ON account (phone);
CREATE INDEX IF NOT EXISTS idx_account_primary_branch ON account (primary_branch_id);

CREATE TABLE IF NOT EXISTS role (
    id          uuid         NOT NULL,
    code        varchar(80)  NOT NULL,
    name        varchar(150) NOT NULL,
    description varchar(255),
    type        varchar(30)  NOT NULL DEFAULT 'SYSTEM',
    status      varchar(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at  timestamp    NOT NULL,
    updated_at  timestamp    NOT NULL,
    created_by  varchar(100),
    updated_by  varchar(100),
    CONSTRAINT pk_role PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_role_code ON role (code);

CREATE TABLE IF NOT EXISTS permission (
    id          uuid         NOT NULL,
    code        varchar(120) NOT NULL,
    name        varchar(150) NOT NULL,
    module      varchar(80)  NOT NULL,
    description varchar(255),
    status      varchar(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at  timestamp    NOT NULL,
    updated_at  timestamp    NOT NULL,
    created_by  varchar(100),
    updated_by  varchar(100),
    CONSTRAINT pk_permission PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_permission_code ON permission (code);

CREATE TABLE IF NOT EXISTS role_permission (
    role_id       uuid NOT NULL,
    permission_id uuid NOT NULL,
    CONSTRAINT pk_role_permission PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role       FOREIGN KEY (role_id)       REFERENCES role (id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission (id)
);

CREATE INDEX IF NOT EXISTS ix_role_permission_permission ON role_permission (permission_id);

CREATE TABLE IF NOT EXISTS account_role_assignment (
    id           uuid        NOT NULL,
    account_id   uuid        NOT NULL,
    role_id      uuid        NOT NULL,
    scope_id     uuid        NOT NULL,
    status       varchar(30) NOT NULL DEFAULT 'ACTIVE',
    assigned_at  timestamp   NOT NULL DEFAULT now(),
    expires_at   timestamp,
    assigned_by  varchar(100),
    created_at   timestamp   NOT NULL,
    created_by   varchar(100),
    updated_at   timestamp   NOT NULL,
    updated_by   varchar(100),
    CONSTRAINT pk_account_role_assignment PRIMARY KEY (id),
    CONSTRAINT fk_account_role_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT fk_account_role_role    FOREIGN KEY (role_id)    REFERENCES role (id),
    CONSTRAINT uq_account_role_scope UNIQUE (account_id, role_id, scope_id)
);

CREATE INDEX IF NOT EXISTS ix_assignment_account_effective ON account_role_assignment (account_id, status, expires_at);
CREATE INDEX IF NOT EXISTS ix_assignment_role ON account_role_assignment (role_id);
CREATE INDEX IF NOT EXISTS ix_assignment_scope ON account_role_assignment (scope_id);

CREATE TABLE IF NOT EXISTS audit_log (
    id           uuid        NOT NULL,
    status       varchar(30) NOT NULL DEFAULT 'ACTIVE',
    actor_type   varchar(20),
    actor_id     uuid,
    action       varchar(100) NOT NULL,
    module       varchar(80)  NOT NULL,
    target_type  varchar(80),
    target_id    uuid,
    branch_id    uuid,
    ip_address   varchar(45),
    user_agent   varchar(500),
    before_data  jsonb,
    after_data   jsonb,
    created_at   timestamp   NOT NULL,
    created_by   varchar(100),
    updated_at   timestamp   NOT NULL,
    updated_by   varchar(100),
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS ix_audit_action_created ON audit_log (action, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_target ON audit_log (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_audit_branch_created ON audit_log (branch_id, created_at);

CREATE TABLE IF NOT EXISTS scope (
    id          uuid        NOT NULL,
    scope_type  varchar(30) NOT NULL,
    branch_id   uuid,
    status      varchar(30) NOT NULL DEFAULT 'ACTIVE',
    created_at  timestamp   NOT NULL,
    updated_at  timestamp   NOT NULL,
    created_by  varchar(100),
    updated_by  varchar(100),
    CONSTRAINT pk_scope PRIMARY KEY (id),
    CONSTRAINT ck_scope_type  CHECK (scope_type IN ('ALL_SYSTEM', 'STORE', 'WAREHOUSE')),
    CONSTRAINT ck_scope_branch CHECK (
        (scope_type = 'ALL_SYSTEM' AND branch_id IS NULL)
        OR (scope_type IN ('STORE', 'WAREHOUSE') AND branch_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_scope_all_system ON scope (scope_type) WHERE scope_type = 'ALL_SYSTEM';
CREATE UNIQUE INDEX IF NOT EXISTS uq_scope_branch ON scope (scope_type, branch_id) WHERE branch_id IS NOT NULL;

ALTER TABLE account_role_assignment ADD CONSTRAINT fk_account_role_scope FOREIGN KEY (scope_id) REFERENCES scope (id);

CREATE TABLE IF NOT EXISTS customer (
    id                 uuid        NOT NULL,
    customer_code      varchar(50) NOT NULL,
    username           varchar(100),
    full_name          varchar(150) NOT NULL,
    phone              varchar(20),
    email              varchar(150),
    password         varchar(255),
    auth_provider      varchar(30) NOT NULL DEFAULT 'LOCAL',
    provider_id        varchar(150),
    has_local_password boolean     NOT NULL DEFAULT true,
    email_verified     boolean     NOT NULL DEFAULT false,
    avatar_url         varchar(500),
    date_of_birth      date,
    gender             varchar(20),
    status             varchar(30) NOT NULL DEFAULT 'ACTIVE',
    last_login_at      timestamp,
    created_at         timestamp   NOT NULL,
    created_by         varchar(100),
    updated_at         timestamp   NOT NULL,
    updated_by         varchar(100),
    CONSTRAINT pk_customer PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_code ON customer (customer_code);
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_username ON customer (username);
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_phone ON customer (phone);
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_email ON customer (email);
CREATE INDEX IF NOT EXISTS idx_customer_status ON customer (status);
CREATE INDEX IF NOT EXISTS idx_customer_provider ON customer (auth_provider, provider_id);
CREATE INDEX IF NOT EXISTS idx_customer_email_verified ON customer (email_verified);

CREATE TABLE IF NOT EXISTS customer_address (
    id             uuid        NOT NULL,
    customer_id    uuid        NOT NULL,
    receiver_name  varchar(150) NOT NULL,
    receiver_phone varchar(20) NOT NULL,
    address_line   varchar(255) NOT NULL,
    ward           varchar(100),
    district       varchar(100),
    city           varchar(100),
    latitude       numeric(10, 7),
    longitude      numeric(10, 7),
    is_default     boolean     NOT NULL DEFAULT false,
    status         varchar(30) NOT NULL DEFAULT 'ACTIVE',
    created_at     timestamp   NOT NULL,
    created_by     varchar(100),
    updated_at     timestamp   NOT NULL,
    updated_by     varchar(100),
    CONSTRAINT pk_customer_address PRIMARY KEY (id),
    CONSTRAINT fk_customer_address_customer FOREIGN KEY (customer_id) REFERENCES customer (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_customer_address_customer ON customer_address (customer_id);
CREATE INDEX IF NOT EXISTS idx_customer_address_default ON customer_address (customer_id, is_default);

CREATE TABLE IF NOT EXISTS refresh_token (
    id             uuid        NOT NULL,
    status         varchar(30) NOT NULL DEFAULT 'ACTIVE',
    principal_type varchar(20) NOT NULL,
    principal_id   uuid        NOT NULL,
    token_hash     varchar(255) NOT NULL,
    device_info    varchar(255),
    ip_address     varchar(64),
    expires_at     timestamp   NOT NULL,
    revoked_at     timestamp,
    created_at     timestamp   NOT NULL,
    created_by     varchar(100),
    updated_at     timestamp   NOT NULL,
    updated_by     varchar(100),
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT ck_refresh_principal_type CHECK (principal_type IN ('ACCOUNT', 'CUSTOMER'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_token_hash ON refresh_token (token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_principal ON refresh_token (principal_type, principal_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_refresh_principal_active ON refresh_token (principal_type, principal_id, status);
