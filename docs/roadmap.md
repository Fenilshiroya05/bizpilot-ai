# Development Roadmap

This roadmap mirrors the phased plan defined in [CLAUDE.md](../CLAUDE.md) (`# 45. DEVELOPMENT PHASES`). Each phase is completed — implemented, tested, security-reviewed, documented, and reported — before the next one begins. No phase is skipped or merged into another.

Status legend: `[x]` complete · `[ ]` not started.

| Phase | Scope | Status |
|---|---|---|
| 1 | Repository structure + documentation + CLAUDE.md | [x] |
| 2 | Backend foundation | [ ] |
| 3 | Database + Flyway | [ ] |
| 4 | Authentication | [ ] |
| 5 | Organizations + multi-tenancy | [ ] |
| 6 | RBAC | [ ] |
| 7 | Customers | [ ] |
| 8 | Leads | [ ] |
| 9 | Products | [ ] |
| 10 | Quotations | [ ] |
| 11 | Invoices | [ ] |
| 12 | Tasks | [ ] |
| 13 | Document management | [ ] |
| 14 | Spring AI foundation | [ ] |
| 15 | RAG + PGVector | [ ] |
| 16 | AI tool calling | [ ] |
| 17 | AI assistant | [ ] |
| 18 | AI lead scoring | [ ] |
| 19 | Analytics | [ ] |
| 20 | Frontend foundation | [ ] |
| 21 | Frontend authentication | [ ] |
| 22 | CRM UI | [ ] |
| 23 | Sales UI | [ ] |
| 24 | Document UI | [ ] |
| 25 | AI assistant UI | [ ] |
| 26 | Dashboard | [ ] |
| 27 | Testing | [ ] |
| 28 | Docker | [ ] |
| 29 | CI/CD | [ ] |
| 30 | Production hardening | [ ] |
| 31 | Deployment | [ ] |

## Phase 1 — Completed Scope

- Repository directory structure for `backend/`, `frontend/`, and `infrastructure/` (empty module folders, no code).
- `README.md` describing the product, planned features, architecture, stack, and setup process.
- `docs/architecture.md`, `docs/database.md`, `docs/ai-architecture.md`, `docs/security.md`, `docs/deployment.md`.
- `.gitignore` covering Java/Maven, Node, environment files, IDE, and OS artifacts.
- `.env.example` listing all planned configuration variables with placeholder values.
- Baseline `docker-compose.yml` for local infrastructure (PostgreSQL/PGVector, Redis, Kafka) with backend/frontend placeholders.
- This roadmap document.

No business logic, entities, controllers, services, or UI components were created in Phase 1, per the project's incremental-build rule (`CLAUDE.md § 2`).

## Process Per Phase

Each subsequent phase follows the same checklist (`CLAUDE.md § 2`):

1. Understand the existing code.
2. Inspect the project structure.
3. Plan the changes.
4. Implement the changes.
5. Run tests.
6. Fix compilation errors.
7. Fix test failures.
8. Review security implications.
9. Review code quality.
10. Update documentation.
11. Report what was completed.
12. Clearly state the next phase.
