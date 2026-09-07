# Database

> Status: Phase 7 — `customers` and `customer_activities` exist (V5 migration), the first real tenant-scoped, RBAC-gated business tables. Built on top of `roles`/`permissions`/`user_roles`/`role_permissions` (Phase 6, V4), `organizations`/tenant-scoping (Phase 5, V3), `users`/`refresh_tokens` (Phase 4, V2), and the Phase 3 database foundation. Remaining business tables are introduced incrementally starting Phase 8 (leads), per `docs/roadmap.md`.

## 0. Implementation Notes (Phase 3)

- **Connection**: `spring.datasource.*` in `application.yml` is built entirely from environment variables — `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` (see `.env.example`). `DB_PASSWORD` has no default; the app fails fast at startup if it's unset rather than falling back to an insecure default.
- **Hibernate**: `spring.jpa.hibernate.ddl-auto=validate` — Hibernate never creates or alters schema; Flyway is the sole migration authority (CLAUDE.md §6, §33).
- **Flyway**: migrations live in `backend/src/main/resources/db/migration/`, versioned `V1__initial_schema.sql`, `V2__...`, etc., and are immutable once merged. `V1__initial_schema.sql` currently only enables the `vector` (PGVector) extension — no business tables yet, since those belong to later phases.
- **Base persistence support**: `com.bizpilot.common.persistence.BaseEntity` is a `@MappedSuperclass` providing a JPA-generated `UUID` id plus `created_at`/`updated_at` auditing (via Spring Data JPA auditing, enabled in `common/config/JpaConfig`). Every future entity extends it.
- **Testing**: `backend/src/test/java/com/bizpilot/TestcontainersConfiguration.java` provides a real, containerized PostgreSQL (`pgvector/pgvector:pg16`, matching `docker-compose.yml`) via Spring Boot's `@ServiceConnection`, used by both application-context tests and a dedicated `BaseEntityPersistenceTest` (which round-trips a test-only fixture entity/table defined only under `src/test/**`, activated only in the `test` profile — never part of production migrations or schema).

## 0a. Implementation Notes (Phase 4)

- `V2__create_users_and_refresh_tokens.sql` adds `users` (email unique globally — not yet per-organization, see below) and `refresh_tokens` (FK to `users`, `ON DELETE CASCADE` — deliberate, since a refresh token has no meaning without its owning user).
- `users.role` and `users.status` were plain `VARCHAR` columns with a `CHECK` constraint restricting them to the enum values. `users.role` was replaced by the full junction-table model in Phase 6 (V4) — see §0c; `users.status` is unaffected.
- `refresh_tokens.token_hash` stores only the SHA-256 hash of the refresh token, never the raw value (see [security.md](security.md)).
- Both tables extend the `BaseEntity` foundation from Phase 3 (UUID id, `created_at`/`updated_at`).

## 0b. Implementation Notes (Phase 5)

