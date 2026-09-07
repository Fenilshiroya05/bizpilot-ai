# Development Roadmap

This roadmap mirrors the phased plan defined in [CLAUDE.md](../CLAUDE.md) (`# 45. DEVELOPMENT PHASES`). Each phase is completed — implemented, tested, security-reviewed, documented, and reported — before the next one begins. No phase is skipped or merged into another.

Status legend: `[x]` complete · `[ ]` not started.

| Phase | Scope | Status |
|---|---|---|
| 1 | Repository structure + documentation + CLAUDE.md | [x] |
| 2 | Backend foundation | [x] |
| 3 | Database + Flyway | [x] |
| 4 | Authentication | [x] |
| 5 | Organizations + multi-tenancy | [x] |
| 6 | RBAC | [x] |
| 7 | Customers | [x] |
| 8 | Leads | [x] |
| 9 | Products | [x] |
| 10 | Quotations | [x] |
| 11 | Invoices | [x] |
| 12 | Tasks | [ ] |
| 13 | Document management | [ ] |
| 14 | Spring AI foundation | [ ] |
| 15 | RAG + PGVector | [ ] |
| 16 | AI tool calling | [ ] |
| 17 | AI assistant | [ ] |
| 18 | AI lead scoring | [ ] |
| 19 | Analytics | [ ] |
| 20 | Frontend foundation | [ ] |
| 21 | Frontend authentication | [ ] |
| 22 | CRM UI | [ ] |
| 23 | Sales UI | [ ] |
| 24 | Document UI | [ ] |
| 25 | AI assistant UI | [ ] |
| 26 | Dashboard | [ ] |
| 27 | Testing | [ ] |
| 28 | Docker | [ ] |
| 29 | CI/CD | [ ] |
| 30 | Production hardening | [ ] |
| 31 | Deployment | [ ] |

## Phase 1 — Completed Scope

- Repository directory structure for `backend/`, `frontend/`, and `infrastructure/` (empty module folders, no code).
- `README.md` describing the product, planned features, architecture, stack, and setup process.
- `docs/architecture.md`, `docs/database.md`, `docs/ai-architecture.md`, `docs/security.md`, `docs/deployment.md`.
- `.gitignore` covering Java/Maven, Node, environment files, IDE, and OS artifacts.
- `.env.example` listing all planned configuration variables with placeholder values.
- Baseline `docker-compose.yml` for local infrastructure (PostgreSQL/PGVector, Redis, Kafka) with backend/frontend placeholders.
- This roadmap document.

No business logic, entities, controllers, services, or UI components were created in Phase 1, per the project's incremental-build rule (`CLAUDE.md § 2`).

## Phase 2 — Completed Scope

- Spring Boot application bootstrap (`com.bizpilot.BizPilotApplication`), Maven build (`backend/pom.xml`, `./mvnw`), environment-driven configuration (`application.yml`, `local`/`test` profiles).
- Actuator `health` endpoint only, no sensitive details exposed.
- Centralized exception handling foundation (`common/exception/GlobalExceptionHandler`, `common/response/ApiError`) implementing the error contract in `docs/security.md`.
- Test foundation (`BizPilotApplicationTests`, `HealthEndpointTests`) and a working multi-stage `backend/Dockerfile`, wired into `docker-compose.yml`.

No datasource, security, or business-module code was added in Phase 2.

## Phase 3 — Completed Scope

