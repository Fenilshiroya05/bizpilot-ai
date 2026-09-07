# Database

> Status: Phase 13 — `documents` exists (V11 migration), the first table storing a locator (`storage_key`) for content that lives outside PostgreSQL entirely (CLAUDE.md §16's explicit "never store uploaded files directly in the database" prohibition) rather than any form of the data itself. Uses a `CHECK` constraint closing the content type to exactly the 3 supported formats. V11 adds only `DOCUMENT_DELETE` — `DOCUMENT_READ`/`DOCUMENT_UPLOAD` already existed since V4 (Phase 6) and are untouched. Built on top of `tasks` (Phase 12, V10), `invoices`/`invoice_items` (Phase 11, V9), `quotations`/`quotation_items` (Phase 10, V8), `products`/`product_categories` (Phase 9, V7), `leads`/`lead_activities` (Phase 8, V6), `customers`/`customer_activities` (Phase 7, V5), `roles`/`permissions`/`user_roles`/`role_permissions` (Phase 6, V4), `organizations`/tenant-scoping (Phase 5, V3), `users`/`refresh_tokens` (Phase 4, V2), and the Phase 3 database foundation. Remaining business tables are introduced incrementally starting Phase 14, per `docs/roadmap.md`.

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

## 0e. Implementation Notes (Phase 8)

- `V6__create_leads.sql` adds `leads` and `lead_activities` — both **explicitly named** in the CLAUDE.md §6 initial-entity list (unlike Phase 7, where `customer_activities` had to be invented as an extra table).
- **Identifying fields are deliberately smaller than Customer's**: CLAUDE.md §11 defines no field list for leads at all (unlike Customer, §10) — an implementation decision, see [security.md](security.md) and `sales.entity.Lead`'s Javadoc. Only `name`, `company`, `email`, `phone` exist; no `address` or `gstin` (tax/account concerns that don't apply to an unqualified lead) and, unlike `customers`, **no `notes` column on the entity itself** — "Notes" (§11) is satisfied entirely by `lead_activities` (type `NOTE`), avoiding the field-vs-activity-feature duplication `customers.notes` has.
- **No Lead → Customer relationship was implemented.** CLAUDE.md never mentions such a relationship anywhere (§10, §11, §14 all checked). This doc's own §4 relationships diagram previously sketched `customers 1──* leads (optional association)` as a Phase-1-era "indicative, subject to refinement" placeholder — that line has been removed; it was never a CLAUDE.md requirement, and adding an optional `customer_id` FK now would introduce tenant-validation surface area and IDOR test scenarios for a relationship nothing in this phase actually needs. Revisit only if a later phase (e.g. Quotations, which does need a customer association) requires linking leads to customers.
- **`status` vs. `archived_at` are deliberately independent columns**, not one field: CLAUDE.md §11 fixes `leads.status` to exactly 7 values (`NEW`/`CONTACTED`/`QUALIFIED`/`PROPOSAL`/`NEGOTIATION`/`WON`/`LOST`) with **no `ARCHIVED` value permitted** — unlike Customer, archiving cannot be modeled as an 8th status value. `archived_at` is a separate nullable `TIMESTAMPTZ` (same nullable-timestamp-means-"hasn't happened" convention as `refresh_tokens.revoked_at`, Phase 4) — `NULL` = active/visible, non-`NULL` = archived/excluded from default listing. A lead can be `WON` or `LOST` and still not be archived.
- **`priority`** (`LOW`/`MEDIUM`/`HIGH`) — CLAUDE.md requires the field but not its values (implementation decision, see `sales.entity.LeadPriority`'s Javadoc): deliberately a different, smaller set than Task priority (`LOW`/`MEDIUM`/`HIGH`/`URGENT`, CLAUDE.md §23), not reused wholesale just because it already exists elsewhere.
- **`assigned_to_user_id`** is a nullable, plain `UUID` FK to `users` (no `ON DELETE` clause — default `RESTRICT`, consistent with `customer_activities.created_by_user_id`), validated at the application layer to belong to the same organization as the lead (`identity.repository.UserRepository.findByIdAndOrganizationId`, added in this phase) *and* to have `UserStatus.ACTIVE` (a disabled/locked assignee would leave the lead silently unworked with no signal to the assigner — caught in security review, fixed before completion) before ever being persisted — never a raw client-supplied value trusted as-is.
- **`lead_activities` backs three CLAUDE.md §11 features with one table**, identical pattern to `customer_activities` (Phase 7): `type` (`CREATED`, `STATUS_CHANGED`, `ASSIGNED`, `ARCHIVED`, `NOTE`) discriminates "Lead activities," "Notes," and "Lead history" — one extra type (`ASSIGNED`) versus Customer's set, since Lead has an assignment feature Customer doesn't. No `organization_id` column of its own — same "child of a tenant-scoped parent" precedent.
- **Indexes**: `ix_leads_organization_id` (tenant scoping), `ix_leads_organization_id_status` (default listing), `ix_leads_organization_id_archived_at` (default "exclude archived" / explicit "archived only" listing), `ix_leads_assigned_to_user_id` (assignee filtering and the assignee-organization validation join), `ix_leads_follow_up_date` (the follow-up-date filter), `ix_lead_activities_lead_id`/`ix_lead_activities_lead_id_type` (notes-only/activities-only/history list endpoints).

## 0f. Implementation Notes (Phase 9)

- `V7__create_products.sql` adds `products` and `product_categories` — both **explicitly named** in the CLAUDE.md §6 initial-entity list. Unlike V5/V6, this migration is **not purely additive-table**: it also inserts `PRODUCT_READ`/`CREATE`/`UPDATE`/`DELETE` into the pre-existing `permissions` table and corresponding rows into `role_permissions` (established by V4). See [security.md](security.md) §2d for the full reasoning (CLAUDE.md §9 frames its permission list as "Examples:", not closed) and the collision-safety argument (every `INSERT` is scoped to `p.name IN ('PRODUCT_...')`, so it can never re-insert a `(role_id, permission_id)` pair V4 already committed).
- **`product_categories`**: deliberately minimal — CLAUDE.md gives no field list for categories at all (an implementation decision, see `products.entity.ProductCategory`'s Javadoc): just a display name, mirroring `organization.entity.Organization`'s equally minimal design. No description field was added.
- **`products`**: fields mirror CLAUDE.md §13 exactly (SKU, name, description, unit, price, tax percentage, status) plus the mandatory `organization_id` FK and an optional `category_id` FK. Only `name`, `sku`, `unit`, `price`, `status` are `NOT NULL`; `description` and `category_id` are nullable.
- **Category relationship is one-category-per-product, not many-to-many**: `products.category_id` is a plain nullable FK, never a join table — CLAUDE.md doesn't specify cardinality, and a simple optional single-category model is the minimal design that satisfies "Product categories" without inventing a hierarchy or multi-category assignment nothing in this phase calls for. `ProductUpdateRequest.clearCategory` correctly takes precedence over a simultaneously-supplied `categoryId` (caught as a test-coverage gap in security review — the logic was already correct, but untested for this exact interaction; two regression tests were added: `updateGivesClearCategoryPrecedenceOverASimultaneouslySuppliedCategoryId` and `updateWithNeitherCategoryIdNorClearCategoryLeavesAnExistingCategoryUnchanged`).
- **Category deletion is `ON DELETE SET NULL`, not `RESTRICT` or `CASCADE`**: deleting a category never blocks on, or cascades to, referencing products — it only clears their `category_id`. This is the least-destructive choice for a lightweight metadata entity with no independent business significance of its own once removed. `ProductCategoryService.delete()` performs a genuine hard delete (no status/lifecycle field was added to categories — CLAUDE.md requires "Active/inactive status" only for products, §13).
- **SKU normalization and uniqueness**: SKU is uppercased in `ProductService.normalizeSku` before every persist/comparison — an implementation decision (CLAUDE.md doesn't define SKU format), chosen for consistency with barcode/scan contexts where case is immaterial. `ux_products_org_sku` is a plain (non-partial) unique index on `(organization_id, sku)` — organization-scoped, not global, so the same SKU may legitimately belong to products of two different organizations. Backed by an application-level pre-check (`existsByOrganizationIdAndSkuIgnoreCase`) plus a `DataIntegrityViolationException` fallback for the race condition, mirroring the pattern established for `customers.email` (Phase 7).
- **Status has only two values, no archive state**: CLAUDE.md §13 explicitly gives "Active/inactive status" (unlike Customer/Lead, where the status model itself was an implementation decision). Unlike Customer/Lead, there is **no separate archive/lifecycle field** — `ProductStatus.INACTIVE` already represents "intentionally unavailable," and `ProductService.delete()` simply transitions a product to `INACTIVE` (idempotent, fully reversible via a subsequent update), rather than introducing a redundant second lifecycle column. A consequence: unlike archived customers/leads, an `INACTIVE` product is **not excluded from default listing** and **can still be freely updated/reactivated** — documented explicitly since it's a deliberate divergence from the Phase 7/8 archive convention, not an inconsistency.
- **Price/tax precision**: `price NUMERIC(19,4)`, `tax_percentage NUMERIC(5,2)` — never floating point (docs/database.md §2). Price must be `>= 0` (zero allowed — free products/services are legitimate); tax percentage must be `0–100` inclusive, defaulting to `0` if omitted at creation (a reasonable default rather than forcing every creation call to specify a rate, since tax-exempt products are a legitimate case).
- **Unit is free text, not an enum**: CLAUDE.md doesn't define whether "Unit" is constrained — free text (e.g. "pcs", "kg", "box") was chosen since businesses use varied, unpredictable units; a hard-coded enum would be too restrictive.
- **Indexes**: `ix_products_organization_id` (tenant scoping), `ix_products_organization_id_status` (status filtering — no "excluded by default" semantics here, unlike customers/leads, since `INACTIVE` isn't an archive state), `ix_products_category_id` (category filtering and the FK), `ux_products_org_sku` (uniqueness + lookup), `ix_product_categories_organization_id` (tenant scoping for categories). No trigram/full-text index for the free-text `q` search, consistent with Phases 7–8's reasoning.

## 0g. Implementation Notes (Phase 10)

- `V8__create_quotations.sql` adds `quotations` and `quotation_items` — both **explicitly named** in the CLAUDE.md §6 initial-entity list. No RBAC changes: `QUOTATION_READ`/`CREATE`/`UPDATE`/`DELETE` were already seeded by V4 (Phase 6) with the correct role mapping already in place — confirmed by direct inspection before writing any code.
- **`quotations.customer_id` and `quotation_items.product_id` are the first foreign keys in this project pointing across two different business modules** (`crm.entity.Customer`, `products.entity.Product`) from a single entity. Modeled as ordinary JPA `@ManyToOne` relationships — the same established pattern as `identity.entity.User` referencing `organization.entity.Organization`, not a module-boundary violation, since it's a genuine data relationship rather than one module reaching into another's repository for business logic.
- **A plain FK cannot enforce cross-tenant integrity.** `customer_id REFERENCES customers(id)` / `product_id REFERENCES products(id)` only guarantee the referenced row exists *somewhere* — `organization_id` is not part of either referenced primary key, so nothing at the database level stops a quotation in Organization A from referencing a customer/product belonging to Organization B. This is why `sales.service.QuotationService` validates *both* references at the application layer via the tenant-safe `findByIdAndOrganizationId` pattern before ever persisting anything — see `V8`'s own migration comments and [security.md](security.md) §3e for the full reasoning. This is an explicit, deliberate limitation of FK-based integrity, not an oversight.
- **Backend-calculated, persisted totals**: `subtotal`, `discount_amount`, `tax_amount`, `grand_total` on `quotations` (and `line_subtotal`/`line_tax_amount` on `quotation_items`) are always computed by `sales.service.QuotationCalculator` from the authoritative line items and `discount_percentage` — never accepted from the client (CLAUDE.md §14/§41). They are persisted columns, not read-time-derived values, so a quotation's historical totals remain stable and directly queryable even if the calculation logic is refined in a later phase.
- **Monetary precision**: `NUMERIC(19,4)` for all money fields (matching `products.price`, Phase 9), `NUMERIC(5,2)` for `discount_percentage`/`tax_percentage` (matching `products.tax_percentage`). `RoundingMode.HALF_UP` applied consistently — see `QuotationCalculator`'s Javadoc for the full discount-then-tax, per-line-allocation formula.
- **Product snapshot rule**: `quotation_items.product_name_snapshot`/`unit_price`/`tax_percentage` are copied from the referenced product at item-creation/replacement time and never re-read from it afterward (`unit_price`/`tax_percentage` are even marked non-updatable at the JPA level) — a later catalog price change must never silently alter an existing quotation's historical values. `quantity` is a `NUMERIC(19,4)` (a `BigDecimal` in Java, not an integer) — an implementation decision, since CLAUDE.md places no constraint on it and `products.unit` is free text (e.g. "kg", "litre"), so fractional quantities are a legitimate real-world case.
- **No archive field — "delete" is a status transition.** Unlike Customer/Lead/Product, `quotations` has no separate lifecycle column: `QuotationStatus` (the fixed CLAUDE.md §14 6-value enum) already contains `CANCELLED`, so the cancel operation (`DELETE /api/v1/quotations/{id}`) simply transitions to that existing value — idempotent, and financial history is never physically deleted.
- **`valid_until` is a nullable `DATE`** (not `TIMESTAMPTZ`) — gives the `EXPIRED` status operational meaning (CLAUDE.md §14 names the status but no supporting field) without any scheduled/automatic transition logic, which this phase deliberately does not build.
- **No `organization_id` column on `quotation_items`** — same "child of a tenant-scoped parent" precedent as `customer_activities`/`lead_activities`: every access path resolves the parent `Quotation` via an organization-scoped lookup first.
- **Items are managed as a single aggregate, not independent CRUD resources**: `Quotation.items` is a `@OneToMany(cascade = ALL, orphanRemoval = true)` collection: replacing a quotation's items on update clears and re-populates the whole collection in one transaction, rather than exposing item-level add/remove/modify endpoints — the simplest design satisfying "creating/updating a quotation manages its items transactionally."
- **Lazy-collection/pagination trade-off**: `Quotation.items` is `FetchType.LAZY`, and `spring.jpa.open-in-view=false` means it can't be read after the transactional service method returns unless eagerly joined. A `JOIN FETCH` combined with `Pageable` is a well-known JPA trap (Hibernate paginates in-memory, silently returning wrong page sizes), so the paginated list endpoint deliberately never fetches items at all (returning `QuotationSummaryResponse`, without an item list) while the single-resource endpoints (`get`/`create`/`update`) use a dedicated `findByIdAndOrganizationIdWithItems` query (a `LEFT JOIN FETCH`, safe for a single non-paginated row) — see `QuotationRepository`'s Javadoc.
- **Indexes**: `ix_quotations_organization_id` (tenant scoping), `ix_quotations_organization_id_status` (status filtering), `ix_quotations_customer_id` (customer filtering and the FK), `ix_quotations_valid_until` (the validity-date filter), `ix_quotation_items_quotation_id`/`ix_quotation_items_product_id` (item lookups and the FK).

## 0h. Implementation Notes (Phase 11)

- `V9__create_invoices.sql` adds `invoices` and `invoice_items` — both **explicitly named** in the CLAUDE.md §6 initial-entity list — plus `INVOICE_READ`/`CREATE`/`UPDATE`/`DELETE`, which did **not** already exist (unlike Quotation's permissions in Phase 10). Follows the exact Phase 9/V7 pattern for adding new permission rows/role mappings without touching V4's existing rows.
- **Structurally near-identical to `quotations`/`quotation_items`** (Phase 10): same cross-module FK pattern to `crm.Customer`/`products.Product`, same FK-insufficiency-for-tenant-isolation caveat (enforced at the application layer in `InvoiceService`, identically to `QuotationService`), same backend-calculated-and-persisted totals, same product-snapshot rule (`unit_price`/`tax_percentage` non-updatable at the JPA level), same "no `organization_id` on the item table" precedent, same lazy-collection/pagination trade-off (`findByIdAndOrganizationIdWithItems` for single-resource reads, a non-fetching `search` query for the paginated list).
- **Two confirmed, deliberate differences from `quotations`**:
  - **No discount columns** (`discount_percentage`/`discount_amount`) — CLAUDE.md §15 never mentions an invoice discount, unlike §14 for quotations. `total = subtotal + tax_amount` only.
  - **No `quotation_id` column** — CLAUDE.md never describes converting a quotation into an invoice anywhere in the document; invoices are created independently, with their own customer and items.
- **Immutability is enforced at the service layer, not the schema.** There is no database constraint preventing an `UPDATE` on a non-`DRAFT` invoice's columns — Postgres has no way to express "this row's mutability depends on the value of its own `status` column" cleanly across the whole row. `sales.service.InvoiceService.update` is the single enforcement point: it rejects the entire request unless `status == 'DRAFT'`. See [security.md](security.md) §3h for the full reasoning behind reading CLAUDE.md's genuinely ambiguous wording this strictly.
- **Cancellation is financially inert.** `InvoiceService.cancel` sets `status = 'CANCELLED'` and saves — it never recalculates `subtotal`/`tax_amount`/`total`, so a cancelled invoice's persisted totals are exactly what they were the instant before cancellation.
- **Indexes**: `ix_invoices_organization_id`, `ix_invoices_organization_id_status`, `ix_invoices_customer_id`, `ix_invoices_due_date`, `ix_invoice_items_invoice_id`, `ix_invoice_items_product_id` — mirrors `quotations`' index set with `due_date` replacing `valid_until`.

## 0i. Implementation Notes (Phase 12)

- `V10__create_tasks.sql` adds a single `tasks` table — **explicitly named** in the CLAUDE.md §6 initial-entity list, with no accompanying `task_activities`/`task_comments`/`task_history` table (CLAUDE.md §23 lists a single "Notes" feature bullet, unlike `leads`/`customers`, which each separately list an "activities" bullet) — plus `TASK_READ`/`CREATE`/`UPDATE`/`DELETE`, which did not already exist. Follows the exact Phase 9/V7/V9 pattern for adding new permission rows/role mappings without touching V4/V7/V9's existing rows.
- **`assigned_to_user_id`, `customer_id`, and `lead_id` are all plain UUID columns with DB-level FKs, not JPA relationships** — mirroring `leads.assigned_to_user_id` (Phase 8, V6) exactly, extended here to all three of a Task's references. `tasks` has no JPA `@ManyToOne` beyond `organization_id`, unlike every other business table introduced so far.
- **The same FK-insufficiency-for-tenant-isolation caveat applies to all three references** (same reasoning as `quotations.customer_id`/`quotation_items.product_id`, Phase 10): a plain FK only guarantees the referenced row exists *somewhere*; cross-tenant integrity is enforced at the application layer in `tasks.service.TaskService` via the tenant-safe `findByIdAndOrganizationId` pattern for each of the three.
- **No immutability at the schema OR service layer** — unlike `invoices` (Phase 11), there is no status-gated rejection anywhere in `TaskService.update`: a task's row can be freely updated regardless of its current `status` value, including moving `CANCELLED`/`COMPLETED` back to `TODO`/`IN_PROGRESS` (an explicit, approved Phase 12 decision).
- **Non-default RBAC role mapping** (see docs/security.md §2g): unlike every prior resource's "SALES minus delete, EMPLOYEE read-only" default, `TASK_CREATE`/`TASK_UPDATE` are granted to EMPLOYEE and SALES as well — only `TASK_DELETE` is restricted to OWNER/ADMIN/MANAGER.
- **Indexes**: `ix_tasks_organization_id`, `ix_tasks_organization_id_status`, `ix_tasks_assigned_to_user_id`, `ix_tasks_customer_id`, `ix_tasks_lead_id`, `ix_tasks_due_date`.

## 0j. Implementation Notes (Phase 13)

- `V11__create_documents.sql` adds a single `documents` table — **explicitly named** in the CLAUDE.md §6 initial-entity list — plus `DOCUMENT_DELETE`, the only new permission (`DOCUMENT_READ`/`DOCUMENT_UPLOAD` already existed since V4/Phase 6 and are untouched). No `document_versions`/`document_tags`/`document_comments`/`document_history` table — none required by CLAUDE.md §16, and no association table to any other business entity, since §16 defines none (unlike `tasks`, which references `customers`/`leads`).
- **Binary content is never stored in Postgres** (CLAUDE.md §16, explicit prohibition) — `storage_key` is an opaque, server-generated locator (`organizations/{organizationId}/documents/{uuid}`) resolved through `documents.service.DocumentStorageService`, which in Phase 13 only has a local-filesystem implementation. `storage_key` is never derived from `original_filename` and carries a unique index (`ux_documents_storage_key`) since it's the sole identifier used to locate the physical file.
- **`content_type` is a closed `CHECK` constraint of exactly 3 values** — PDF/TXT/DOCX (CLAUDE.md §16's "Supported initial formats"), enforced identically at the entity/service layer by `DocumentValidator`.
- **`file_size` has both a lower and upper `CHECK` bound** (`> 0 AND <= 20971520`, i.e. 20 MB) — the same 20 MB ceiling is enforced first and primarily by `spring.servlet.multipart.max-file-size`/`max-request-size` (application.yml), with this `CHECK` and `DocumentValidator.MAX_FILE_SIZE_BYTES` as defense-in-depth for any code path that bypasses the servlet-level multipart resolver.
- **`uploaded_by_user_id` is a plain UUID column**, not a JPA relationship — the same attribution-style-reference pattern as `tasks.assigned_to_user_id` (Phase 12)/`leads.assigned_to_user_id` (Phase 8); it is never used for authorization, only display.
- **First hard delete**: unlike every prior "delete" (a status transition to `CANCELLED`/`ARCHIVED`), deleting a document physically removes both the stored file and the database row — CLAUDE.md §16 gives documents no terminal status value to transition to instead, and the approved Phase 13 decision explicitly rules out a soft-delete flag. Storage deletion happens before the database row deletion (the reverse of the upload ordering) specifically so a database-delete failure after a successful storage delete is self-healing on retry (`DocumentStorageService.delete` is idempotent) — see [security.md](security.md) §3k for the full ordering rationale on both upload and delete.
- **Indexes**: `ux_documents_storage_key` (unique), `ix_documents_organization_id`, `ix_documents_organization_id_status`, `ix_documents_uploaded_by_user_id`, `ix_documents_content_type`.

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
| `leads` ✅ (Phase 8) | Sales leads, with status/source/priority — see §0e. No Lead→Customer relationship (deliberately not implemented — see §0e). |
| `lead_activities` ✅ (Phase 8) | Backs "activities"/"notes"/"history" (CLAUDE.md §11) in one table — see §0e. |
| `products` ✅ (Phase 9) | Product catalog items — see §0f. |
| `product_categories` ✅ (Phase 9) | Product categorization, one-per-product (not many-to-many) — see §0f. |
| `quotations` ✅ (Phase 10) | Quotations issued to customers — see §0g. |
| `quotation_items` ✅ (Phase 10) | Line items on a quotation, snapshotting product name/price/tax — see §0g. |
| `invoices` ✅ (Phase 11) | Invoices issued to customers — no discount, no quotation reference (see §0h). |
| `invoice_items` ✅ (Phase 11) | Line items on an invoice, snapshotting product name/price/tax — see §0h. |
| `tasks` ✅ (Phase 12) | Task management records, optionally linked to a customer/lead — see §0i. |
| `documents` ✅ (Phase 13) | Uploaded document metadata and processing status — no business-entity associations (see §0j). |
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
organizations 1──* tasks
organizations 1──* documents

users *──* roles (via user_roles)
roles *──* permissions

customers 1──* customer_activities
leads 1──* lead_activities
-- No leads <-> customers relationship (deliberately not implemented — see §0e)

product_categories 1──* products (optional — a product may have no category)

customers 1──* quotations
quotations 1──* quotation_items
quotation_items *──1 products

customers 1──* invoices
invoices 1──* invoice_items
invoice_items *──1 products
-- No quotations <-> invoices relationship (deliberately not implemented — see §0h)

-- tasks.customer_id / tasks.lead_id / tasks.assigned_to_user_id are plain
-- UUID FK columns, not JPA relationships (see §0i) — shown here as
-- optional references, not owned collections, since a customer/lead/user
-- has no back-reference to its tasks.
tasks *──1 organizations
tasks *──0..1 customers (optional)
tasks *──0..1 leads (optional)
tasks *──0..1 users (optional, via assigned_to_user_id)

-- documents has NO business-entity association at all (CLAUDE.md §16 names
-- none — unlike tasks — see §0j); uploaded_by_user_id is a plain UUID
-- attribution reference, same pattern as tasks.assigned_to_user_id.
documents *──1 organizations
documents *──0..1 users (optional, via uploaded_by_user_id)
documents 1──* document_chunks -- Phase 15 (RAG) — not created in Phase 13

conversations 1──* conversation_messages
```

## 5. Data Access Rules

- Entities are **never** exposed directly through REST APIs — DTOs and mappers are mandatory at every controller boundary.
- All repository queries touching tenant-scoped tables must filter by `organization_id`; this is treated as a security control, not just a data-modeling detail (see [security.md](security.md)).
- Vector similarity queries against `document_chunks` must always be constrained to the requesting organization's documents.
