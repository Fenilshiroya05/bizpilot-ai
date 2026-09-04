---
name: testing
description: Use when writing or planning tests for BizPilot AI backend code — unit, service, controller, repository, integration, security, and Testcontainers-based tests using JUnit 5 and Mockito. Triggers on "add tests", "write a test for this", "how should this be tested". Tests must verify real behavior, not just raise coverage numbers.
---

# Testing

## 1. Purpose

Ensure BizPilot AI features have tests that actually verify behavior — especially multi-tenancy, security, and financial-calculation correctness — using the project's chosen stack.

## 2. When to Use

- After implementing backend logic that needs test coverage.
- When deciding what *kind* of test a given piece of logic needs.

## 3. When Not to Use

- Writing the feature itself → `bizpilot-backend` / `api-development` / `database` / `ai-development` (this skill covers the tests that follow).
- Auditing security without writing tests → `security-review` (but use this skill to turn its findings into regression tests).

## 4. Project-Specific Context

- Stack: JUnit 5, Mockito, Testcontainers (backend); frontend testing arrives in Phase 20+ (CLAUDE.md §32).
- Current test foundation (Phase 2): `BizPilotApplicationTests` (context load) and `HealthEndpointTests` (live actuator check) under `backend/src/test/java/com/bizpilot`, with a `test` Spring profile (`application-test.yml`).
- Testing is mandatory across layers per CLAUDE.md §32: unit, repository, service, controller, security, integration, multi-tenancy, and AI tool tests.
- Multi-tenancy tests are explicitly required, not optional (CLAUDE.md §7): prove Organization A cannot read/write Organization B's data.
- Financial calculations (quotation/invoice totals) must be deterministic and backend-owned — test the calculation logic directly, don't just snapshot an example (CLAUDE.md §41).

## 5. Required Workflow

1. Identify what changed and what risk it carries (new endpoint? new query? new calculation? new tenant-scoped access?).
2. Pick the right test level(s) from §6 — don't default to only controller tests or only unit tests.
3. Write tests that assert on real outcomes (HTTP status + body shape, persisted state, computed values) — not on mocks calling mocks.
4. For anything tenant-scoped, add an explicit cross-tenant-denial test.
5. Run the suite (`./mvnw test` or `./mvnw clean verify`) and confirm it passes before reporting done — never report tests as passing without having run them.

## 6. Technical Rules — Test Levels

- **Unit tests**: pure logic (calculations, mappers, validators) — no Spring context, fast.
- **Service tests**: Mockito-mocked repositories/collaborators, verify business rules and orchestration.
- **Repository tests**: `@DataJpaTest` or Testcontainers-backed, verify queries return correct/scoped results.
- **Controller tests**: `@WebMvcTest` or full `@SpringBootTest` with `MockMvc`/`TestRestTemplate`, verify status codes, response shape, validation errors.
- **Integration tests**: full context, real (Testcontainers) Postgres/Redis where relevant, verify a feature end-to-end.
- **Security tests**: unauthenticated → 401, wrong role/permission → 403, cross-tenant → 403/404 (never leak existence of another tenant's resource).
- **AI tool tests** (once Phase 16+ exists): verify tool calls enforce auth/org/permission and route through services, not repositories.

## 7. Quality Checks

- Tests fail when the underlying behavior is broken (mutate the code mentally — would this test catch it?).
- No tests that only assert a mock was called without asserting the resulting behavior/state.
- No flaky time-, order-, or environment-dependent tests.

## 8. Security Considerations

Every new authz/tenant boundary gets a negative test (access denied), not just a positive one (access granted).

## 9. Testing Expectations

This skill *is* the testing expectations. Coverage percentage is not the goal — meaningful assertions on real behavior are.

## 10. Expected Final Output/Report

List tests added/changed, what each one verifies, the test level, and the actual run result (pass/fail counts from `./mvnw test`). Flag any risk area still without coverage.
