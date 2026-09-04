---
name: code-review
description: Use for a broad, multi-dimensional review of BizPilot AI changes — architecture, correctness, security, performance, maintainability, testing, database usage, API design, logging, error handling, and AI-specific risks together. Findings classified CRITICAL/HIGH/MEDIUM/LOW. Read-only unless explicitly asked to fix. Use spring-boot-review for a narrower Spring-only pass, security-review for a security-only deep dive, or database for a schema-only pass.
---

# BizPilot Code Review

## 1. Purpose

Give a single, holistic review of a BizPilot AI change across every dimension that matters for a production SaaS system — not just "does it compile."

## 2. When to Use

- Before merging a non-trivial feature/PR that spans more than one concern (e.g. a new endpoint plus its query plus its AI tool).
- When asked for "a review" without a narrower scope specified.

## 3. When Not to Use

- A tightly-scoped Spring/DI/transactions-only pass → `spring-boot-review`.
- A dedicated, deep security audit → `security-review`.
- A schema/migration-only pass → `database`.
- Writing tests or fixing findings — only if explicitly asked; default is report-only.

## 4. Project-Specific Context

Draws on the same source of truth as every other skill here: `CLAUDE.md` for architecture/stack/rules, `docs/architecture.md`, `docs/database.md`, `docs/security.md`, `docs/ai-architecture.md`, and `docs/roadmap.md` for what phase the change belongs to. Key project invariants to check against:

- Modular monolith layering, DTOs at the boundary, no cross-module repository access (CLAUDE.md §42).
- Multi-tenancy enforced server-side via security context (CLAUDE.md §7).
- Backend-authoritative financial calculations (CLAUDE.md §14, §15, §41).
- AI tools go through services, never repositories, and require confirmation for irreversible actions (CLAUDE.md §19, §20).
- Standard error envelope and centralized exception handling (CLAUDE.md §27).

## 5. Required Workflow

1. Scope the review: diff, PR, branch, or named files.
2. Read enough surrounding context to judge correctness, not just the changed lines.
3. Walk each dimension in §6 in turn.
4. Classify every finding CRITICAL/HIGH/MEDIUM/LOW with a concrete failure scenario.
5. Do not fix anything unless explicitly asked — report only.

## 6. Technical Rules — Dimensions to Cover

- **Architecture**: layering respected, correct module ownership, no unnecessary coupling.
- **Correctness**: logic does what it claims, edge cases (empty, null, boundary values) handled.
- **Security**: auth/authz present and correct, tenant isolation, input validation, no secrets — escalate to `security-review` if this needs a deep dive.
- **Performance**: N+1 queries, missing pagination, unnecessary blocking calls, inefficient loops over large collections.
- **Maintainability**: no unnecessary abstraction, no duplicated logic, clear naming consistent with the rest of the codebase.
- **Testing**: appropriate tests exist for the risk level of the change (see `testing` skill's levels).
- **Database usage**: queries scoped correctly, indexes present, migrations immutable and correctly formed.
- **API design**: correct HTTP methods/status codes, consistent error shape, versioned path.
- **Logging**: SLF4J only, no sensitive data, useful for debugging without being noisy.
- **Error handling**: exceptions routed to the central handler, no internal details leaked to clients.
- **AI-specific risks**: prompt injection exposure, tool calls bypassing services, missing confirmation gates, cross-tenant retrieval risk — apply only when the change touches `ai/`.

## 7. Quality Checks

Every finding is specific (file/line), explains the concrete consequence, and offers an actionable fix — not a vague style preference.

## 8. Security Considerations

Security findings are never downgraded for convenience. If a finding overlaps with what `security-review` would catch in more depth, still report it here and recommend a follow-up `security-review` pass for anything auth/tenant/AI-security-critical.

## 9. Testing Expectations

Flag missing test coverage as a finding (typically MEDIUM, or HIGH if it's a tenant-isolation or financial-calculation gap), and name what test the `testing` skill should add.

## 10. Expected Final Output/Report

```text
CRITICAL: ...
HIGH: ...
MEDIUM: ...
LOW: ...
```
Each item: location, what's wrong, why it matters, recommended fix. Close with an overall merge/no-merge recommendation and what must be resolved first.
