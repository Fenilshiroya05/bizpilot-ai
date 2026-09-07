-- Phase 7: customers + customer_activities (CRM module foundation, CLAUDE.md §10).
--
-- customer_activities backs three CLAUDE.md §10 features (activities, notes,
-- history) with a single table discriminated by `type`, rather than three
-- separate tables or a generic audit/event-sourcing system — see
-- docs/database.md and crm.entity.CustomerActivityType's Javadoc.
--
-- customer_activities intentionally has no organization_id column of its own:
-- it follows the same "child of a tenant-scoped parent" precedent as
-- refresh_tokens (Phase 4) — every access path resolves the parent customer
-- via an organization-scoped lookup first, so customer_id alone is always
-- already tenant-safe by the time this table is queried.

CREATE TABLE customers (
    id              UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    name            VARCHAR(255) NOT NULL,
    company         VARCHAR(255),
    email           VARCHAR(255),
    phone           VARCHAR(30),
    address         VARCHAR(500),
    gstin           VARCHAR(15),
    status          VARCHAR(20) NOT NULL
                        CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    notes           VARCHAR(2000),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

-- Every tenant-scoped query filters by organization_id (docs/database.md §2).
CREATE INDEX ix_customers_organization_id ON customers (organization_id);

-- Supports the default listing query (organization + status filter together).
CREATE INDEX ix_customers_organization_id_status ON customers (organization_id, status);

-- Organization-scoped uniqueness, not global (the same email may legitimately
-- belong to customers of two different organizations). Partial index: NULL
-- email is allowed on any number of customers, since email is optional.
CREATE UNIQUE INDEX ux_customers_org_email ON customers (organization_id, email) WHERE email IS NOT NULL;

CREATE TABLE customer_activities (
    id                 UUID PRIMARY KEY,
    customer_id        UUID NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    type               VARCHAR(20) NOT NULL
                           CHECK (type IN ('CREATED', 'STATUS_CHANGED', 'ARCHIVED', 'NOTE')),
    content            VARCHAR(2000) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users (id),
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_customer_activities_customer_id ON customer_activities (customer_id);

-- Supports the notes-only and activities-only (type-filtered) list endpoints.
CREATE INDEX ix_customer_activities_customer_id_type ON customer_activities (customer_id, type);
