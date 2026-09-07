-- Phase 9: products + product_categories, plus PRODUCT_* RBAC permissions.
--
-- CLAUDE.md §9 lists its permission catalog under the heading "Examples:" —
-- unlike the closed LeadStatus/LeadSource enums (§11), this signals the
-- catalog is illustrative, not exhaustive. Adding PRODUCT_* permissions here
-- extends that catalog rather than violating it.
--
-- This migration ONLY inserts new permission rows and role_permission
-- mappings for those new permissions — it never touches the 16 rows/pairs
-- V4 already committed. Every INSERT below is scoped to
-- `p.name IN ('PRODUCT_...')` specifically so it can never collide with
-- the existing role_permissions composite primary key.

INSERT INTO permissions (id, name, created_at, updated_at) VALUES
    (gen_random_uuid(), 'PRODUCT_READ', now(), now()),
    (gen_random_uuid(), 'PRODUCT_CREATE', now(), now()),
    (gen_random_uuid(), 'PRODUCT_UPDATE', now(), now()),
    (gen_random_uuid(), 'PRODUCT_DELETE', now(), now());

-- Role -> permission mapping, mirroring the same philosophy documented for
-- Customers/Leads (docs/security.md): OWNER/ADMIN/MANAGER get full CRUD,
-- SALES gets CRUD minus delete, EMPLOYEE gets read-only.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name IN ('PRODUCT_READ', 'PRODUCT_CREATE', 'PRODUCT_UPDATE', 'PRODUCT_DELETE')
  AND r.name IN ('OWNER', 'ADMIN', 'MANAGER');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name IN ('PRODUCT_READ', 'PRODUCT_CREATE', 'PRODUCT_UPDATE')
  AND r.name = 'SALES';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name = 'PRODUCT_READ'
  AND r.name = 'EMPLOYEE';

-- Deliberately minimal: CLAUDE.md gives no field list for categories at all
-- (see docs/database.md) — a display name only, mirroring
-- organization.entity.Organization's equally minimal design.
CREATE TABLE product_categories (
    id              UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    name            VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_product_categories_organization_id ON product_categories (organization_id);

CREATE TABLE products (
    id              UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    -- ON DELETE SET NULL: deleting a category never deletes or blocks on
    -- referencing products, it only clears their category assignment —
    -- the simplest, least-destructive behavior for this lightweight
    -- metadata relationship (see products.service.ProductCategoryService).
    category_id     UUID REFERENCES product_categories (id) ON DELETE SET NULL,
    sku             VARCHAR(50) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     VARCHAR(2000),
    unit            VARCHAR(20) NOT NULL,
    -- NUMERIC, never floating point, for monetary/tax values (docs/database.md §2).
    price           NUMERIC(19, 4) NOT NULL CHECK (price >= 0),
    tax_percentage  NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (tax_percentage >= 0 AND tax_percentage <= 100),
    status          VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_products_organization_id ON products (organization_id);

-- Supports the status filter (no "excluded by default" semantics here,
-- unlike customers/leads — see docs/database.md).
CREATE INDEX ix_products_organization_id_status ON products (organization_id, status);

CREATE INDEX ix_products_category_id ON products (category_id);

-- Organization-scoped uniqueness, not global (the same SKU may legitimately
-- belong to products of two different organizations). SKU is normalized to
-- uppercase at the application layer before this index is ever checked, so
-- a plain (non-partial, non-functional) unique index is sufficient.
CREATE UNIQUE INDEX ux_products_org_sku ON products (organization_id, sku);