- `V3__create_organizations_and_link_users.sql` adds `organizations` (id, `name`, `created_at`/`updated_at` — no other fields; CLAUDE.md doesn't specify an organization field list) and `users.organization_id` (`UUID NOT NULL REFERENCES organizations(id)`, indexed).
- `organization_id` is **NOT NULL with no default** — CLAUDE.md §7 states "every business belongs to an organization" as an unconditional invariant, and the project has no production data yet, so no backfill step was included. This migration will therefore fail against **any** environment (not just a local machine) whose `users` table already has rows — including a shared staging/QA database seeded during earlier Phase 4 testing. Migrations are immutable (CLAUDE.md §33), so that case needs a *new* migration with a real backfill, not an edit to this one; for a disposable local/dev database, `docker compose down -v` is sufficient.
- **No generic `TenantScopedEntity` base class was introduced.** The originally-anticipated abstraction (see prior revision of this doc) would have been premature: `User` is still the *only* entity that needs `organization_id` in this phase (no customers/leads/etc. exist yet — those are Phase 7+). Extracting a shared base class for a single use case is exactly the "generic multi-tenancy framework the application doesn't need yet" the Phase 5 spec explicitly warns against. Revisit this when the first real business entity (Phase 7) needs the same column.
- **Tenant resolution**: the current organization is derived only from the authenticated JWT (`organization.TenantContext`, backed by `security.CurrentUserProvider`) — never from client-supplied input. See [security.md](security.md) for the full mechanism and the reasoning behind auto-provisioning an organization at registration.

## 0c. Implementation Notes (Phase 6)

- `V4__create_rbac_model.sql` adds `roles`, `permissions`, `user_roles`, `role_permissions`; seeds the fixed 5-role and 16-permission catalog from CLAUDE.md §9; seeds the role→permission mapping (an implementation decision — see [security.md](security.md) §2a for the full table and reasoning); migrates every existing user's `users.role` value into `user_roles`; then drops `users.role`.
- **Single authoritative source**: the Phase 4 `users.role` column and the new junction-table model were never run side by side — the migration replaces one with the other in one step, specifically to avoid "two competing sources of truth" for role assignment.
- **Junction tables have no surrogate key**: `user_roles` and `role_permissions` use a composite primary key (`user_id, role_id` / `role_id, permission_id`) and no `created_at`/`updated_at` — unlike every other table in this project (which extends `BaseEntity`), a pure many-to-many assignment either exists or doesn't; there's no independent lifecycle worth auditing. Modeled in JPA as plain `@ManyToMany @JoinTable` relationships (`User.roles`, `Role.permissions`) — no dedicated junction-entity classes.
- **FK cascade choices** (deliberate per relationship, not a blanket rule — see §2 below): `user_roles.user_id` cascades on user deletion (an assignment is meaningless without the user); `user_roles.role_id` is `ON DELETE RESTRICT` (protects against deleting a role that's still assigned to someone); `role_permissions.role_id` cascades on role deletion; `role_permissions.permission_id` is `ON DELETE RESTRICT`.
- **No DB-level "every user has ≥1 role" constraint**: this is an application-level invariant only (registration always assigns `EMPLOYEE`; no code path in this phase removes a user's last role) — a true multi-row cardinality constraint would need a trigger, which was judged unnecessary complexity for this phase.

## 0d. Implementation Notes (Phase 7)

- `V5__create_customers.sql` adds `customers` and `customer_activities` — the first tables for a real business resource (CLAUDE.md §10), and the first to exercise the Phase 5 tenant-isolation and Phase 6 RBAC mechanisms against actual data.
- **`customers`**: fields mirror CLAUDE.md §10 exactly (name, company, email, phone, address, gstin, status, notes) plus the mandatory `organization_id` FK. Only `name` is `NOT NULL`; every other business field is nullable, per "do not unnecessarily collect personal information."
- **Clearing an optional field via PATCH**: `CustomerUpdateRequest` uses `null` = "leave unchanged" and `""` = "clear this field" (`CustomerService` normalizes `""` to `null` before persisting). The GSTIN `@Pattern` regex is deliberately `^$|<gstin-pattern>` (accepting empty string as well as a valid GSTIN) so a previously-set GSTIN can actually be cleared — a plain `@Pattern` without the `^$|` alternative only special-cases `null`, not `""`, which would otherwise make a set GSTIN permanently unclearable through the API (caught in security review, fixed before completion).
- **Status model** (CLAUDE.md is silent on values — an implementation decision, see [security.md](security.md) and `crm.entity.CustomerStatus`'s Javadoc): `ACTIVE`, `INACTIVE`, `ARCHIVED`. `ARCHIVED` is the soft-delete state, reachable only through the dedicated archive operation (`DELETE /api/v1/customers/{id}`), never through the general update endpoint — enforced in `CustomerService`, not just by convention.
- **Email uniqueness is per-organization, not global**: `ux_customers_org_email` is a *partial* unique index (`WHERE email IS NOT NULL`) on `(organization_id, email)` — the same email may legitimately belong to customers of two different organizations, and email itself is optional, so any number of customers may have a `NULL` email. Backed by an application-level pre-check (`existsByOrganizationIdAndEmailIgnoreCase`) plus a `DataIntegrityViolationException` fallback for the race condition, mirroring the pattern `identity.service.UserService` already uses for account-email uniqueness.
- **`customer_activities` backs three CLAUDE.md §10 features with one table**: "Customer activities," "Customer notes," and "Customer history" are all rows in this single table, discriminated by a `type` column (`CREATED`, `STATUS_CHANGED`, `ARCHIVED`, `NOTE`) rather than three separate tables — see `crm.entity.CustomerActivityType`'s Javadoc for the reasoning. This is deliberately **not** the general-purpose, project-wide Audit Logging capability (CLAUDE.md §24, still unbuilt — see [security.md](security.md) §7): it only ever records customer lifecycle events, nothing else in the system.
- **`customer_activities` has no `organization_id` column of its own** — same precedent as `refresh_tokens` (a child of a tenant-scoped parent, `users`, without its own tenant column). Every access path resolves the parent `Customer` via the org-scoped `findByIdAndOrganizationId` lookup first; only then is `customer_id` used to query this table, so cross-tenant access is structurally impossible without duplicating `organization_id` here. Entries are immutable (insert-only) by convention — nothing in `CustomerService` ever updates or deletes one.
- **Attribution without a JPA relationship**: `customer_activities.created_by_user_id` is a plain `UUID` column with a DB-level FK to `users`, not a `@ManyToOne` JPA relationship — it's an attribution stamp only; nothing in this phase needs to load the full `User` entity just to render an activity entry, so the extra relationship (and its lazy-loading surface) was left out.
- **Indexes**: `ix_customers_organization_id` (tenant scoping, mandatory per §2), `ix_customers_organization_id_status` (supports the default listing query, which always filters by organization + status), `ix_customer_activities_customer_id` and `ix_customer_activities_customer_id_type` (support the notes-only/activities-only/history list endpoints). No trigram/full-text index was added for the free-text `q` search — a plain `LIKE`-based, database-paginated query is sufficient for this phase's scope; revisit if search performance becomes a real concern at larger data volumes.

## 1. Engine

- **PostgreSQL** is the primary datastore.
- **PGVector** extension is used for embedding storage and similarity search (documents/RAG).
- **Flyway** manages all schema changes. Production schema is never modified manually.

## 2. Conventions

- **Primary keys:** UUID (`uuid` type), generated by the application or database default, for all tenant-scoped and externally-referenced entities.
- **Timestamps:** every table includes `created_at` and `updated_at` (`timestamptz`), managed automatically.
- **Foreign keys:** enforced at the database level for all relationships; cascading behavior chosen deliberately per relationship (no blanket `ON DELETE CASCADE`).
- **Indexes:** added for all foreign keys and frequently filtered/sorted columns (e.g. `organization_id`, `status`, `email`).
- **Tenant scoping:** every business table includes an `organization_id` foreign key to `organizations`, indexed, and enforced in every query at the repository/service level.
- **Soft delete / archive:** entities that support archiving (e.g. customers, leads) use a `status` column rather than hard deletes, to preserve history and audit trails.
- **Money/amounts:** stored as `numeric` with explicit precision/scale — never floating point.
- **Migrations are immutable:** once merged, a migration file is never edited; further changes are new migrations (e.g. `V1__initial_schema.sql`, `V2__add_customer_fields.sql`).

## 3. Planned Entities

| Table | Purpose |
|---|---|
| `organizations` ✅ (Phase 5) | Tenant root. `name` only — CLAUDE.md specifies no other fields. Every business record will ultimately belong to one. |
| `users` ✅ (Phase 4, org-scoped since Phase 5) | User accounts. `organization_id` NOT NULL since Phase 5 — see §0b. |
| `refresh_tokens` ✅ (Phase 4, not in original CLAUDE.md list) | Hashed refresh-token sessions, one row per issued token; supports rotation/revocation. |
| `roles` ✅ (Phase 6) | RBAC roles (OWNER, ADMIN, MANAGER, SALES, EMPLOYEE), replacing the Phase 4 single `users.role` column — see §0c. |
| `permissions` ✅ (Phase 6) | Granular permissions (e.g. `CUSTOMER_READ`, `AI_USE`) — fixed catalog of 16, per CLAUDE.md §9. |
| `user_roles` ✅ (Phase 6) | Join table assigning roles to users (composite PK, no `BaseEntity`). |
| `role_permissions` ✅ (Phase 6, not in original CLAUDE.md list) | Join table assigning permissions to roles (composite PK, no `BaseEntity`) — see §0c for why this wasn't in the original entity list. |
| `customers` ✅ (Phase 7) | CRM customer records — see §0d. |
| `customer_activities` ✅ (Phase 7, not in original CLAUDE.md list) | Backs "activities"/"notes"/"history" (CLAUDE.md §10) in one table — see §0d for why this wasn't in the original entity list. |
| `leads` | Sales leads, with status/source/priority. |
| `lead_activities` | Activity/history log per lead. |
| `products` | Product catalog items. |
| `product_categories` | Product categorization. |
| `quotations` | Quotations issued to customers. |
| `quotation_items` | Line items on a quotation. |
| `invoices` | Invoices issued to customers. |
| `invoice_items` | Line items on an invoice. |
| `tasks` | Task management records, optionally linked to a customer/lead. |
| `documents` | Uploaded document metadata and processing status. |
| `document_chunks` | Chunked, embedded document text for RAG (PGVector column). |
| `conversations` | AI assistant conversation sessions. |
| `conversation_messages` | Individual messages within a conversation. |
| `notifications` | User-facing notifications. |
| `audit_logs` | Audit trail of sensitive/important operations. |
| `ai_tool_calls` | Log of AI tool invocations, inputs, and results. |
| `ai_usage` | AI request/token/cost/duration tracking for future billing. |

Exact columns, constraints, and relationships are finalized and delivered as Flyway migrations incrementally, each by the phase that owns that table (e.g. `organizations` in Phase 5, `customers` in Phase 7) — see `docs/roadmap.md`. Phase 3 only established the datasource/JPA/Flyway foundation these migrations build on.

## 4. Relationships (indicative, subject to refinement as each table is introduced)

```text
organizations 1──* users
organizations 1──* customers
organizations 1──* leads
organizations 1──* products
organizations 1──* quotations
organizations 1──* invoices
organizations 1──* documents

users *──* roles (via user_roles)
roles *──* permissions

customers 1──* customer_activities
customers 1──* leads (optional association)
leads 1──* lead_activities

quotations 1──* quotation_items
quotation_items *──1 products

invoices 1──* invoice_items
invoice_items *──1 products

documents 1──* document_chunks

conversations 1──* conversation_messages
```

## 5. Data Access Rules

- Entities are **never** exposed directly through REST APIs — DTOs and mappers are mandatory at every controller boundary.
- All repository queries touching tenant-scoped tables must filter by `organization_id`; this is treated as a security control, not just a data-modeling detail (see [security.md](security.md)).
- Vector similarity queries against `document_chunks` must always be constrained to the requesting organization's documents.
