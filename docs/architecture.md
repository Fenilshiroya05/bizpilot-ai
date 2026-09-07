# Architecture

> Status: Phase 8 — the `sales` module is populated with Leads, adding assignment (a cross-tenant-validated reference to a `User`) as a new pattern beyond what Phase 7 (Customers) required. Remaining business modules (`products`, `documents`, `ai`, `analytics`, `tasks`, `notifications`, `audit`) remain empty placeholders until their respective phases. No frontend code exists yet.

## 1a. Backend Foundation (Phase 2)

- Entry point: `com.bizpilot.BizPilotApplication` (`@SpringBootApplication`), positioned at the root package so component scanning covers every module package under `com.bizpilot.*` as they're populated in later phases.
- Configuration: `application.yml` (defaults, env-var driven) with a `local` profile (`application-local.yml`) active by default, and a `test` profile (`application-test.yml`) used by the test suite. No secrets or environment-specific URLs are hard-coded.
- Actuator: only the `health` endpoint is exposed, with `show-details: never` — no internal details leak through `/actuator/health`.
- `common/exception/GlobalExceptionHandler` + `common/response/ApiError` implement the centralized error-response contract defined in [security.md](security.md) (`timestamp`, `status`, `code`, `message`, `path`), with a validation-specific handler and a catch-all handler that logs server-side but never returns stack traces to the client.

## 1b. Database Foundation (Phase 3)

- Datasource, JPA/Hibernate, and Flyway are configured in `application.yml`, driven entirely by `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` environment variables — no defaults for `DB_PASSWORD`.
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate never creates/alters schema; Flyway (`backend/src/main/resources/db/migration/`) is the sole migration authority, per [database.md](database.md).
- `common/persistence/BaseEntity` + `common/config/JpaConfig` provide the UUID id / `created_at` / `updated_at` foundation every future entity builds on.
- Full details, including the deferred tenant-scoped base entity and the Testcontainers-based test setup, are in [database.md](database.md).

## 1c. Authentication & Identity Foundation (Phase 4)

