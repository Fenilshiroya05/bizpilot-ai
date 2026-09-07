-- Phase 6: RBAC foundation (roles, permissions, user_roles, role_permissions).
--
-- Replaces the single `users.role` column (Phase 4) with the many-to-many
-- model CLAUDE.md §6 specifies, establishing ONE authoritative source of
-- truth for role assignment rather than two competing ones. No production
-- data exists yet, so this migration creates the new tables, seeds the fixed
-- role/permission catalog, migrates every existing user's current `role`
-- value into `user_roles`, and only then drops the old column — in that
-- order, so no user is ever without a role mid-migration.

CREATE TABLE roles (
    id         UUID PRIMARY KEY,
    name       VARCHAR(20) NOT NULL UNIQUE
                   CHECK (name IN ('OWNER', 'ADMIN', 'MANAGER', 'SALES', 'EMPLOYEE')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE permissions (
    id         UUID PRIMARY KEY,
    name       VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Pure many-to-many link tables: composite primary key, no surrogate id and
-- no created_at/updated_at — an assignment either exists or doesn't, there is
-- no independent lifecycle to audit here (unlike Organization/User/RefreshToken).
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role_id ON user_roles (role_id);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions (id) ON DELETE RESTRICT,
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX ix_role_permissions_permission_id ON role_permissions (permission_id);

-- Seed roles — the fixed set from CLAUDE.md §9. Do not add roles beyond these.
INSERT INTO roles (id, name, created_at, updated_at) VALUES
    (gen_random_uuid(), 'OWNER', now(), now()),
    (gen_random_uuid(), 'ADMIN', now(), now()),
    (gen_random_uuid(), 'MANAGER', now(), now()),
    (gen_random_uuid(), 'SALES', now(), now()),
    (gen_random_uuid(), 'EMPLOYEE', now(), now());

-- Seed permissions — the fixed catalog from CLAUDE.md §9. Do not invent names.
INSERT INTO permissions (id, name, created_at, updated_at) VALUES
    (gen_random_uuid(), 'CUSTOMER_READ', now(), now()),
    (gen_random_uuid(), 'CUSTOMER_CREATE', now(), now()),
    (gen_random_uuid(), 'CUSTOMER_UPDATE', now(), now()),
    (gen_random_uuid(), 'CUSTOMER_DELETE', now(), now()),
    (gen_random_uuid(), 'LEAD_READ', now(), now()),
    (gen_random_uuid(), 'LEAD_CREATE', now(), now()),
    (gen_random_uuid(), 'LEAD_UPDATE', now(), now()),
    (gen_random_uuid(), 'LEAD_DELETE', now(), now()),
    (gen_random_uuid(), 'QUOTATION_READ', now(), now()),
    (gen_random_uuid(), 'QUOTATION_CREATE', now(), now()),
    (gen_random_uuid(), 'QUOTATION_UPDATE', now(), now()),
    (gen_random_uuid(), 'QUOTATION_DELETE', now(), now()),
    (gen_random_uuid(), 'DOCUMENT_READ', now(), now()),
    (gen_random_uuid(), 'DOCUMENT_UPLOAD', now(), now()),
    (gen_random_uuid(), 'AI_USE', now(), now()),
    (gen_random_uuid(), 'USER_MANAGE', now(), now());

-- Role -> permission mapping. CLAUDE.md defines roles and permission names
-- but not this mapping — it is an implementation decision (see
-- docs/security.md for the full documented reasoning). Summary:
--   OWNER, ADMIN : full permission catalog (no permission exists yet, e.g.
--                  billing/org-deletion, that would distinguish them)
--   MANAGER      : full CRUD on future business records, no user management
--   SALES        : day-to-day CRUD, no delete
--   EMPLOYEE     : read-only + AI use (matches the least-privilege default
--                  already assigned at self-registration since Phase 4)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('OWNER', 'ADMIN');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'MANAGER'
  AND p.name IN ('CUSTOMER_READ', 'CUSTOMER_CREATE', 'CUSTOMER_UPDATE', 'CUSTOMER_DELETE',
                 'LEAD_READ', 'LEAD_CREATE', 'LEAD_UPDATE', 'LEAD_DELETE',
                 'QUOTATION_READ', 'QUOTATION_CREATE', 'QUOTATION_UPDATE', 'QUOTATION_DELETE',
                 'DOCUMENT_READ', 'DOCUMENT_UPLOAD', 'AI_USE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SALES'
  AND p.name IN ('CUSTOMER_READ', 'CUSTOMER_CREATE', 'CUSTOMER_UPDATE',
                 'LEAD_READ', 'LEAD_CREATE', 'LEAD_UPDATE',
                 'QUOTATION_READ', 'QUOTATION_CREATE', 'QUOTATION_UPDATE',
                 'DOCUMENT_READ', 'DOCUMENT_UPLOAD', 'AI_USE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'EMPLOYEE'
  AND p.name IN ('CUSTOMER_READ', 'LEAD_READ', 'QUOTATION_READ', 'DOCUMENT_READ', 'AI_USE');

-- Migrate every existing user's current single-column role into user_roles.
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = u.role;

-- users.role is now redundant — user_roles is the single authoritative source.
ALTER TABLE users DROP COLUMN role;
