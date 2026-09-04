---
name: bizpilot-backend
description: Use when implementing or extending BizPilot AI backend functionality (Spring Boot / Java 21 controllers, services, repositories, entities, DTOs) in the backend/ module. Triggers on "add endpoint", "implement service", "create entity", "add repository", "new backend module", "implement customer/lead/product/quotation/invoice/task backend feature". Not for pure code review or database migration authoring alone (use spring-boot-review / database for those).
---

# BizPilot Backend Implementation

## 1. Purpose

Implement backend functionality for BizPilot AI following the architecture, package structure, and conventions defined in `CLAUDE.md`, so every module is consistent regardless of who (or which session) writes it.

## 2. When to Use

- Adding a new REST endpoint, service method, repository, entity, or DTO in `backend/`.
- Building out a business module (`crm`, `sales`, `products`, `documents`, `tasks`, etc.) per its assigned development phase.
- Wiring configuration, exception handling, or cross-cutting `common/` code.

## 3. When Not to Use

- Reviewing existing code without changing it → `spring-boot-review` or `code-review`.
- Writing/altering Flyway migrations or entity-relationship design in depth → `database` (use together when a feature needs both).
- Building AI/RAG/tool-calling features → `ai-development` (use together, this skill still governs the surrounding Spring Boot plumbing).
- Frontend work — out of scope entirely.

## 4. Project-Specific Context

- **Stack**: Java 21, Spring Boot 3.x, Spring Web, Spring Security, Spring Data JPA, Hibernate, Bean Validation, Maven, Flyway, PostgreSQL + PGVector, Redis, Kafka (CLAUDE.md §3).
- **Architecture**: modular monolith. Root package `com.bizpilot`, one package per module: `common, security, identity, organization, crm, sales, products, documents, ai, analytics, tasks, notifications, audit` (CLAUDE.md §42, `docs/architecture.md`).
- **Current state**: Phase 2 (backend foundation) is complete — `BizPilotApplication`, `application.yml` (+ `local`/`test` profiles), Actuator `health` only, `common/exception/GlobalExceptionHandler` + `common/response/ApiError`. No datasource, security, or business modules exist yet; check `docs/roadmap.md` for the current phase before adding anything.
- **Multi-tenancy is mandatory** for every business entity/table (CLAUDE.md §7): the organization is derived from the authenticated user's security context — never from a client-supplied field.
- **Financial calculations** (quotations/invoices) are always computed server-side, never trusted from the client (CLAUDE.md §14, §15, §41).

## 5. Required Workflow

1. Check `docs/roadmap.md` — confirm the module/feature belongs to the current or already-completed phase. If it belongs to a later phase, stop and say so instead of implementing it.
2. Read the existing code in the target module and in `common/` before adding anything — do not duplicate existing exception/response/pagination helpers.
3. Implement in layers, one direction of dependency only: `Controller → Service → Repository → Entity`, with DTOs + mappers at the controller boundary (never return/accept JPA entities directly).
4. Add Bean Validation annotations on request DTOs; let `GlobalExceptionHandler` (already in `common/exception`) turn violations into the standard `ApiError` shape — don't hand-roll new error formats.
5. Scope every repository query and service method touching tenant data by the current organization (from security context), per `docs/security.md`.
6. Add/adjust configuration via `application.yml` + environment variables — never hard-code URLs, credentials, or provider keys (CLAUDE.md §35).
7. Run the build and tests before reporting done (see §9).

## 6. Technical Rules

- No unnecessary dependencies — check `CLAUDE.md §3` and the current `pom.xml` before adding a library; if `CLAUDE.md` doesn't call for it, don't add it (e.g. no Lombok unless explicitly approved).
- No `System.out.println` — use SLF4J via `LoggerFactory.getLogger`.
- No business logic in controllers; controllers only translate HTTP ↔ service calls.
- No entities exposed over REST — always DTO + mapper.
- Package-private/module boundaries: don't reach into another module's repository or entity directly — go through its service.
- Never log passwords, JWTs, API keys, or full request/response bodies containing sensitive data (CLAUDE.md §24, §38).

## 7. Quality Checks

Before declaring work done, verify:

- Compiles cleanly, no unused imports, no dead code.
- Consistent naming with existing modules (package casing, class suffixes: `*Controller`, `*Service`, `*Repository`, `*Dto`, `*Mapper`).
- No duplicated logic that already exists in `common/`.
- No accidental changes to unrelated modules or Phase 1/2 documentation.

## 8. Security Considerations

- Every new endpoint needs an explicit authorization check once RBAC exists (Phase 6+) — do not assume "not yet implemented" is a reason to skip it once the phase arrives.
- Treat all request input as untrusted — validate at the DTO boundary.
- Follow `docs/security.md` for the error-response contract and audit-logging expectations for sensitive operations.

## 9. Testing Expectations

- New service/repository logic needs unit tests (JUnit 5 + Mockito).
- New endpoints need controller-level tests.
- Tenant-scoped features need at least one test proving cross-tenant access is rejected (CLAUDE.md §7).
- Run `./mvnw test` (or `clean verify` for a full check) and ensure it passes before reporting completion.

## 10. Expected Final Output/Report

Summarize: what was implemented, which files were created/modified, which module/phase it belongs to, build/test results, and anything intentionally deferred to a later phase. Do not claim a feature is "done" if it doesn't compile or tests don't pass.
