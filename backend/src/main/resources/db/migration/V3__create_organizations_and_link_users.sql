-- Phase 5: organizations (tenant root) + user -> organization relationship.
--
-- organization_id is NOT NULL: CLAUDE.md §7 states "every business belongs to
-- an organization" as an unconditional invariant, and this project has no
-- production data yet, so no backfill/default-value step is included for the
-- existing `users` table. This means this migration will fail against ANY
-- environment (not just a local dev machine) whose `users` table already has
-- rows — including a shared staging/QA database seeded during earlier Phase 4
-- testing. Since migrations are immutable (CLAUDE.md §33), the fix in that
-- case is a new migration with a real backfill, not editing this one; for a
-- disposable local/dev database, `docker compose down -v` is enough.

CREATE TABLE organizations (
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

ALTER TABLE users
    ADD COLUMN organization_id UUID NOT NULL REFERENCES organizations (id);

-- Every tenant-scoped query filters by organization_id (docs/database.md §2),
-- so this is a required index, not a premature one.
CREATE INDEX ix_users_organization_id ON users (organization_id);
