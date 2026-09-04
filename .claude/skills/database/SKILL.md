---
name: database
description: Use for BizPilot AI database work — PostgreSQL/PGVector schema design, JPA/Hibernate entities and relationships, Flyway migrations, indexes, query performance, transactions, and tenant isolation at the data layer. Triggers on "add a migration", "design this entity/table", "add an index", "change the schema", "is this query safe". Never proposes destructive operations without explicit confirmation.
---

# Database (PostgreSQL / JPA / Flyway)

## 1. Purpose

Design and evolve the BizPilot AI schema safely: new tables/columns, JPA entity mappings, Flyway migrations, and query/index work — consistent with the conventions in `docs/database.md`.

## 2. When to Use

- Adding or changing a table, column, index, or relationship.
- Writing a new Flyway migration.
- Reviewing whether a JPA mapping or query is correct, performant, and tenant-safe.

## 3. When Not to Use

- Pure Java/Spring service logic with no schema/query involvement → `bizpilot-backend`.
- General security review (use `security-review` for auth/RBAC; this skill still owns tenant-isolation-at-the-query-level).
- Don't use this to run destructive commands against a real database — see §6.

## 4. Project-Specific Context

- Engine: PostgreSQL + PGVector extension for embeddings; Flyway manages all schema changes (CLAUDE.md §6, §33).
- Planned entity list and conventions are in `docs/database.md` — UUID primary keys, `created_at`/`updated_at` timestamptz on every table, explicit FKs, `organization_id` on every tenant-scoped table, `numeric` (never float) for money.
- Migrations are immutable once merged: `V1__initial_schema.sql`, `V2__add_customer_fields.sql`, etc. — never edit a merged migration; add a new one.
- Vector search on `document_chunks` must always be filtered by `organization_id` before returning results to the AI layer (CLAUDE.md §17, §38).
- Status columns are preferred over hard deletes for archivable entities (customers, leads) — see `docs/database.md §2`.

## 5. Required Workflow

1. Check `docs/database.md` for the entity's planned shape before inventing a new one; update that doc if the actual design deviates (and explain why).
2. Check `docs/roadmap.md` — don't build tables belonging to a later phase.
3. Write the Flyway migration as a new, numbered, immutable file — never modify an existing one.
4. Add/update the JPA entity to match exactly; keep entities free of business logic beyond simple invariants.
5. Add indexes for every foreign key and every column used in a `WHERE`/`ORDER BY` on a list endpoint, especially `organization_id`.
6. For any query touching a tenant-scoped table, confirm it filters by `organization_id` derived from the security context — flag it if it doesn't.

## 6. Technical Rules

- **Never** run or recommend destructive operations (`DROP TABLE`, `TRUNCATE`, `DELETE` without a `WHERE`, altering a column type in a lossy way) against anything but a fresh local/dev database, and only with the user's explicit confirmation each time.
- No manual production schema edits — everything is a Flyway migration (CLAUDE.md §33).
- No `SELECT *` in repository queries returning DTOs; project only needed columns where it matters for performance.
- Prefer `@Query` with explicit JPQL/native SQL over relying on ambiguous derived query method names for anything non-trivial.
- Pagination (`Pageable`) on every list-returning repository method backing a REST list endpoint.

## 7. Quality Checks

- Every new/changed table has: UUID PK, `created_at`/`updated_at`, correct FKs, `organization_id` if tenant-scoped, and indexes on FK/filter columns.
- Migration file naming is sequential and doesn't collide with an existing version.
- Entity ↔ migration ↔ `docs/database.md` all agree.

## 8. Security Considerations

- Tenant isolation is a security boundary, not a convenience filter (CLAUDE.md §7, §38) — treat a missing `organization_id` filter as a high-severity finding, not a style nit.
- No secrets (DB passwords, connection strings) in migration files or seed data.

## 9. Testing Expectations

- New repository queries get a repository-level test (ideally against a real Postgres via Testcontainers once that infra exists).
- Tenant-scoped queries need a test proving Organization A cannot read Organization B's rows.
- Migrations should be tested by actually running them against a clean database, not just eyeballed.

## 10. Expected Final Output/Report

State: the migration file(s) added, the entity/mapping changes, indexes added, tenant-isolation status of any new query, and any destructive action that was *considered but not performed* pending confirmation.
