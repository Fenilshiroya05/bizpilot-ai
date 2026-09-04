---
name: spring-boot-review
description: Use to review existing BizPilot AI Spring Boot code (backend/) for dependency injection, architecture, transactions, exception handling, validation, configuration, logging, performance and Spring best practices. Read-only review — does not modify code. Triggers on "review this controller/service", "check this Spring Boot code", "is this implementation correct". For a broader multi-dimensional review use code-review; for auth/tenant-specific issues use security-review.
---

# Spring Boot Code Review

## 1. Purpose

Review BizPilot AI backend code for Spring/Java correctness, architectural fit, and maintainability — without changing it, unless explicitly asked to fix findings.

## 2. When to Use

- After implementing or receiving a backend change and before it's considered final.
- When asked to sanity-check a controller/service/repository/config class.

## 3. When Not to Use

- Broad, all-dimension review spanning DB/API/AI/security together → `code-review`.
- Dedicated security audit (authz, tenant isolation, IDOR, secrets) → `security-review`.
- Reviewing SQL/migrations/entity design in depth → `database`.
- Don't use this to write new code — it's read-only unless the user explicitly asks for fixes.

## 4. Project-Specific Context

- Modular monolith under `com.bizpilot.*`; layering is `Controller → Service → Repository → Entity`, DTOs at the boundary (CLAUDE.md §42, `docs/architecture.md`).
- Multi-tenancy must be enforced at service/repository level via the authenticated user's organization — never a client-supplied ID (CLAUDE.md §7).
- Standard error contract: `{timestamp, status, code, message, path}` via centralized `@RestControllerAdvice` (CLAUDE.md §27, `common/exception/GlobalExceptionHandler`).
- No business logic should be duplicated between REST controllers and AI tools — both must call the same service layer (CLAUDE.md §19).

## 5. Required Workflow

1. Identify the diff or files in scope (ask for a target if none given — a PR, branch, or file list).
2. Read the changed code plus enough surrounding context (calling controller, related service, tests) to judge correctness, not just style.
3. Check each area in §6 systematically; don't stop at the first issue found.
4. Classify each finding by severity (CRITICAL / HIGH / MEDIUM / LOW) with a concrete failure scenario, not a vague concern.
5. Do not modify code — report findings only, unless the user explicitly says "fix it."

## 6. Technical Rules — What to Check

- **Dependency injection**: constructor injection only (no field `@Autowired`), no circular dependencies, no `new` on Spring-managed beans.
- **Architecture**: layering respected, no controller-to-repository shortcuts, no cross-module repository/entity access.
- **Transactions**: `@Transactional` placed correctly (service layer, not controller/repository), read-only transactions marked, no transaction spanning external I/O (AI calls, HTTP, file storage) unnecessarily.
- **Exception handling**: exceptions propagate to the central handler rather than being swallowed; no raw `Exception` returned to clients with internal details.
- **Validation**: `@Valid`/Bean Validation on inbound DTOs; business-rule validation in the service, not the controller.
- **Configuration**: no hard-coded URLs/credentials/ports; environment-variable driven (CLAUDE.md §35).
- **Logging**: SLF4J only, no `System.out`, no sensitive data logged (passwords, tokens, PII, full documents).
- **Performance**: N+1 query patterns, missing pagination on list endpoints, unnecessary eager fetching, blocking calls inside hot paths.
- **Maintainability / unnecessary complexity**: premature abstractions, dead code, duplicated logic that belongs in `common/`.

## 7. Quality Checks

Each finding must include: file/line, what's wrong, why it matters (concrete consequence), and a recommended fix. Avoid nitpicks with no real consequence — prioritize signal over volume.

## 8. Security Considerations

Flag (but don't necessarily deep-dive — hand off to `security-review` for full analysis): missing authorization checks, tenant-scoping gaps, secrets in code/config, unvalidated input reaching persistence or AI prompts.

## 9. Testing Expectations

Note if the reviewed change lacks tests appropriate to its risk (new service logic, new endpoint, tenant-scoped query) — recommend what `testing` skill work should follow, but don't write the tests yourself in this skill.

## 10. Expected Final Output/Report

A findings list, most severe first, each tagged CRITICAL/HIGH/MEDIUM/LOW with file reference, the problem, why it matters, and a fix recommendation. End with a one-line overall verdict (e.g. "safe to merge once CRITICAL/HIGH items are fixed").
