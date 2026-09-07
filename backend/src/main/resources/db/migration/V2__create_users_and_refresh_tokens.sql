-- Phase 4: authentication & identity foundation.
--
-- users.email is unique globally (not per-organization) since the
-- organizations table doesn't exist yet (Phase 5). Once it does, this
-- uniqueness constraint is expected to be revisited as (organization_id, email).

-- email is always lower-cased by UserService before it reaches this table, so
-- a plain unique constraint (matching the JPA @Column(unique = true) mapping)
-- is sufficient — no functional/LOWER() index needed.
CREATE TABLE users (
    id             UUID PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER', 'SALES', 'EMPLOYEE')),
    status         VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE TABLE refresh_tokens (
    id                       UUID PRIMARY KEY,
    user_id                  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash               VARCHAR(64) NOT NULL,
    expires_at               TIMESTAMPTZ NOT NULL,
    revoked_at               TIMESTAMPTZ,
    replaced_by_token_hash   VARCHAR(64),
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
