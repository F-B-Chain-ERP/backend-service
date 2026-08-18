-- ============================================================
-- changeSet id=001-init-schema  (tương đương 001-init-schema.yaml)
-- Chạy trực tiếp trên PostgreSQL, không qua Liquibase
-- ============================================================

CREATE TABLE account (
    id            uuid         NOT NULL,
    username      varchar(100) NOT NULL,
    password      varchar(255) NOT NULL,
    full_name     varchar(150) NOT NULL,
    email         varchar(150),
    phone         varchar(20),
    avatar_url    varchar(500),
    status        varchar(30)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at timestamp,
    auth_provider varchar(50),
    provider_id   varchar(255),
    created_at    timestamp    NOT NULL,
    updated_at    timestamp    NOT NULL,
    created_by    varchar(100),
    updated_by    varchar(100),
    CONSTRAINT pk_account PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_account_username ON account (username);
CREATE UNIQUE INDEX uq_account_email    ON account (email);

CREATE TABLE role (
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

CREATE UNIQUE INDEX uq_role_code ON role (code);

CREATE TABLE permission (
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

CREATE UNIQUE INDEX uq_permission_code ON permission (code);

CREATE TABLE role_permission (
    role_id       uuid NOT NULL,
    permission_id uuid NOT NULL,
    CONSTRAINT pk_role_permission PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role       FOREIGN KEY (role_id)       REFERENCES role (id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission (id)
);

CREATE TABLE account_role_assignment (
    account_id  uuid        NOT NULL,
    role_id     uuid        NOT NULL,
    status      varchar(30) NOT NULL DEFAULT 'ACTIVE',
    assigned_at timestamp   NOT NULL DEFAULT now(),
    expires_at  timestamp,
    CONSTRAINT pk_account_role_assignment PRIMARY KEY (account_id, role_id),
    CONSTRAINT fk_account_role_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT fk_account_role_role    FOREIGN KEY (role_id)    REFERENCES role (id)
);

CREATE TABLE audit_log (
    id               uuid         NOT NULL,
    actor_account_id uuid,
    action           varchar(100) NOT NULL,
    module           varchar(80)  NOT NULL,
    target_type      varchar(80),
    target_id        uuid,
    ip_address       varchar(45),
    user_agent       varchar(500),
    before_data      jsonb,
    after_data       jsonb,
    created_at       timestamp    NOT NULL,
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);
