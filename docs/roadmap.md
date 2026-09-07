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
| 7 | Customers | [ ] |
| 8 | Leads | [ ] |
| 9 | Products | [ ] |
| 10 | Quotations | [ ] |
| 11 | Invoices | [ ] |
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
