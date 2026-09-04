---
name: api-development
description: Use when designing or building BizPilot AI REST API endpoints under /api/v1/* — controller/service/repository separation, DTOs, validation, error responses, HTTP status codes, pagination, versioning, OpenAPI docs, authorization and tenant isolation. Triggers on "add an API endpoint", "design this API", "what status code should this return". Pairs with bizpilot-backend for implementation and testing for coverage.
---

# API Development

## 1. Purpose

Build and shape REST APIs for BizPilot AI so every endpoint looks and behaves consistently across modules.

## 2. When to Use

- Designing a new endpoint or resource (request/response shape, status codes, pagination).
- Reviewing whether an existing endpoint follows the project's API conventions.

## 3. When Not to Use

- Deep internal service/entity implementation with no API-shape question involved → `bizpilot-backend`.
- Full code review across all dimensions → `code-review`.
- Auth/RBAC/tenant deep-dive → `security-review` (this skill enforces that every endpoint *has* the check; `security-review` audits whether it's *correct*).

## 4. Project-Specific Context

- Versioned APIs under `/api/v1/...`: `auth, users, organizations, customers, leads, products, quotations, invoices, tasks, documents, ai, analytics, audit` (CLAUDE.md §28).
- Standard error envelope (CLAUDE.md §27):
  ```json
  { "timestamp": "...", "status": 400, "code": "VALIDATION_ERROR", "message": "Invalid request", "path": "/api/customers" }
  ```
  Already implemented centrally in `common/exception/GlobalExceptionHandler` + `common/response/ApiError` — reuse it, don't reinvent per-controller error handling.
- DTOs only over the wire — entities are never serialized directly (CLAUDE.md §6, §42).
- Backend owns all financial calculations (quotation/invoice totals) — never trust totals from the request body (CLAUDE.md §14, §15, §41).
- Multi-tenancy: the organization comes from the authenticated user's security context, never from a request parameter/body/header (CLAUDE.md §7).

## 5. Required Workflow

1. Confirm the resource/module and its phase in `docs/roadmap.md`.
2. Define the request/response DTOs (with Bean Validation annotations) before the controller method body.
3. Pick the correct HTTP method + status code (see §6).
4. Add pagination (`page`, `size`, `sort`) for list endpoints; add filtering/sorting params only as needed by the actual use case.
5. Wire authorization checks (permission required) once RBAC exists for that resource — don't leave a TODO if the phase already includes auth.
6. Add/extend OpenAPI annotations if the project's OpenAPI setup exists yet (Phase-dependent — check before assuming it's wired up).
7. Write controller-level tests covering success, validation failure, not-found, and forbidden/unauthorized cases.

## 6. Technical Rules

- HTTP methods: `GET` (read, safe/idempotent), `POST` (create), `PUT`/`PATCH` (update — prefer `PATCH` for partial), `DELETE` (delete/archive).
- Status codes: `200` read/update success, `201` created (with `Location` header where sensible), `204` no-content delete, `400` validation, `401` unauthenticated, `403` forbidden, `404` not found, `409` conflict, `422` unprocessable business rule, `429` rate-limited.
- Controllers stay thin: parse/validate input, call one service method, map result to DTO. No business logic in the controller.
- Every list endpoint is paginated; never return an unbounded collection.
- Never accept a computed/authoritative value (total, tax, discount) from the client and persist it as-is.

## 7. Quality Checks

- Request/response DTOs don't leak entity internals (e.g. other tenants' data, internal-only fields).
- Consistent naming and URL structure with existing endpoints in the same module.
- Error responses match the standard envelope in every failure branch.

## 8. Security Considerations

- Confirm the endpoint enforces both authentication and permission-based authorization once available for that module.
- Confirm tenant scoping is applied server-side, not just filtered in the frontend.
- Rate-limit-sensitive endpoints (auth, AI, uploads) per CLAUDE.md §26 — flag if missing once that infra exists.

## 9. Testing Expectations

- Controller tests: happy path, validation error, not-found, forbidden.
- Contract tests confirming pagination/sorting params behave as documented.
- See `testing` skill for the full pyramid this feeds into.

## 10. Expected Final Output/Report

Summarize: endpoint(s) added/changed, method + path + status codes, DTO shapes, pagination/filtering behavior, auth/tenant enforcement status, and test coverage added.