- PostgreSQL datasource, JPA/Hibernate, and Flyway wired via environment variables (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`), `ddl-auto=validate` (Flyway is the sole schema authority).
- `backend/src/main/resources/db/migration/V1__initial_schema.sql` — enables the `vector` (PGVector) extension only; no business tables.
- `common/persistence/BaseEntity` (UUID id + `created_at`/`updated_at` auditing) and `common/config/JpaConfig` (`@EnableJpaAuditing`), reusable by every future entity.
- `docker-compose.yml` backend service now depends on `postgres` being healthy and receives `DB_*` configuration.
- Testcontainers-based test foundation (`TestcontainersConfiguration`, real `pgvector/pgvector:pg16` container) and a `BaseEntityPersistenceTest` proving id generation and auditing timestamps through a real persistence round-trip.
- `.env.example` and `docs/database.md` updated to match the `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` convention.

No authentication, organizations/multi-tenancy schema, RBAC, or other business tables were added in Phase 3 — those belong to Phase 4 onward.

## Phase 4 — Completed Scope

- `identity` module: `User` entity (+ `UserRole`, `UserStatus` enums), `UserRepository`, `UserService` (registration), `UserResponse`/`UserMapper`.
- `security` module: JWT issuance/validation (`JwtService`, `JwtProperties`), stateless bearer-token `SecurityConfig` + `JwtAuthenticationFilter`, refresh-token rotation/reuse-detection (`RefreshTokenService`, `RefreshToken` entity), `CurrentUserProvider`, REST auth entry point/access-denied handler producing the standard `ApiError` shape.
- Endpoints: `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `GET /api/v1/auth/me`.
- `V2__create_users_and_refresh_tokens.sql` — `users` and `refresh_tokens` tables.
- RBAC foundation: single `role` column per user (OWNER/ADMIN/MANAGER/SALES/EMPLOYEE), `@EnableMethodSecurity` + `@PreAuthorize` verified working end-to-end. Full granular permission catalog remains Phase 6.
- 38 backend tests passing (unit + Testcontainers-backed integration), covering registration, login (including enumeration/status-leakage resistance), token validation, refresh rotation + reuse detection, logout, and role-based authorization.
- Fixed a pre-existing gap surfaced during validation: unmapped routes now return `404` instead of `500` (`GlobalExceptionHandler`).

No organizations/multi-tenancy schema, granular RBAC permissions, or other business tables were added in Phase 4. Email verification and forgot/reset-password (CLAUDE.md §8) are acknowledged but not yet implemented — deferred to a later authentication pass (see `docs/security.md §1a`).

## Phase 5 — Completed Scope

- `organization` module: `Organization` entity (`name` only — CLAUDE.md specifies no field list), `OrganizationRepository`, `OrganizationService` (`create`, `getCurrentOrganization`), `OrganizationResponse`/`OrganizationMapper`, `OrganizationController`, and `TenantContext` (the single reusable current-organization resolution mechanism).
- `identity.User` gained a mandatory `organization` relationship (`organization_id` NOT NULL); `security.AuthService.register()` auto-provisions a new organization per signup (`RegisterRequest.organizationName`).
- JWT access tokens now carry an `orgId` claim; `security.UserPrincipal`/`JwtAuthenticationFilter` updated accordingly — tenant resolution still requires no database lookup per request.
- Endpoint: `GET /api/v1/organizations/current` — the only organization-facing endpoint, resolves strictly from the JWT, accepts no organization id from the client in any form.
- `V3__create_organizations_and_link_users.sql` — `organizations` table + `users.organization_id` (NOT NULL FK, indexed).
- 9 new tests (2 `TenantContextTest`, 3 `OrganizationServiceTest`, 4 `TenantIsolationTests`) plus updates to existing Phase 4 tests for the new JWT claim/constructor signatures — 51 backend tests passing total. `TenantIsolationTests` proves the two mandatory scenarios: cross-tenant data is never returned, and a client-supplied organization id (via header and query parameter) never overrides the authenticated tenant — verified both via automated tests and manual `docker-compose` end-to-end validation.
- No generic `TenantScopedEntity` base class was introduced — `User` is still the only entity needing `organization_id`; that abstraction is deferred until a second entity actually needs it (Phase 7+), per the project's "don't over-engineer a generic multi-tenancy framework" guidance.

No RBAC/roles/permissions, business entities (customers/leads/products/etc.), or frontend work were added in Phase 5 — those belong to Phase 6 onward.

## Phase 6 — Completed Scope

- `identity` module gains `Role`/`Permission` entities (`@ManyToMany` to each other via `role_permissions`) and `RoleRepository`/`PermissionRepository`. `User.roles` replaces the Phase 4 single `role` column with a `@ManyToMany` set (via `user_roles`).
- `V4__create_rbac_model.sql` — `roles`, `permissions`, `user_roles`, `role_permissions` tables (composite-PK junction tables, no `BaseEntity`); seeds the fixed 5-role/16-permission catalog from CLAUDE.md §9; seeds a role→permission mapping (an implementation decision, since CLAUDE.md doesn't specify one — documented in `docs/security.md`); migrates every existing user's `users.role` value into `user_roles`; drops `users.role` — one authoritative source of role assignment at all times, never two side by side.
- JWT access tokens now carry an `authorities` claim (replacing the Phase 4/5 `role` claim) — resolved fresh from the DB at every login/refresh (`security.AuthService.resolveAuthorities`), combining `ROLE_<name>` per assigned role plus each role's raw permission names. `JwtAuthenticationFilter` builds request authorities directly from this claim — no database lookup per authenticated request, preserving the no-DB-hit-per-request design.
- Authorization enforced via Spring Security method security: `@PreAuthorize("hasAuthority('...')")` for permission-based checks (business operations), `@PreAuthorize("hasRole('...')")` still available for role-based checks (admin operations) — both already proven working end-to-end in Phase 4, now extended to the full permission catalog.
- No role/permission-assignment REST API was added — CLAUDE.md doesn't assign one to Phase 6; role changes happen via the Flyway seed data or direct DB manipulation only.
- Mandatory security tests: `RoleSeedDataTests` (7 tests verifying the seeded catalog and role→permission mapping exactly), `RbacAuthorizationTests` (7 tests: permission-gated endpoint allow/forbid/unauthenticated, tenant+RBAC combined scenario proving a shared permission never leaks cross-org data, JWT authorities-claim tampering rejected via signature validation, role grant/revoke taking effect only on refresh — never on an already-issued token).
- Existing Phase 4 authentication tests and Phase 5 tenant-isolation tests updated for the new `User`/`UserPrincipal`/`JwtService` signatures and continue to pass unmodified in behavior — full regression suite green.
- Security review (background subagent, scoped to the full Phase 6 diff): no CRITICAL or HIGH findings; merge-ready. One MEDIUM noted (potential N+1 when resolving permissions for a user with more than one role — not reachable today, since no code path assigns multiple roles; worth revisiting once a real role-assignment feature exists) and two LOW code-smell notes (a repository method taking a raw `String` instead of a constrained type; an unchecked cast in JWT claim extraction, already safely handled by the existing malformed-claim catch path) — both deferred as non-blocking.

No business entities (customers/leads/products/etc.), role/permission-assignment API, or frontend work were added in Phase 6 — those belong to Phase 7 onward.

## Phase 7 — Completed Scope

- `crm` module populated for the first time: `entity/Customer`, `entity/CustomerStatus` (ACTIVE/INACTIVE/ARCHIVED — CLAUDE.md doesn't define values, a documented implementation decision), `entity/CustomerActivity`, `entity/CustomerActivityType` (CREATED/STATUS_CHANGED/ARCHIVED/NOTE), repositories, DTOs, mappers, `CustomerService`, `CustomerController`, and dedicated exceptions (`CustomerNotFoundException`, `DuplicateCustomerException`, `CustomerArchivedException`, `InvalidCustomerDataException`).
- `V5__create_customers.sql` — `customers` (fields exactly per CLAUDE.md §10: name, company, email, phone, address, gstin, status, notes) and `customer_activities` (one table backing "activities"/"notes"/"history" via a `type` discriminator, not three separate tables or a generic audit system). Organization-scoped partial-unique email index; no `organization_id` on `customer_activities` — it inherits tenant safety from its parent `Customer` (same precedent as `refresh_tokens`).
- Endpoints under `/api/v1/customers`: create, get, update (PATCH, partial), archive (`DELETE`, soft-delete only), list/search/filter/paginate, add note, list notes, list activities, list history — 9 endpoints, matching the minimum set required. Every endpoint requires the matching Phase 6 `CUSTOMER_*` permission; no new roles/permissions were introduced.
- Archive is a soft-delete (`status = ARCHIVED`) reachable only through the dedicated archive endpoint — never through the general update endpoint (explicitly rejected with `400 INVALID_CUSTOMER_DATA`); archived customers reject further modification (`409 CUSTOMER_ARCHIVED`) and are excluded from default listing unless explicitly filtered for.
- `GlobalExceptionHandler` gained handlers for the four new exceptions plus `HttpMessageNotReadableException` (fixes malformed JSON/invalid enum values that previously would have fallen through to a generic `500`).
- Mandatory tests: `CustomerServiceTest` (14 Mockito unit tests), `CustomerApiTests` (20 full-context CRUD/validation/search/pagination/activity tests), `CustomerTenantIsolationTests` (10 tests — cross-org read/update/archive/activities/notes/history all fail as 404, plus body/query/header organization-id-smuggling attempts), `CustomerAuthorizationTests` (5 tests — unauthenticated/EMPLOYEE/SALES/MANAGER permission boundaries) — 49 new tests. Full suite: 116 backend tests passing, confirming no regression to Phases 1–6.
- Manual `docker-compose` end-to-end validation: all 5 Flyway migrations apply cleanly against a fresh Postgres; create/list/search/filter/notes/activities/history/archive all verified via real HTTP; cross-org access confirmed to 404 (never leaking existence); no secrets in logs.
- Security review (background subagent, scoped to the full Phase 7 diff): no CRITICAL or HIGH findings; merge-ready. One MEDIUM fixed before completion — a previously-set GSTIN could never be cleared via PATCH, because `@Pattern` only special-cases `null`, not the empty string that the PATCH "clear a field" convention relies on (fixed by widening the regex to `^$|<gstin-pattern>` in both `CustomerCreateRequest` and `CustomerUpdateRequest`, plus a regression test). Four LOW items noted and deferred as non-blocking: unescaped `%`/`_` LIKE wildcards in free-text search (cosmetic), no optimistic locking on `Customer` (pre-existing project-wide convention, not introduced here), an activity-then-flush ordering nitpick in `update()`, and no functional index on `LOWER(email)` for defense-in-depth beyond the existing service-layer normalization.

No leads, products, quotations, invoices, tasks, documents, AI/RAG, analytics, dashboard, frontend, generic Audit Logging module, or generic Rate Limiting module were added in Phase 7 — those belong to Phase 8 onward.

## Phase 8 — Completed Scope

- `sales` module populated for the first time: `entity/Lead`, `entity/LeadStatus` (NEW/CONTACTED/QUALIFIED/PROPOSAL/NEGOTIATION/WON/LOST — fixed exactly by CLAUDE.md §11), `entity/LeadSource` (WEBSITE/REFERRAL/SOCIAL_MEDIA/EMAIL/PHONE/OTHER — also fixed by CLAUDE.md §11), `entity/LeadPriority` (LOW/MEDIUM/HIGH — an implementation decision, deliberately not reusing Task's LOW/MEDIUM/HIGH/URGENT set), `entity/LeadActivity`, `entity/LeadActivityType` (CREATED/STATUS_CHANGED/ASSIGNED/ARCHIVED/NOTE), repositories, DTOs, mappers, `LeadService`, `LeadController`, and dedicated exceptions (`LeadNotFoundException`, `LeadArchivedException`, `InvalidAssigneeException`, `InvalidLeadDataException`).
- **Identifying fields deliberately smaller than Customer's**: only `name` (required), `company`, `email`, `phone` — no `address`/`gstin` (not applicable to an unqualified lead) and no `notes` field on the entity itself (unlike Customer) — "Notes" is satisfied entirely by the activity-log feature, avoiding Customer's field-vs-activity duplication.
- **No Lead → Customer relationship** — CLAUDE.md never mentions one; the only prior hint was a non-binding Phase-1 documentation placeholder, now removed from `docs/database.md`.
- **Status and archival are independent**: `LeadStatus` has no archived value (CLAUDE.md permits only the 7 named values), so a separate nullable `archivedAt` timestamp handles record lifecycle — a lead can be `WON`/`LOST` and still not be archived.
- `V6__create_leads.sql` — `leads` and `lead_activities` (both explicitly named in CLAUDE.md §6, unlike Phase 7's `customer_activities`). Organization-scoped indexes on status, archived-at, assignee, and follow-up date.
- **Lead assignment**: a dedicated `POST /api/v1/leads/{id}/assign` action (not a field on the general update DTO, to avoid the "unchanged vs. unassign" ambiguity a nullable PATCH field can't express) — the assignee is validated to belong to the same organization as the lead via a new, minimal, additive `identity.repository.UserRepository.findByIdAndOrganizationId` method. Every assignment/reassignment/unassignment is recorded as a `LeadActivity` (type `ASSIGNED`) rather than a separate assignment-history table — the simplest design satisfying the requirement.
- Endpoints under `/api/v1/leads`: create, get, update (PATCH, partial, with an explicit `clearFollowUpDate` flag to unambiguously clear a set follow-up date), archive (`DELETE`, soft-delete via `archivedAt`, never a status value), assign, list/search/filter/paginate (search text, status, source, priority, assignee, unassigned-only, follow-up-on-or-before, archived-only), add note, list notes, list activities, list history — 10 endpoints, matching the minimum set required. Every endpoint requires the matching Phase 6 `LEAD_*` permission (assignment reuses `LEAD_UPDATE` — no new permission was introduced).
- `GlobalExceptionHandler` gained handlers for the four new Lead exceptions.
- Mandatory tests: `LeadServiceTest` (19 Mockito unit tests), `LeadApiTests` (21 full-context CRUD/validation/search/pagination/assignment/activity tests), `LeadTenantIsolationTests` (12 tests — cross-org read/update/archive/activities/notes/history/assign all fail as 404, cross-org assignee rejected as 400, plus body/query/header organization-id-smuggling attempts), `LeadAuthorizationTests` (4 tests — unauthenticated/EMPLOYEE/SALES/MANAGER permission boundaries) — 56 new tests. Full suite: 172 backend tests passing, confirming no regression to Phases 1–7.
- Manual `docker-compose` end-to-end validation: all 6 Flyway migrations apply cleanly against a fresh Postgres; create/assign/update-status/notes/activities/history/archive all verified via real HTTP, including status (business outcome) and archival (record lifecycle) behaving independently; cross-org lead access and cross-org assignee attempts both correctly rejected; no secrets in logs.
- Security review (background subagent, scoped to the full Phase 8 diff): no CRITICAL, HIGH, or MEDIUM findings; merge-ready. One LOW fixed before completion — `LeadService.assign()` validated assignee organization membership but not account status, meaning a lead could be assigned to a `DISABLED`/`LOCKED` user and silently go unworked (fixed by rejecting non-`ACTIVE` assignees with the same `InvalidAssigneeException`, plus a regression test). Two LOW items noted and deferred as non-blocking: free-text search uses a leading-wildcard `LIKE` that can't use a B-tree index at scale (same pre-existing pattern as Phase 7's Customer search, not a regression), and `lead_activities`'s tenant safety is a code-convention (every call site resolves the org-scoped parent first) rather than DB-enforced — accepted, identical to the Phase 7 `customer_activities` precedent, flagged only as a standing checklist item for future service reviews.

No AI Lead Scoring, Products, Quotations, Invoices, Tasks, Documents, AI/RAG, Analytics, Dashboard, frontend, generic Audit Logging module, generic Rate Limiting module, lead-conversion workflows, or reminders/notifications were added in Phase 8 — those belong to Phase 9 onward (AI Lead Scoring specifically belongs to Phase 18).

## Phase 9 — Completed Scope

- `products` module populated for the first time: `entity/Product`, `entity/ProductCategory`, `entity/ProductStatus` (ACTIVE/INACTIVE — explicitly given by CLAUDE.md §13, no other values), repositories, DTOs, mappers, `ProductService`, `ProductCategoryService`, `ProductController`, `ProductCategoryController`, and dedicated exceptions (`ProductNotFoundException`, `ProductCategoryNotFoundException`, `DuplicateSkuException`, `InvalidProductCategoryException`, `InvalidProductDataException`).
- **First phase to extend the RBAC permission catalog itself**: `V7__create_products.sql` adds `PRODUCT_READ`/`CREATE`/`UPDATE`/`DELETE` to the pre-existing `permissions`/`role_permissions` tables (established by V4) rather than only creating new business tables — CLAUDE.md §9 frames its permission list as "Examples:" (not closed), so this extends rather than violates it. Mapping: OWNER/ADMIN/MANAGER full CRUD; SALES CRUD minus delete; EMPLOYEE read-only — identical philosophy to Customers/Leads. Required updating a pre-existing Phase 6 regression test (`identity.RoleSeedDataTests`) whose hardcoded "full catalog" literal legitimately grew from 16 to 20 permissions.
- **Identifying fields exactly per CLAUDE.md §13**: SKU (required, uppercase-normalized, organization-scoped unique), name (required), description (optional), unit (required, free text), price (`NUMERIC(19,4)`, `>= 0`, zero allowed), tax percentage (`NUMERIC(5,2)`, `0–100`, defaults to `0` if omitted).
- **Product category**: deliberately minimal (name only, no description) — one-per-product via a plain nullable `category_id` FK, never many-to-many. Category references are tenant-validated (`InvalidProductCategoryException`, `400`) exactly the way Lead's assignee reference is validated. Category deletion is `ON DELETE SET NULL` — never blocks on, or cascades to, referencing products.
- **No archive mechanism, unlike Customer/Lead**: CLAUDE.md §13 names no delete/archive feature, only "CRUD" — `DELETE /api/v1/products/{id}` transitions a product to `INACTIVE` (idempotent, fully reversible), and unlike Customer/Lead, an `INACTIVE` product is *not* excluded from default listing and *can* still be freely updated/reactivated.
- Endpoints under `/api/v1/products`: create, get, update (PATCH, partial, with an explicit `clearCategory` flag), delete (soft, via status), list/search/filter/paginate (search text, status, category, unit). Endpoints under `/api/v1/products/categories` (a literal path segment, verified not to conflict with `/api/v1/products/{id}`): create, get, list, update, delete (hard delete). 11 endpoints total, matching the minimum set required. Every endpoint requires the matching Phase 9 `PRODUCT_*` permission.
- `GlobalExceptionHandler` gained handlers for the five new Product/Category exceptions.
- Mandatory tests: `ProductServiceTest` (14 Mockito unit tests), `ProductCategoryServiceTest` (5 unit tests), `ProductApiTests` (23 full-context CRUD/validation/search/pagination/category tests), `ProductTenantIsolationTests` (10 tests — cross-org read/update/delete for both products and categories fail as 404, cross-org category assignment rejected as 400, plus body/query/header organization-id-smuggling attempts), `ProductAuthorizationTests` (4 tests — unauthenticated/EMPLOYEE/SALES/MANAGER permission boundaries) — 56 new tests. Full suite: 228 backend tests passing, confirming no regression to Phases 1–8.
- Manual `docker-compose` end-to-end validation: all 7 Flyway migrations apply cleanly against a fresh Postgres; the actual seeded `role_permissions` rows were inspected directly via `psql` and matched the intended mapping exactly; create/list/filter/category-assign/delete/reactivate all verified via real HTTP; a PostgreSQL "function lower(bytea) does not exist" bug (found during this validation, caused by wrapping a nullable bind parameter — not a column — in a JPQL `LOWER()` call for the `unit` filter) was fixed before completion; cross-org access and cross-org category assignment both correctly rejected; no secrets in logs.
- Security review (background subagent, scoped to the full Phase 9 diff): no CRITICAL, HIGH, or blocking MEDIUM findings; merge-ready. Two MEDIUM test-coverage gaps around the `clearCategory`/`categoryId` interaction were identified (the underlying logic was already correct, just untested for this exact interaction) and **fixed** by adding `updateGivesClearCategoryPrecedenceOverASimultaneouslySuppliedCategoryId` and `updateWithNeitherCategoryIdNorClearCategoryLeavesAnExistingCategoryUnchanged`. Several LOW items noted and deferred as non-blocking, all pre-existing across the codebase rather than introduced by Phase 9: no handler for `MethodArgumentTypeMismatchException` on non-UUID path segments (falls through to a generic 500 — identical gap exists for Customers/Leads), no configured max page size, and JPA entity `@Column` annotations omitting explicit `length` (harmless today since Bean Validation is the only write path, but a latent trap for a future non-DTO creation path).

No Quotations, Invoices, Tasks, Documents, AI/RAG, Analytics, Dashboard, frontend, generic Audit Logging module, generic Rate Limiting module, inventory/stock/warehouse/supplier/purchase management, a discount engine, or any product-to-quotation/invoice foreign key were added in Phase 9 — those belong to Phase 10 onward.

## Phase 10 — Completed Scope

- `sales` module gains `entity/Quotation`, `entity/QuotationItem`, `entity/QuotationStatus` (DRAFT/SENT/ACCEPTED/REJECTED/EXPIRED/CANCELLED — fixed exactly by CLAUDE.md §14), repositories, DTOs, mappers, `QuotationService`, `QuotationCalculator` (a pure, dependency-free financial calculation class), `QuotationPdfService`, `QuotationController`, and dedicated exceptions (`QuotationNotFoundException`, `InvalidCustomerReferenceException`, `InvalidProductReferenceException`, `InvalidQuotationDataException`).
- **No RBAC migration** — `QUOTATION_READ`/`CREATE`/`UPDATE`/`DELETE` already existed from Phase 6 (V4), unlike Phase 9's `PRODUCT_*`. Confirmed directly against the database before implementation began.
- **First entity referencing two other business modules**: `Quotation.customer` (`crm.entity.Customer`) and `QuotationItem.product` (`products.entity.Product`), both validated tenant-safe before being persisted — a plain FK alone cannot enforce this, since `organization_id` isn't part of either referenced primary key (documented explicitly in the migration and `docs/database.md`/`docs/security.md`).
- **Backend-authoritative financial calculation** (CLAUDE.md §14/§41): `subtotal = Σ(quantity × unitPrice)`, discount applied per line (same percentage as the aggregate, for deterministic allocation), tax calculated on the discounted line amount, `grandTotal = taxableAmount + taxAmount`. `NUMERIC(19,4)`/`RoundingMode.HALF_UP` throughout, reusing Phase 9's precision convention. Request DTOs have no total fields at all — nothing for a client to submit, verified by a test sending spoofed `subtotal`/`discountAmount`/`taxAmount`/`grandTotal` values in a raw JSON body and confirming they're silently ignored.
- **Product snapshot rule**: `QuotationItem` captures `productNameSnapshot`/`unitPrice`/`taxPercentage` from the product at creation/replacement time (the latter two marked non-updatable at the JPA level) — a later catalog price change never alters an existing quotation's historical totals.
- **Delete/cancel reuses the existing `CANCELLED` status** — no separate archive field, unlike Customer/Lead, since CLAUDE.md's own 6-value status enum already expresses it.
- **PDF generation** (CLAUDE.md §14, an unconditional requirement): Apache PDFBox (Apache 2.0 license, minimal transitive dependencies — `pdfbox-io`, `fontbox`, `commons-logging`), generated on demand via `GET /api/v1/quotations/{id}/pdf`, never persisted (no storage abstraction exists until Phase 13).
- Endpoints under `/api/v1/quotations`: create, get, update (PATCH, replaces the entire item collection when items are supplied), cancel (`DELETE`), list/search/filter/paginate (status, customer, valid-until), PDF. List responses omit items (`QuotationSummaryResponse`) to avoid the JPA collection-fetch-join-plus-pagination trap; single-resource responses include full item detail via a dedicated `JOIN FETCH` query.
- Mandatory tests: `QuotationCalculatorTest` (11 pure unit tests covering multi-line sums, discount-before-tax ordering, rounding, decimal quantities, zero-item edge case, aggregate-vs-per-line discount rounding divergence), `QuotationServiceTest` (17 Mockito unit tests, including archived-customer/inactive-product rejection), `QuotationApiTests` (15 full-context CRUD/validation/search/pagination/PDF tests, including the frontend-spoofed-totals test and a non-WinAnsi-character PDF regression test), `QuotationTenantIsolationTests` (11 tests — cross-org read/update/cancel/PDF/listing all fail as 404, cross-org customer/product references fail as 400, plus body/query/header organization-id-smuggling attempts), `QuotationAuthorizationTests` (5 tests — unauthenticated/EMPLOYEE/SALES/MANAGER permission boundaries, including the PDF endpoint) — 59 new tests. Full suite: 287 backend tests passing, confirming no regression to Phases 1–9.
- Manual `docker-compose` end-to-end validation performed (see final report for details).
- A dedicated security/code review found no critical/high issues and two medium findings (PDF crash on non-WinAnsi characters; missing archived-customer/inactive-product reference checks), both fixed before completion — see [security.md §3g](security.md#3g-security-review-findings-phase-10) for details and the three low-severity findings documented as intentionally deferred.

No Invoices, Tasks, Documents, AI/RAG, Analytics, Dashboard, frontend, inventory/stock management, payments, generic Audit Logging module, generic Rate Limiting module, or scheduled/automatic quotation-status transitions were added in Phase 10 — those belong to Phase 11 onward.

## Phase 11 — Completed Scope

- `sales` module gains `entity/Invoice`, `entity/InvoiceItem`, `entity/InvoiceStatus` (DRAFT/ISSUED/PARTIALLY_PAID/PAID/OVERDUE/CANCELLED — fixed exactly by CLAUDE.md §15), repository, DTOs, mappers, `InvoiceService`, `InvoiceCalculator` (pure, dependency-free — mirrors `QuotationCalculator` minus the discount step), `InvoicePdfService`, `InvoiceController`, and dedicated exceptions (`InvoiceNotFoundException`, `InvalidInvoiceDataException`, `InvoiceNotEditableException`) — plus reuse of the existing, generalized `InvalidCustomerReferenceException`/`InvalidProductReferenceException`.
- **New RBAC migration** — unlike Quotation (Phase 10), `INVOICE_READ`/`CREATE`/`UPDATE`/`DELETE` did not already exist in CLAUDE.md's permission catalog; added in `V9` following the exact Phase 9/`PRODUCT_*` precedent (OWNER/ADMIN/MANAGER full CRUD, SALES no delete, EMPLOYEE read-only), without touching V4's existing rows.
- **No discount, no quotation reference** — both explicit, confirmed scope decisions (CLAUDE.md §15 never mentions either): `total = subtotal + taxAmount` only; invoices are created independently, with their own customer and items.
- **Hard immutability rule, the first of its kind in this project**: once an invoice leaves `DRAFT`, `PATCH /api/v1/invoices/{id}` rejects the entire request with `409 CONFLICT` (`INVOICE_NOT_EDITABLE`) — customer, items, due date, and status all become frozen. Chosen as the stricter of two readings of a genuinely ambiguous prompt; documented consequence: no in-phase mechanism exists to progress `ISSUED → PARTIALLY_PAID → PAID` (only to `CANCELLED`, via the separate cancel action), since CLAUDE.md explicitly forbids inventing a payment subsystem and names no such endpoint. See [security.md §3h](security.md#3h-implementation-notes-phase-11--immutability-as-a-data-integrity-boundary-not-just-a-business-preference).
- **Product snapshot rule and tenant-safe reference validation reused verbatim from Phase 10**, including the archived-customer/inactive-product checks added by that phase's own security review — applied to invoices from day one.
- **Cancellation is financially inert**: `DELETE /api/v1/invoices/{id}` transitions to `CANCELLED` (idempotent, no physical delete) without recalculating or otherwise touching `subtotal`/`taxAmount`/`total`.
- **PDF generation**: reuses the existing Apache PDFBox dependency and the Phase 10 WinAnsiEncoding-sanitization fix verbatim — no new dependency.
- Endpoints under `/api/v1/invoices`: create, get, update (PATCH, DRAFT-only), cancel (`DELETE`), list/search/filter/paginate (status, customer, due-date), PDF. Same summary/detail response-shape split as Quotation to avoid the JPA collection-fetch-join-plus-pagination trap.
- Mandatory tests: `InvoiceCalculatorTest` (9 pure unit tests), `InvoiceServiceTest` (22 Mockito unit tests, including a parameterized test rejecting updates across all 5 non-DRAFT statuses), `InvoiceApiTests` (15 full-context CRUD/validation/search/pagination/PDF tests, including the frontend-spoofed-totals test and a non-WinAnsi-character PDF regression test), `InvoiceTenantIsolationTests` (11 tests), `InvoiceAuthorizationTests` (5 tests) — 62 new tests, plus a required update to the pre-existing Phase 6 `RoleSeedDataTests` (its fixed expected-permission-set literals now include `INVOICE_*`, the same kind of update Phase 9 made to it for `PRODUCT_*`). Full suite: 349 backend tests passing, confirming no regression to Phases 1–10.
- Manual `docker-compose` end-to-end validation performed (see final report for details).
- A dedicated security/code review found no critical/high/medium findings — every tenant-isolation, RBAC, mass-assignment, and reference-validation control was reused directly from Phase 10's already-hardened implementation. See [security.md §3i](security.md#3i-security-review-findings-phase-11).

No Payments/payment gateway, Tasks, Documents/storage, AI/RAG, AI assistant/tool calling/lead scoring, Analytics, Dashboard, frontend, inventory, notifications, generic audit logging, generic rate limiting, scheduled jobs, or quotation-to-invoice conversion were added in Phase 11 — those belong to Phase 12 onward (or, for payments, are never in scope per CLAUDE.md).

## Process Per Phase

Each subsequent phase follows the same checklist (`CLAUDE.md § 2`):

1. Understand the existing code.
2. Inspect the project structure.
3. Plan the changes.
4. Implement the changes.
5. Run tests.
6. Fix compilation errors.
7. Fix test failures.
8. Review security implications.
9. Review code quality.
10. Update documentation.
11. Report what was completed.
12. Clearly state the next phase.
