-- Phase 15: RAG foundation — document_chunks (relational integrity) and
-- vector_store (Spring AI PgVectorStore-compatible schema).
--
-- The "vector" PostgreSQL extension is already enabled by V1__initial_schema.sql
-- (Phase 3) — this migration deliberately does NOT re-run CREATE EXTENSION.
--
-- document_chunks is the DB-enforced tenant/document integrity table CLAUDE.md
-- §6/§17 asks for. It intentionally stores NEITHER chunk text NOR the
-- embedding vector — both remain the sole responsibility of vector_store
-- below (owned by Spring AI's PgVectorStore at query/delete time). Storing
-- them twice would be redundant and a consistency risk; document_chunks
-- exists purely so every chunk is tied, by a real NOT NULL foreign key, to
-- exactly one organization and one document — a guarantee Spring AI's own
-- JSON metadata column cannot give at the database-schema level.
CREATE TABLE document_chunks (
    id               UUID PRIMARY KEY,
    organization_id  UUID NOT NULL REFERENCES organizations (id),
    document_id      UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index      INTEGER NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_document_chunks_document_id_chunk_index UNIQUE (document_id, chunk_index)
);

CREATE INDEX ix_document_chunks_organization_id ON document_chunks (organization_id);
CREATE INDEX ix_document_chunks_document_id ON document_chunks (document_id);

-- vector_store: the exact table shape Spring AI's PgVectorStore (1.1.8,
-- verified against its actual source at the pinned tag, not assumed) expects
-- at its default public.vector_store location — id/content/metadata/embedding,
-- metadata as plain "json" (not "jsonb" — Spring AI's own schema-init DDL
-- uses "json"; its INSERT statement casts the bound parameter to ::jsonb,
-- which Postgres accepts via its built-in jsonb->json assignment cast).
--
-- Flyway owns this table (bizpilot.ai / spring.ai.vectorstore.pgvector's own
-- initialize-schema is left false — see application.yml), exactly like every
-- other table in this project ("Flyway is the single source of truth for
-- schema changes"). Spring AI's own schema-init path would use
-- uuid_generate_v4() (requiring the uuid-ossp extension) as the id default;
-- gen_random_uuid() (built into PostgreSQL core since v13, no extra
-- extension needed) is used here instead, per project convention. The
-- "hstore" extension Spring AI's own schema-init defensively enables is not
-- actually referenced by any INSERT/SELECT/DELETE this store issues
-- (verified against source) and is therefore not created here.
--
-- Every row is always inserted with an explicit id, generated application-
-- side as the SAME UUID as its corresponding document_chunks row (see
-- DocumentProcessingService) — the DEFAULT below is a harmless backstop,
-- never actually relied upon.
CREATE TABLE vector_store (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(1536)
);

-- HNSW + cosine distance (vector_cosine_ops) — matches
-- spring.ai.vectorstore.pgvector.{index-type=HNSW, distance-type=COSINE_DISTANCE}
-- in application.yml, and OpenAI's text-embedding-3-small's normalized
-- embeddings, for which cosine distance is the recommended metric. Index
-- name matches PgVectorStore's own default ("spring_ai_vector_index" for the
-- default table name) so the table is indistinguishable, from Spring AI's
-- point of view, from one it created itself with initialize-schema=true.
CREATE INDEX spring_ai_vector_index ON vector_store USING hnsw (embedding vector_cosine_ops);
