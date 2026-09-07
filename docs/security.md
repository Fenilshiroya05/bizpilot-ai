# Security

> Status: Phase 5 — organizations (tenant root) and tenant isolation are implemented and validated, on top of Phase 4 authentication (registration, login, JWT access tokens, refresh-token rotation with reuse detection, logout). Full granular RBAC (permission catalog, `roles`/`permissions`/`user_roles` tables) remains Phase 6. Email verification and forgot/reset-password are acknowledged CLAUDE.md requirements **not yet implemented** — deferred to a later authentication pass (see §1a).

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
- **Access tokens**: JWT (HS256), claims limited to `sub` (user id) and `role` — no email/PII in the token payload. Signing secret comes from `JWT_SECRET` (required, ≥32 bytes, no default — fails fast at startup otherwise). Short lifetime via `JWT_ACCESS_TOKEN_EXPIRATION_MINUTES` (default 15).
- **Refresh tokens**: opaque, cryptographically random (256-bit) values — *not* JWTs. Only their SHA-256 hash is ever persisted (`refresh_tokens.token_hash`); the raw value is returned to the client exactly once. Lifetime via `JWT_REFRESH_TOKEN_EXPIRATION_DAYS` (default 7).
- **Rotation + reuse detection**: every `/refresh` call revokes the presented token and issues a new one. Presenting an already-revoked (previously-rotated) token is treated as a signal of token theft and revokes *every* active refresh token for that user — implemented as its own `REQUIRES_NEW` transaction so the revocation survives regardless of the exception subsequently thrown for the reuse attempt.
- **Login enumeration resistance**: login never distinguishes "unknown email" from "wrong password" — both return a generic `401 INVALID_CREDENTIALS`. A fixed, precomputed BCrypt hash is compared against when no user is found, so the response time doesn't leak whether an email is registered.
- **Account-status leakage prevention**: password is verified *before* account status (`ACTIVE`/`DISABLED`/`LOCKED`) is checked, so a disabled/locked account's status is never revealed to someone who doesn't already know the password (both return `401` for a wrong password regardless of status; only a *correct* password against a non-active account returns `403 ACCOUNT_DISABLED`/`ACCOUNT_LOCKED`).
- **No password/token logging**: verified — no code path logs raw passwords, password hashes, JWTs, or raw refresh tokens.
- **Default role on registration**: every self-registered user gets `role=EMPLOYEE`, `status=ACTIVE` — least-privilege default. Proper role assignment/invite flows arrive with full RBAC (Phase 6).
- **Not yet implemented** (acknowledged CLAUDE.md §8 gaps): email verification, forgot-password, reset-password. Account lockout (`LOCKED`) is a valid, checked state but nothing currently transitions a user into it automatically — that arrives with rate-limiting/brute-force protection (CLAUDE.md §26).

## 2. Authorization (RBAC)

- Roles: `OWNER`, `ADMIN`, `MANAGER`, `SALES`, `EMPLOYEE`.
- Permissions are granular (e.g. `CUSTOMER_READ`, `CUSTOMER_CREATE`, `AI_USE`, `USER_MANAGE`).
- **Authorization is enforced in the backend only.** Frontend permission checks exist purely for UX (hiding buttons) and carry zero security weight.
- Every controller/service method that touches sensitive or tenant-scoped data must have an explicit authorization check.
- **Phase 4 foundation**: each user has exactly one `role` (single column, not yet the full `roles`/`permissions`/`user_roles` junction schema). The JWT carries the role as a Spring Security authority (`ROLE_<name>`), and `@EnableMethodSecurity` + `@PreAuthorize("hasRole(...)")` is wired and verified working end-to-end. The granular per-permission catalog (`CUSTOMER_READ`, etc.) is Phase 6 scope.
- **Current user**: `security.CurrentUserProvider` lets any service ask "who is making this request?" from the `SecurityContextHolder` without parsing JWTs directly — future modules should depend on this rather than duplicating token-parsing logic.

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
- **Malformed-claim safety**: `JwtAuthenticationFilter.authenticate` wraps claim extraction (`sub`/`role`/`orgId`) in a try/catch — a validly-signed token with a missing or malformed claim (e.g. one minted before the `orgId` claim existed) is treated exactly like any other invalid token (left unauthenticated → `401`), never an uncaught exception that would bypass the standard `ApiError` envelope. Covered by an explicit regression test (`AuthenticationFlowTests.meWithValidSignatureButMissingOrganizationClaimReturns401NotAServerError`).

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

## 8. Secrets & Configuration

- No secrets are ever committed to the repository (`.env` is gitignored; only `.env.example` with placeholder values is committed).
- No secrets are placed in Dockerfiles or docker-compose files — they are injected via environment variables at runtime.
- Actuator endpoints never expose secrets or sensitive configuration.

## 9. Observability

- Spring Actuator + Micrometer for health/metrics, structured logging application-wide.
- Health endpoints expose only operational status, not internal configuration or secrets.

## 10. Financial Integrity

- All quotation/invoice totals (subtotal, discount, tax, grand total) are calculated **exclusively on the backend**. Values submitted from the frontend for these fields are never trusted or persisted as-is.
