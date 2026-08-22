-- Identity: login accounts and the role/permission model.
--
-- Every table carries the audit columns declared on BaseEntity. Timestamps are timestamptz:
-- a university runs across time zones and daylight-saving transitions, and a naive timestamp
-- makes "when was this grade changed?" unanswerable twice a year.

CREATE TABLE users (
    id              UUID         PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    status          VARCHAR(30)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email    UNIQUE (email)
);
CREATE INDEX idx_users_status ON users (status);

CREATE TABLE roles (
    id          UUID        PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    CONSTRAINT uk_roles_name UNIQUE (name)
);

CREATE TABLE permissions (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    CONSTRAINT uk_permissions_name UNIQUE (name)
);

-- Pure association: no identity or audit of its own, so a plain composite key is correct here.
CREATE TABLE role_permissions (
    role_id       UUID NOT NULL,
    permission_id UUID NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role       FOREIGN KEY (role_id)       REFERENCES roles (id)       ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);
CREATE INDEX idx_role_permissions_permission ON role_permissions (permission_id);

-- A grant, by contrast, is an event worth auditing, so it is a full entity.
CREATE TABLE user_roles (
    id                 UUID        PRIMARY KEY,
    user_id            UUID        NOT NULL,
    role_id            UUID        NOT NULL,
    granted_at         TIMESTAMPTZ NOT NULL,
    granted_by_user_id UUID,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    CONSTRAINT uk_user_roles_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);
CREATE INDEX idx_user_roles_user ON user_roles (user_id);
