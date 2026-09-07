-- Phase 8: leads + lead_activities (CLAUDE.md §11).
--
-- lead_activities backs three CLAUDE.md §11 features (activities, notes,
-- history) with a single table discriminated by `type`, following the exact
-- same pattern as customer_activities (Phase 7, V5) — see
-- sales.entity.LeadActivityType's Javadoc.
--
-- lead_activities intentionally has no organization_id column of its own:
-- same "child of a tenant-scoped parent" precedent as customer_activities
-- and refresh_tokens — every access path resolves the parent lead via an
-- organization-scoped lookup first, so lead_id alone is always already
-- tenant-safe by the time this table is queried.
--
-- `status` (a fixed, CLAUDE.md-defined business-outcome enum) and
-- `archived_at` (a separate, orthogonal record-lifecycle marker) are
-- deliberately independent: status has no ARCHIVED value (CLAUDE.md §11
-- defines exactly 7 status values, no more), so archiving cannot be modeled
-- as a status transition the way it was for customers (Phase 7).

CREATE TABLE leads (
    id                  UUID PRIMARY KEY,
    organization_id     UUID NOT NULL REFERENCES organizations (id),
    name                VARCHAR(255) NOT NULL,
    company             VARCHAR(255),
    email               VARCHAR(255),
    phone               VARCHAR(30),
    status              VARCHAR(20) NOT NULL
                            CHECK (status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'PROPOSAL',
                                               'NEGOTIATION', 'WON', 'LOST')),
    source              VARCHAR(20) NOT NULL
                            CHECK (source IN ('WEBSITE', 'REFERRAL', 'SOCIAL_MEDIA', 'EMAIL', 'PHONE', 'OTHER')),
    priority            VARCHAR(10) NOT NULL
                            CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    follow_up_date      DATE,
    assigned_to_user_id UUID REFERENCES users (id),
    archived_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL
);

-- Every tenant-scoped query filters by organization_id (docs/database.md §2).
CREATE INDEX ix_leads_organization_id ON leads (organization_id);

-- Supports the default listing query (organization + status filter together).
CREATE INDEX ix_leads_organization_id_status ON leads (organization_id, status);

-- Supports the default "exclude archived" / explicit "archived only" listing query.
CREATE INDEX ix_leads_organization_id_archived_at ON leads (organization_id, archived_at);

-- Supports assignee filtering ("my leads") and the assignee-organization validation join.
CREATE INDEX ix_leads_assigned_to_user_id ON leads (assigned_to_user_id);

-- Supports the follow-up-date filter (e.g. "leads due for follow-up").
CREATE INDEX ix_leads_follow_up_date ON leads (follow_up_date);

CREATE TABLE lead_activities (
    id                 UUID PRIMARY KEY,
    lead_id            UUID NOT NULL REFERENCES leads (id) ON DELETE CASCADE,
    type               VARCHAR(20) NOT NULL
                           CHECK (type IN ('CREATED', 'STATUS_CHANGED', 'ASSIGNED', 'ARCHIVED', 'NOTE')),
    content            VARCHAR(2000) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users (id),
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_lead_activities_lead_id ON lead_activities (lead_id);

-- Supports the notes-only and activities-only (type-filtered) list endpoints.
CREATE INDEX ix_lead_activities_lead_id_type ON lead_activities (lead_id, type);
