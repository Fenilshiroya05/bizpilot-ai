-- Test-only fixture table backing SampleEntity, used solely to verify
-- BaseEntity's id generation and created_at/updated_at auditing behavior
-- through a real persistence round-trip. Never applied to production
-- (see application-test.yml, which is the only profile enabling this location).
-- Versioned from V1001 (instead of V2) so it never collides with a real,
-- production migration version as those are added phase by phase.
CREATE TABLE sample_entities (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
