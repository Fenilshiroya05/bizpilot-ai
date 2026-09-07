# Security

> Status: Phase 7 — the first real tenant-scoped, RBAC-gated business resource (Customers) is implemented, exercising the Phase 5 tenant-isolation and Phase 6 RBAC mechanisms against real data for the first time. Email verification and forgot/reset-password are acknowledged CLAUDE.md requirements **not yet implemented** — deferred to a later authentication pass (see §1a).

Priority order for this entire project: **Security > Correctness > Maintainability > Testability > Performance > Convenience.**

## 1. Authentication

- Registration, login, logout.
- JWT access tokens with a short lifetime; refresh tokens for session continuation.
- Passwords hashed with **BCrypt or Argon2** — never stored in plain text, never reversible.
- Password validation rules enforced server-side.
- Account status tracking (active/disabled/locked).
- Email verification and forgot/reset-password architected as first-class flows (token-based, time-limited, single-use).
- Passwords, JWTs, API keys, and other secrets are **never logged**, in any environment.

### 1a. Implementation Notes (Phase 4)

- **Endpoints**: `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `GET /api/v1/auth/me` (current user).
- **Password hashing**: BCrypt (`BCryptPasswordEncoder`).
- **Access tokens**: JWT (HS256), claims limited to `sub` (user id), `orgId`, and `authorities` (Phase 6: the resolved Spring Security authority set — see §2a) — no email/PII in the token payload. Signing secret comes from `JWT_SECRET` (required, ≥32 bytes, no default — fails fast at startup otherwise). Short lifetime via `JWT_ACCESS_TOKEN_EXPIRATION_MINUTES` (default 15).
- **Refresh tokens**: opaque, cryptographically random (256-bit) values — *not* JWTs. Only their SHA-256 hash is ever persisted (`refresh_tokens.token_hash`); the raw value is returned to the client exactly once. Lifetime via `JWT_REFRESH_TOKEN_EXPIRATION_DAYS` (default 7).
- **Rotation + reuse detection**: every `/refresh` call revokes the presented token and issues a new one. Presenting an already-revoked (previously-rotated) token is treated as a signal of token theft and revokes *every* active refresh token for that user — implemented as its own `REQUIRES_NEW` transaction so the revocation survives regardless of the exception subsequently thrown for the reuse attempt.
- **Login enumeration resistance**: login never distinguishes "unknown email" from "wrong password" — both return a generic `401 INVALID_CREDENTIALS`. A fixed, precomputed BCrypt hash is compared against when no user is found, so the response time doesn't leak whether an email is registered.
- **Account-status leakage prevention**: password is verified *before* account status (`ACTIVE`/`DISABLED`/`LOCKED`) is checked, so a disabled/locked account's status is never revealed to someone who doesn't already know the password (both return `401` for a wrong password regardless of status; only a *correct* password against a non-active account returns `403 ACCOUNT_DISABLED`/`ACCOUNT_LOCKED`).
- **No password/token logging**: verified — no code path logs raw passwords, password hashes, JWTs, or raw refresh tokens.
- **Default role on registration**: every self-registered user gets the `EMPLOYEE` role (looked up from the Phase 6 seed data), `status=ACTIVE` — least-privilege default.
- **Not yet implemented** (acknowledged CLAUDE.md §8 gaps): email verification, forgot-password, reset-password. Account lockout (`LOCKED`) is a valid, checked state but nothing currently transitions a user into it automatically — that arrives with rate-limiting/brute-force protection (CLAUDE.md §26).

## 2. Authorization (RBAC)

- Roles: `OWNER`, `ADMIN`, `MANAGER`, `SALES`, `EMPLOYEE`.
- Permissions are granular (e.g. `CUSTOMER_READ`, `CUSTOMER_CREATE`, `AI_USE`, `USER_MANAGE`).
- **Authorization is enforced in the backend only.** Frontend permission checks exist purely for UX (hiding buttons) and carry zero security weight.
- Every controller/service method that touches sensitive or tenant-scoped data must have an explicit authorization check.
- **Current user**: `security.CurrentUserProvider` lets any service ask "who is making this request?" from the `SecurityContextHolder` without parsing JWTs directly — future modules should depend on this rather than duplicating token-parsing logic.

### 2a. Implementation Notes (Phase 6)

- **Model**: `roles`, `permissions`, `user_roles`, `role_permissions` (CLAUDE.md §6) — a genuine many-to-many at both levels (`identity.entity.Role`/`Permission`, `User.roles`). This **replaced** the single `users.role` column from Phase 4 in the same migration (V4) that introduces the new tables — one authoritative source, never two competing ones. The schema technically permits multiple roles per user, but nothing in this phase assigns more than one by default (registration still assigns exactly `EMPLOYEE`).
- **Seed data**: the 5 roles and the full permission catalog from CLAUDE.md §9 are seeded by Flyway (V4), not application startup code — deterministic, versioned, and consistent with how every other schema change in this project is made (CLAUDE.md §33).
- **Role → permission mapping** (an implementation decision — CLAUDE.md defines roles and permission names but not this mapping):

  | Role | Permissions |
  |---|---|
  | `OWNER`, `ADMIN` | Full catalog (all 16 permissions). No permission exists yet — e.g. billing, organization deletion — that would meaningfully distinguish OWNER from ADMIN; revisit when one is introduced. |
  | `MANAGER` | Full CRUD on customers/leads/quotations, `DOCUMENT_READ`/`DOCUMENT_UPLOAD`, `AI_USE`. No `USER_MANAGE`. |
  | `SALES` | Read/create/update (no delete) on customers/leads/quotations, `DOCUMENT_READ`/`DOCUMENT_UPLOAD`, `AI_USE`. No `USER_MANAGE`. |
  | `EMPLOYEE` | Read-only on customers/leads/quotations/documents, plus `AI_USE` — matches the least-privilege self-registration default. |

  As of Phase 7, `CUSTOMER_READ`/`CREATE`/`UPDATE`/`DELETE` gate real business data for the first time (`crm.controller.CustomerController` — see §2b) — the mapping above is no longer only provable via test-only endpoints.
- **JWT integration**: the access token's `authorities` claim is the user's fully-resolved Spring Security authority set — both `ROLE_<name>` (role authorities, keeping `hasRole(...)` working unchanged from Phase 4) and raw permission names (e.g. `CUSTOMER_READ`, enabling `hasAuthority(...)`). It's computed once, at login/refresh time, from the *current* database state (`AuthService.resolveAuthorities`) — never carried forward from a previous token. This keeps the established "no database lookup per authenticated request" design: `JwtAuthenticationFilter` builds `GrantedAuthority`s directly from this claim.
- **No staleness beyond one token lifetime**: because authorities are re-resolved on every refresh (not just login), a role grant or revocation takes effect on the *next* refresh call, not just the next full login — the same pattern already used for account-status changes (`AccountNotActiveException` check in `RefreshTokenService.rotate`). The maximum staleness window for an already-issued, not-yet-refreshed access token is bounded by `JWT_ACCESS_TOKEN_EXPIRATION_MINUTES` (15 minutes by default).
- **No caching layer**: per the project's explicit "don't introduce Redis just for RBAC" guidance — authorities are computed from the relational model on each login/refresh (a handful of small in-memory collection operations, no extra query beyond what JPA already needs to load the association), which is simple and fast enough without a cache.
- **No role/permission-assignment API**: there is no endpoint anywhere that lets a client assign, modify, or query someone else's roles/permissions. Role changes in this phase are only possible via direct database/repository action (what an eventual admin-only user-management feature, `USER_MANAGE`-gated, would do in a later phase) — there is currently no public attack surface for privilege escalation to target.
- **Method security**: `@EnableMethodSecurity` (already enabled since Phase 4) + `@PreAuthorize("hasRole(...)")` / `@PreAuthorize("hasAuthority(...)")`, verified end-to-end against two test-only endpoints (`security.RoleProtectedTestController`, never shipped to production — same pattern as Phase 3's `SampleEntity` fixture).

### 2b. Implementation Notes (Phase 7 — first real permission-gated resource)

- Every `crm.controller.CustomerController` endpoint carries an explicit `@PreAuthorize("hasAuthority('CUSTOMER_*')")`: `CUSTOMER_READ` for all reads (details, listing/search, notes, activities, history), `CUSTOMER_CREATE` for creation, `CUSTOMER_UPDATE` for field updates and adding a note, `CUSTOMER_DELETE` for archiving. No new roles or permissions were introduced — this phase only consumes the Phase 6 catalog.
- **Why "add a note" requires `CUSTOMER_UPDATE`, not `CUSTOMER_READ`**: CLAUDE.md doesn't define a dedicated note-creation permission, and adding one for a single sub-feature would be inventing scope the project spec doesn't ask for. `CUSTOMER_UPDATE` is the closest existing permission for "adds data to a customer record."
- Verified end-to-end against real seeded roles (not test-only fixtures): `CustomerAuthorizationTests` proves EMPLOYEE (read-only) is forbidden from create/update/delete, SALES (CRUD minus delete) is forbidden from archiving, and MANAGER (full CRUD) can perform every operation.

## 3. Multi-Tenancy / Tenant Isolation

- The current organization is derived **only** from the authenticated user's security context — never from a client-supplied `organizationId`, header, or query parameter.
- Every repository query against a tenant-scoped table filters by `organization_id`.
- RAG/vector search results are filtered by `organization_id` at retrieval time, before being sent to the LLM.
- Automated tests must specifically exercise cross-tenant access attempts and assert they fail (see [database.md](database.md) and the testing strategy).

### 3a. Implementation Notes (Phase 5)

- **Tenant root**: `organizations` (name only — see `docs/database.md §3`). Every `User` belongs to exactly one, via a NOT NULL `organization_id` FK, assigned once at creation and never reassigned in this phase.
- **Provisioning**: since CLAUDE.md defines no invite/join-organization flow, `POST /api/v1/auth/register` auto-provisions a brand-new organization per signup (`RegisterRequest.organizationName`, required). There is intentionally no separate "create organization" endpoint yet.
- **Resolution mechanism**: `organization.TenantContext.currentOrganizationId()` is the *only* way application code determines the current tenant. It reads `organizationId` off `security.UserPrincipal`, which `JwtAuthenticationFilter` builds entirely from the validated JWT's `orgId` claim — no database lookup, and no path through which a request parameter, body field, or header could ever be substituted. `organization.service.OrganizationService.getCurrentOrganization()` is the only service method that resolves an `Organization` for a controller, and it exclusively uses `TenantContext` — there is deliberately no "get organization by arbitrary id" method reachable from a controller (CLAUDE.md §7, project spec §11).
- **API surface**: exactly one endpoint, `GET /api/v1/organizations/current` — it accepts no organization id from the client in any form. This was verified with an explicit test that sends another organization's id via both a header (`X-Organization-Id`) and a query parameter and confirms the response is unaffected (`TenantIsolationTests`).
- **No "user without an organization" case**: the NOT NULL constraint plus mandatory provisioning at registration mean this state cannot occur in this design — not tested, since there's nothing to trigger it (see `docs/database.md §0b` for the reasoning).
- **Malformed-claim safety**: `JwtAuthenticationFilter.authenticate` wraps claim extraction (`sub`/`orgId`/`authorities`) in a try/catch — a validly-signed token with a missing or malformed claim (e.g. one minted before a claim existed) is treated exactly like any other invalid token (left unauthenticated → `401`), never an uncaught exception that would bypass the standard `ApiError` envelope. Covered by an explicit regression test (`AuthenticationFlowTests.meWithValidSignatureButMissingOrganizationClaimReturns401NotAServerError`).
- **RBAC + tenant isolation together (Phase 6)**: these are independent, both-required checks — `@PreAuthorize` decides *whether* an action is permitted at all; `TenantContext` decides *whose* data it applies to. Neither can substitute for the other: holding a permission (e.g. `CUSTOMER_READ`) never provides a way to read another organization's data, since the resolved organization id still comes only from the JWT, never from anything permission-related. Verified explicitly by `RbacAuthorizationTests.havingThePermissionNeverGrantsAccessToAnotherOrganizationsData`.

### 3b. Implementation Notes (Phase 7 — first real tenant-scoped resource)

- **Tenant-safe lookup pattern**: `crm.repository.CustomerRepository.findByIdAndOrganizationId(id, organizationId)` is the *only* by-id lookup used anywhere in `CustomerService` — there is no code path that calls a plain `findById`. A customer belonging to another organization and a truly nonexistent id are deliberately indistinguishable to the client: both produce `404 CUSTOMER_NOT_FOUND`, never a `403`, so a cross-tenant probe can't learn whether a given id exists in another tenant.
- **Child records inherit tenant safety from their parent, not from their own column**: `customer_activities` (backing "activities"/"notes"/"history") has no `organization_id` column of its own — the same precedent as `security.entity.RefreshToken` (a child of `User`). Every read/write against it goes through `CustomerService`'s org-scoped `Customer` lookup *first*; only once that succeeds is `customer_id` used to query activities, so cross-tenant access is structurally impossible without ever duplicating `organization_id` on the child table.
- **Mass-assignment resistance**: `CustomerCreateRequest`/`CustomerUpdateRequest` have no `organizationId` field at all — there is nothing for a client to smuggle at the DTO level. The organization is always resolved via `organization.service.OrganizationService.getCurrentOrganization()` (itself backed by `TenantContext`), never accepted from the request.
- Verified by `CustomerTenantIsolationTests`: cross-org read/update/archive/activities/notes/history all fail as 404; an attempt to smuggle another organization's id via an extra JSON body field, a query parameter, and an `X-Organization-Id` header are each proven ineffective.

## 4. AI-Specific Security

- **Prompt injection:** user-provided documents and chat input are untrusted. Content retrieved from documents or generated by users can influence the *answer*, but must never be able to alter system instructions, bypass authorization, or trigger unconfirmed actions.
- **Authorization is never delegated to the model.** The AI can request an action; only backend code decides whether it's permitted.
- **Tool call validation:** every AI tool call is validated for authentication, organization scope, permission, and business rules before execution (see [ai-architecture.md](ai-architecture.md)).
- **Action confirmation:** sensitive/irreversible actions require explicit user confirmation before backend execution.
- **Hallucination control:** for business-data questions, if the required data cannot be retrieved via a tool, the assistant states that the data could not be found rather than guessing.
- **Sensitive content in logs:** sensitive document contents and full prompts are not written to standard logs.

## 5. Standard Web/API Security

Protections required across the application:

- **SQL injection** — parameterized queries via JPA/Hibernate; no string-concatenated SQL.
- **XSS** — output encoding on the frontend; sanitization of any user-supplied content that is rendered.
- **CSRF** — addressed where applicable given the JWT-based auth model (e.g. for any cookie-based flows).
- **Broken access control / IDOR** — object-level authorization checks on every resource access, scoped by organization and permission.
- **File upload attacks** — content-type validation, size limits, storage outside the web root, no execution of uploaded content.
- **Excessive API usage** — rate limiting on authentication, registration, password reset, AI endpoints, file upload, and public APIs (Redis-backed).

## 6. Error Handling

- Centralized exception handling with a consistent JSON error envelope:

  ```json
  {
    "timestamp": "...",
    "status": 400,
    "code": "VALIDATION_ERROR",
    "message": "Invalid request",
    "path": "/api/customers"
  }
  ```

- Internal stack traces and implementation details are never exposed to clients.

## 7. Audit Logging

Tracked for important operations: user, organization, action, entity type, entity ID, timestamp, IP where appropriate, and result. Examples: `LOGIN`, `CREATE_CUSTOMER`, `UPDATE_CUSTOMER`, `DELETE_CUSTOMER`, `CREATE_QUOTATION`, `AI_TOOL_EXECUTION`, `DOCUMENT_UPLOAD`.

Audit logs never contain passwords, JWTs, API keys, other secrets, or sensitive document contents.

**Not yet implemented (Phase 7 note)**: this remains the generic, project-wide `audit_logs` capability (CLAUDE.md §24) — no phase in the roadmap has built it yet, and Phase 7 deliberately does not either. `crm.entity.CustomerActivity` (see `docs/database.md`) is a narrower, customer-scoped timeline for the CLAUDE.md §10 "activities/notes/history" *features*, not a substitute for this system-wide audit log — it records only customer lifecycle events (created/status changed/archived/note added), not every sensitive operation across the application (e.g. it does not log `LOGIN` or `AI_TOOL_EXECUTION`).

## 8. Secrets & Configuration

- No secrets are ever committed to the repository (`.env` is gitignored; only `.env.example` with placeholder values is committed).
- No secrets are placed in Dockerfiles or docker-compose files — they are injected via environment variables at runtime.
- Actuator endpoints never expose secrets or sensitive configuration.

## 9. Observability

- Spring Actuator + Micrometer for health/metrics, structured logging application-wide.
- Health endpoints expose only operational status, not internal configuration or secrets.

## 10. Financial Integrity

- All quotation/invoice totals (subtotal, discount, tax, grand total) are calculated **exclusively on the backend**. Values submitted from the frontend for these fields are never trusted or persisted as-is.
