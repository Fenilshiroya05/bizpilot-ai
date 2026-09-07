# Architecture

> Status: Phase 5 — organizations (tenant root) and tenant isolation exist and are validated end-to-end, on top of Phase 4 (authentication & identity), Phase 3 (database foundation), and Phase 2 (backend foundation). Business modules (`crm`, `sales`, `products`, `documents`, `ai`, `analytics`, `tasks`, `notifications`, `audit`) remain empty placeholders until their respective phases. No frontend code exists yet.

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
| `identity` | Users, roles, permissions, user-role assignment |
| `organization` | Organizations and multi-tenancy context resolution |
| `crm` | Customers, customer activities/notes |
| `sales` | Leads, quotations, invoices, sales pipeline |
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
