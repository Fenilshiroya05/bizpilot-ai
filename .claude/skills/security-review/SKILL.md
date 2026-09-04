---
name: security-review
description: Use for a dedicated security audit of BizPilot AI code or design — authentication, JWT/refresh tokens, RBAC/authorization, tenant isolation, IDOR, input validation, SQL injection, XSS, CSRF, secrets exposure, rate limiting, audit logging, privilege escalation, and AI-specific risks (prompt injection, unauthorized tool calls). Triggers on "security review", "is this safe", "check for vulnerabilities", "review auth/authz". Read-only unless explicitly asked to fix.
---

# Security Review

## 1. Purpose

Audit BizPilot AI code, configuration, or design against the security requirements in `CLAUDE.md` and `docs/security.md`, treating findings as serious by default.

## 2. When to Use

- Before merging any change touching authentication, authorization, tenant scoping, file upload, or AI tool execution.
- When explicitly asked for a security review of a feature, endpoint, or PR.

## 3. When Not to Use

- General code quality/architecture review with no specific security angle → `spring-boot-review` or `code-review` (this skill focuses narrowly and deeply on security).
- Don't use this to implement fixes by default — report findings; only fix if the user explicitly asks.

## 4. Project-Specific Context

- Priority order for the whole project: **Security > Correctness > Maintainability > Testability > Performance > Convenience** (CLAUDE.md, final section).
- Auth: JWT access + refresh tokens, BCrypt/Argon2 password hashing, email verification and forgot/reset-password flows (CLAUDE.md §8).
- RBAC: roles `OWNER, ADMIN, MANAGER, SALES, EMPLOYEE`; granular permissions like `CUSTOMER_READ`, `AI_USE`, `USER_MANAGE` — backend-enforced only, frontend checks are UX-only (CLAUDE.md §9).
- Multi-tenancy: organization derived from the security context, never a client-supplied ID; RAG/vector search must never leak across organizations (CLAUDE.md §7, §17, §38).
- AI-specific: user documents/prompts are untrusted input; retrieved content must never override system instructions or bypass authorization; the AI never decides authorization — backend code always does (CLAUDE.md §38, §40, `docs/ai-architecture.md §8`).
- Audit logging required for sensitive operations, but must never log passwords, JWTs, API keys, or sensitive document content (CLAUDE.md §24).
- Full checklist lives in `docs/security.md` — treat it as the canonical reference, don't re-derive rules from scratch.

## 5. Required Workflow

1. Identify what's in scope: a diff, a module, or a specific concern (e.g. "review the auth flow").
2. Walk the request lifecycle end-to-end for the feature: entry point → authentication → authorization → tenant scoping → data access → response — note where each control is missing or weak.
3. Check the OWASP-style list in §6 against the actual code, not just in the abstract.
4. For AI-touching code, specifically verify tool calls go through application services (not repositories directly) and that sensitive actions require explicit confirmation before execution (CLAUDE.md §19, §20).
5. Classify each finding by severity and describe a concrete exploit/failure scenario — not just "this could be a problem."

## 6. Technical Rules — What to Check

- **AuthN**: password hashing algorithm, token expiry, refresh-token rotation/revocation, no plaintext secrets.
- **AuthZ / RBAC**: every sensitive endpoint/service method checks permission server-side; no reliance on frontend hiding.
- **Tenant isolation / IDOR**: every tenant-scoped query filters by the authenticated user's organization; object IDs alone are never sufficient to authorize access.
- **Input validation**: all external input validated before use; no string-concatenated SQL; output encoding for anything rendered.
- **CSRF**: relevant only for cookie-based flows — confirm whether the auth model uses cookies before flagging.
- **Secrets**: nothing hard-coded, nothing logged, `.env` never committed (check `.gitignore`).
- **Rate limiting**: present on login, registration, password reset, AI endpoints, uploads once that infra exists (CLAUDE.md §26).
- **AI-specific**: prompt injection resistance, tool-call validation (auth + org + permission + business rules), no hallucinated data presented as fact, confirmation gate before irreversible actions.
- **Privilege escalation**: role/permission checks can't be bypassed via parameter tampering or missing checks on secondary endpoints (e.g. bulk operations, admin-only fields on a general update endpoint).

## 7. Quality Checks

Every finding includes: the exact vulnerable code/flow, the concrete attack scenario (who, how, what they gain), severity, and a specific fix — not a generic "add validation."

## 8. Security Considerations

This entire skill *is* the security consideration. Do not soften severity for convenience; do not assume a control exists because it's documented in `CLAUDE.md` — verify it's actually implemented.

## 9. Testing Expectations

Recommend concrete tests that would have caught each finding (e.g. a cross-tenant access test, an unauthenticated-request test, a tampered-role-claim test). Hand off to `testing` for actual test authoring if asked.

## 10. Expected Final Output/Report

Findings list, most severe first, tagged CRITICAL/HIGH/MEDIUM/LOW, each with: location, exploit scenario, impact, and fix. Close with an explicit statement of what was verified as *safe*, not only what's broken.