- **`identity` module**: `User` entity (+ `UserRole`, `UserStatus` enums), `UserRepository`, `UserService` (registration business logic), `UserResponse`/`UserMapper` — the domain model for user accounts.
- **`security` module**: JWT issuance/validation (`jwt/JwtService`, `jwt/JwtProperties`), the stateless bearer-token filter chain (`SecurityConfig`, `JwtAuthenticationFilter`), refresh-token lifecycle (`RefreshTokenService`, `entity/RefreshToken`), and the auth REST API (`AuthController`, `AuthService`, `dto/*`).
- Controllers depend on services, services depend on repositories — same layering as every other module (CLAUDE.md §42). `AuthController` → `AuthService` → (`UserService`, `RefreshTokenService`, `JwtService`).
- `security.CurrentUserProvider` is the one place that reads `SecurityContextHolder` — other modules should depend on it rather than touching Spring Security directly (CLAUDE.md's "keep authentication concerns inside the security/identity layer").
- Full detail (token model, rotation/reuse-detection, enumeration resistance) is in [security.md](security.md).

## 1d. Organizations & Multi-Tenancy Foundation (Phase 5)

- **`organization` module**: `Organization` entity (`name` only), `OrganizationRepository`, `OrganizationService` (`create`, `getCurrentOrganization`), `OrganizationResponse`/`OrganizationMapper`, `OrganizationController` (`GET /api/v1/organizations/current` — the only endpoint), and `TenantContext` (the single reusable "what organization is this request for?" mechanism).
- `identity.User` now has a mandatory `organization` relationship (`organization_id`, NOT NULL, set once at creation). `security.AuthService.register()` auto-provisions a new `Organization` per signup, then creates the `User` with it — see `docs/security.md §3a` for why (no invite/join flow exists yet).
- **Cross-module dependency shape**: `identity` → `organization` (registration needs `OrganizationService` to provision a tenant) and `organization` → `security` (`TenantContext` needs `CurrentUserProvider`/`UserPrincipal`). `security` itself has no direct dependency on `organization` — `AuthService` only ever touches `Organization` indirectly, via `User.getOrganization().getId()`. This keeps the layering acyclic in practice even though, at the whole-system level, tenancy and identity are inherently intertwined.
- The JWT now carries an `orgId` claim alongside `sub`/`role`, so `TenantContext` never needs a database lookup to resolve the current tenant, consistent with the no-DB-hit-per-request design from Phase 4.
- Full detail (tenant resolution mechanism, isolation guarantees, the mandatory cross-tenant test scenario) is in [security.md](security.md) and [database.md](database.md).

## 1e. RBAC Foundation (Phase 6)

- **`identity` module** gains `Role`/`Permission` entities and `RoleRepository`/`PermissionRepository`. `User.roles` is now a `@ManyToMany` set (replacing the Phase 4 single `role` column); `Role.permissions` is a `@ManyToMany` set to `Permission`. Both relationships are `FetchType.LAZY`, accessed only inside an enclosing transaction (`open-in-view: false`).
- **No new module was introduced** — RBAC entities live in `identity` (which already owned `User`) rather than a separate `rbac` module; CLAUDE.md's module list has no dedicated RBAC package, and splitting it out would be a module purely for two small entity classes tightly coupled to `User`.
- **Authorization enforcement is Spring Security method security**, not a bespoke authorization service: `@PreAuthorize("hasAuthority('CUSTOMER_READ')")` for permission checks, `@PreAuthorize("hasRole('ADMIN')")` still available for admin-style checks — both read from the same `GrantedAuthority` set built once per request in `JwtAuthenticationFilter`. No separate `AuthorizationService` abstraction was introduced; Spring's own method security already covers every case this phase needs.
- **Authorities are resolved once, at token issuance** (`security.AuthService.resolveAuthorities`), from the authoritative DB state (`user.getRoles()` → `ROLE_<name>` + each role's `getPermissions()` → raw permission name), and embedded in the JWT's `authorities` claim. `JwtAuthenticationFilter` builds the request's `GrantedAuthority` set directly from that claim — zero database lookups per authenticated request, preserving the Phase 4 no-DB-hit-per-request design. This means a role/permission change takes effect only on the next login or `/refresh`, not on already-issued access tokens (bounded by the access token's short TTL) — see [security.md](security.md) for the full staleness rationale.
- **RBAC and tenant resolution are fully independent JWT claims** (`authorities` vs `orgId`) — a permission never provides an alternate path to another organization's data; both checks are required together, never substitutable for each other. Verified in `RbacAuthorizationTests.havingThePermissionNeverGrantsAccessToAnotherOrganizationsData`.
- No role/permission-assignment REST API was added this phase — CLAUDE.md doesn't assign such an endpoint to Phase 6, and adding one would need its own authorization/audit design. Role changes in this phase happen only via direct DB manipulation (as done in tests) or the Flyway seed data.
- Full detail (role/permission catalog, the role→permission mapping decision, privilege-escalation analysis) is in [security.md](security.md); schema detail is in [database.md](database.md).

## 1f. CRM Foundation — Customers (Phase 7)

- **`crm` module** is populated for the first time: `entity/Customer`, `entity/CustomerStatus`, `entity/CustomerActivity`, `entity/CustomerActivityType`, `repository/CustomerRepository`, `repository/CustomerActivityRepository`, `dto/*`, `mapper/CustomerMapper`, `mapper/CustomerActivityMapper`, `service/CustomerService`, `controller/CustomerController`, `exception/*`. Same layering as every other module: `Controller → Service → Repository → Entity`, DTOs/mappers at the boundary.
- **First real consumer of tenant isolation and RBAC together**: every `CustomerService` method resolves the organization via `organization.TenantContext`/`organization.service.OrganizationService` (never from client input) and every by-id lookup uses the tenant-safe `CustomerRepository.findByIdAndOrganizationId`, never a plain `findById`. Every `CustomerController` endpoint carries an explicit `@PreAuthorize("hasAuthority('CUSTOMER_*')")` using the Phase 6 permission catalog — no new roles/permissions were introduced.
- **One table backs three CLAUDE.md §10 features**: `CustomerActivity` (type `CREATED`/`STATUS_CHANGED`/`ARCHIVED`/`NOTE`) backs "activities," "notes," and "history" without a separate table per feature or any project-wide audit/event-sourcing framework — see [database.md](database.md) for the full reasoning, and [security.md](security.md) §7 for why this is explicitly *not* the still-unbuilt, general-purpose Audit Logging capability (CLAUDE.md §24).
- **Cross-module reuse, not duplication**: `CustomerService` obtains the current `Organization` entity via `organization.service.OrganizationService.getCurrentOrganization()` (going through that module's service, per CLAUDE.md §42), rather than reaching into `OrganizationRepository` directly.
- Full detail (status model, archive/soft-delete decision, uniqueness, validation, tenant-isolation/RBAC test coverage) is in [security.md](security.md) and [database.md](database.md).

## 1g. Sales Foundation — Leads (Phase 8)

- **`sales` module** is populated for the first time: `entity/Lead`, `entity/LeadStatus`, `entity/LeadSource`, `entity/LeadPriority`, `entity/LeadActivity`, `entity/LeadActivityType`, `repository/LeadRepository`, `repository/LeadActivityRepository`, `dto/*`, `mapper/LeadMapper`, `mapper/LeadActivityMapper`, `service/LeadService`, `service/LeadSearchCriteria` (an internal filter-bundling record, not a REST DTO), `controller/LeadController`, `exception/*`. Same layering and conventions as `crm` (Phase 7): `Controller → Service → Repository → Entity`, DTOs/mappers at the boundary, tenant-safe `findByIdAndOrganizationId` lookups everywhere, `@PreAuthorize("hasAuthority('LEAD_*')")` on every endpoint using the Phase 6 permission catalog.
- **New pattern beyond Phase 7: a cross-tenant-validated relationship to another entity.** Lead assignment references a `User` (via a plain `assignedToUserId` UUID, not a JPA relationship), and that reference must itself be tenant-validated — a candidate assignee must belong to the same organization as the lead. This required one small, additive extension to `identity.repository.UserRepository` (`findByIdAndOrganizationId`), following the exact same tenant-safe-lookup convention already established for `Customer`/`Lead` themselves, applied here to validating a *reference* rather than the primary resource.
- **Assignment is a dedicated action endpoint (`POST /api/v1/leads/{id}/assign`), not a field on the general update DTO** — a plain nullable field on a PATCH-semantics DTO can't distinguish "leave unchanged" from "unassign," so assignment needed its own unambiguous request/response contract instead of overloading `LeadUpdateRequest`.
- **Lead status and lead archival are two independent concerns**, unlike Customer (where `ARCHIVED` was one of the status values): CLAUDE.md §11 fixes `LeadStatus` to exactly 7 values with no archived state permitted, so `Lead` has both `status` (the fixed business-outcome enum) and a separate `archivedAt` timestamp (record-lifecycle state) — see [database.md](database.md) for the full reasoning.
- **No Lead → Customer relationship** — a deliberate decision, not an oversight; CLAUDE.md never mentions one, and the only prior hint (this doc's own Phase-1-era indicative relationships diagram) was a non-binding placeholder, now removed. See [database.md](database.md) §0e.
- Full detail (identifying-field decision, priority values, assignment/activity model, archive semantics, validation, tenant-isolation/RBAC test coverage) is in [security.md](security.md) and [database.md](database.md).

## 1. Style

BizPilot AI is built as a **modular monolith** on the backend, not a microservices system. Business capabilities are separated into clearly bounded Java packages (modules) inside a single Spring Boot application. This gives most of the maintainability benefits of modular design (clear boundaries, independent evolution, testability) without the operational overhead of distributed systems, which is not justified at this stage.

The frontend is a single-page application (SPA) that talks to the backend exclusively through a versioned REST API.

## 2. High-Level Diagram (target state)

```text
                    ┌─────────────────────────────┐
                    │        Frontend (SPA)        │
                    │  React + TS + Vite + Tailwind│
                    └───────────────┬──────────────┘
                                    │ HTTPS / REST (JSON)
                                    ▼
                    ┌─────────────────────────────┐
                    │      Backend (Spring Boot)   │
                    │  ┌─────────────────────────┐ │
                    │  │ Web layer (Controllers)  │ │
                    │  ├─────────────────────────┤ │
                    │  │ Security (JWT, RBAC)     │ │
                    │  ├─────────────────────────┤ │
                    │  │ Service layer            │ │
                    │  ├─────────────────────────┤ │
                    │  │ AI layer (Spring AI)     │ │
                    │  ├─────────────────────────┤ │
                    │  │ Repository layer (JPA)   │ │
                    │  └─────────────────────────┘ │
                    └───┬──────────┬──────────┬────┘
                        │          │          │
                        ▼          ▼          ▼
                 ┌───────────┐ ┌───────┐ ┌──────────┐
                 │PostgreSQL │ │ Redis │ │  Kafka   │
                 │ +PGVector │ │       │ │          │
                 └───────────┘ └───────┘ └──────────┘
```

## 3. Backend Modules

Each module owns its own controller/service/repository/entity/dto/mapper/exception classes and should depend on other modules only through their public service interfaces — never reach into another module's repository or entity directly.

| Module | Responsibility |
|---|---|
| `common` | Shared utilities, base entities, pagination helpers, cross-cutting configuration |
| `security` | Authentication, JWT issuance/validation, security filters, password handling |
| `identity` | Users, roles, permissions, user-role assignment ✅ (Phase 6) |
| `organization` | Organizations and multi-tenancy context resolution |
| `crm` | Customers, customer activities/notes ✅ (Phase 7) |
| `sales` | Leads ✅ (Phase 8), quotations, invoices, sales pipeline |
| `products` | Product catalog, categories |
| `documents` | Document upload, storage abstraction, text extraction, chunking |
| `ai` | Spring AI integration: chat, embeddings, RAG, tool calling, conversation memory |
| `analytics` | Dashboards, aggregated reporting |
| `tasks` | Task management |
| `notifications` | Notification delivery |
| `audit` | Audit logging for sensitive/important operations |

Layering within a module:

```text
Controller -> Service -> Repository -> Entity
                 │
                 ▼
                DTO / Mapper (never expose entities over REST)
```

## 4. Multi-Tenancy

BizPilot AI is multi-tenant: every business is an `Organization`, and every business record (customers, leads, products, quotations, documents, etc.) belongs to exactly one organization.

Principles:

- The current organization is **derived from the authenticated user's security context** on every request — never accepted as a client-supplied parameter.
- Tenant scoping is enforced at the **service and repository level**, not only via UI filtering.
- Every tenant-scoped repository query must filter by organization ID.
- Cross-tenant access (including vector search results in RAG) must be structurally prevented, not just tested for.
- Automated tests specifically targeting tenant isolation are mandatory (see [security.md](security.md)).

## 5. AI Layer

The AI layer sits behind the same service boundaries as the rest of the application:

- The AI assistant calls **application services**, never repositories directly.
- All AI tool calls are validated for authentication, organization scope, and permissions before execution, and are logged.
- Sensitive/irreversible actions require explicit user confirmation before the backend executes them.

Full detail in [ai-architecture.md](ai-architecture.md).

## 6. API Design

- Versioned REST APIs under `/api/v1/...` (e.g. `/api/v1/customers`, `/api/v1/ai`).
- Consistent JSON error envelope for all error responses (see [security.md](security.md)).
- DTOs at the API boundary; entities are never serialized directly.
- Pagination, filtering, and sorting are first-class concerns on list endpoints.

## 7. Cross-Cutting Concerns

- **Error handling:** centralized exception handling, no stack traces leaked to clients.
- **Observability:** Spring Actuator + Micrometer, structured logging, health checks (see [deployment.md](deployment.md)).
- **Rate limiting:** Redis-backed limits on authentication, AI, and upload endpoints.
- **Audit logging:** significant state-changing operations and AI tool executions are recorded (see [security.md](security.md)).

## 8. Frontend Architecture

- Feature-based organization under `frontend/src/features/*`, mirroring backend business capabilities.
- Shared UI primitives in `components/`, page shells in `layouts/`, routed views in `pages/`.
- Server state managed via TanStack Query; forms via React Hook Form + Zod validation.
- No business calculations (e.g. quotation/invoice totals) are performed on the frontend — the backend is always authoritative.
