# BizPilot AI

BizPilot AI is an AI-powered business operations platform for small and medium businesses (SMBs). It combines a traditional CRM/sales/operations backend with an AI assistant that can search business data, search uploaded documents (RAG), call approved backend tools, and create business records under explicit authorization and confirmation.

This is being built as a production-grade, multi-tenant SaaS application — not a prototype.

> **Status:** Phase 1 — repository structure and documentation only. No business functionality has been implemented yet. See [docs/roadmap.md](docs/roadmap.md) for the full build plan.

---

## Table of Contents

- [What BizPilot AI Is](#what-bizpilot-ai-is)
- [Planned Features](#planned-features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Repository Structure](#repository-structure)
- [Local Setup](#local-setup)
- [Environment Variables](#environment-variables)
- [Running the Backend](#running-the-backend)
- [Running the Frontend](#running-the-frontend)
- [Running with Docker](#running-with-docker)
- [Running Tests](#running-tests)
- [Deployment](#deployment)
- [Documentation](#documentation)
- [Development Process](#development-process)

---

## What BizPilot AI Is

BizPilot AI lets a business manage its core operations — organizations, users, roles/permissions, customers, leads, products, sales pipeline, quotations, invoices, tasks, and documents — from a single platform, with an AI assistant layered on top that understands the business's own data instead of operating as a generic chatbot.

## Planned Features

- Multi-tenant organizations with strict tenant isolation
- Authentication (JWT access/refresh tokens) and RBAC authorization
- Customer relationship management (CRM)
- Lead management with AI-assisted lead scoring
- Product catalog
- Quotations and invoices with backend-authoritative financial calculations
- Task management
- Document upload, processing, and Retrieval-Augmented Generation (RAG)
- A central AI business assistant with controlled tool calling and action confirmation
- Business analytics and AI-generated insights (grounded in real data only)
- Audit logging and AI usage tracking
- Rate limiting on sensitive endpoints

Business functionality is **not yet implemented**. This repository currently contains only the project scaffolding and documentation produced in Phase 1.

## Architecture

See [docs/architecture.md](docs/architecture.md) for the full system architecture, module boundaries, and multi-tenancy design.

High-level shape:

- **Backend:** Modular monolith (Spring Boot / Java 21), organized by business capability (`identity`, `organization`, `crm`, `sales`, `products`, `documents`, `ai`, `analytics`, `tasks`, `notifications`, `audit`, `security`, `common`).
- **Frontend:** React + TypeScript SPA consuming a versioned REST API.
- **Data:** PostgreSQL with PGVector for embeddings; Redis for caching/rate limiting; Kafka for async/event-driven workflows.
- **AI:** Spring AI as the provider-agnostic abstraction layer (OpenAI, Anthropic, Google Gemini, or local/Ollama models configurable per deployment).

## Tech Stack

### Backend

- Java 21
- Spring Boot 3.x (Web, Security, Data JPA, Validation, Actuator)
- Spring AI
- Hibernate
- Maven
- Flyway
- PostgreSQL + PGVector
- Redis
- Apache Kafka

### Frontend

- React + TypeScript
- Vite
- Tailwind CSS
- shadcn/ui
- TanStack Query
- React Hook Form + Zod
- Recharts

### Infrastructure

- Docker / Docker Compose (local development)
- GitHub Actions (CI/CD — planned in a later phase)

## Repository Structure

```text
.
├── CLAUDE.md                # Master build specification and phased development plan
├── README.md                # This file
├── docs/                    # Architecture, database, AI, security, deployment, roadmap docs
├── backend/                 # Spring Boot backend (modular by business capability)
│   └── src/main/java/com/bizpilot/
│       ├── common/          # Shared utilities, base classes, cross-cutting config
│       ├── security/        # Authentication, authorization, JWT, security filters
│       ├── identity/        # Users, roles, permissions
│       ├── organization/    # Organizations, multi-tenancy
│       ├── crm/             # Customers
│       ├── sales/           # Leads, quotations, invoices
│       ├── products/        # Product catalog
│       ├── documents/       # Document upload, processing, storage
│       ├── ai/              # Spring AI integration, RAG, tool calling, assistant
│       ├── analytics/       # Dashboards, reporting
│       ├── tasks/           # Task management
│       ├── notifications/   # Notifications
│       └── audit/           # Audit logging
├── frontend/                # React + TypeScript SPA
│   └── src/
│       ├── components/      # Shared/reusable UI components
│       ├── layouts/         # Page layouts / shells
│       ├── pages/           # Route-level pages
│       ├── features/        # Feature modules (auth, dashboard, customers, leads, ...)
│       ├── hooks/           # Shared React hooks
│       ├── services/        # API clients
│       ├── types/           # Shared TypeScript types
│       └── utils/           # Shared utilities
├── infrastructure/          # Docker, CI/CD, and deployment-related assets
├── docker-compose.yml       # Local development environment
├── .env.example             # Environment variable template (no real secrets)
└── .gitignore
```

The backend module folders under `com/bizpilot/` are populated with real code starting in Phase 2 (currently just the application entry point and shared `common` foundation — business modules stay empty `.gitkeep` placeholders until their own phase). Frontend directories remain placeholders until Phase 20.

## Local Setup

Prerequisites:

- Java 21 (e.g. via `sdkman` or Temurin)
- Maven 3.9+ (or use the bundled `./mvnw` wrapper — no local Maven install required)
- Node.js 20+ and npm/pnpm (needed starting Phase 20 — not yet required)
- Docker and Docker Compose
- PostgreSQL client tools (optional, for local inspection)

> **Note for Valtech-managed machines:** this is a personal, non-client project. The backend's `backend/.mvn/settings.xml` + `backend/.mvn/maven.config` scope Maven builds in this repo to public Maven Central, so they don't depend on (or get blocked by) the corporate Nexus mirror configured in your global `~/.m2/settings.xml`. Nothing in your global Maven configuration is modified.

## Environment Variables

Copy the template and fill in real values locally — **never commit `.env`**:

```bash
cp .env.example .env
```

See [.env.example](.env.example) for the full list of supported variables (database, JWT, AI provider/API key, Redis, Kafka, storage, CORS, application URL). As of Phase 4, the backend reads `SERVER_PORT`, `SPRING_PROFILES_ACTIVE`, the database variables (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`), and the JWT variables (`JWT_SECRET`, `JWT_ACCESS_TOKEN_EXPIRATION_MINUTES`, `JWT_REFRESH_TOKEN_EXPIRATION_DAYS`). `DB_PASSWORD` and `JWT_SECRET` have no defaults — the app fails fast at startup without them (`JWT_SECRET` must additionally be at least 32 bytes for HS256 signing). The rest become relevant in later phases.

## Running the Backend

The backend needs a running PostgreSQL to start (Flyway migrations run on startup). Start it via Docker, then run the backend natively:

```bash
export DB_PASSWORD=changeme   # or set it in .env and use `docker compose --env-file .env up -d postgres`
export JWT_SECRET=$(openssl rand -base64 48)   # or any value >= 32 bytes; set in .env for reuse across restarts
docker compose up -d postgres
cd backend
DB_PASSWORD=$DB_PASSWORD JWT_SECRET=$JWT_SECRET ./mvnw spring-boot:run
```

The API starts on `http://localhost:8080` by default (override with `SERVER_PORT`). Verify it's up:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Active Spring profile defaults to `local` (override with `SPRING_PROFILES_ACTIVE`).

> If `localhost:5432` or `localhost:8080` is already in use by something else on your machine (another local Postgres install, another app), either stop that process or remap the published port in `docker-compose.yml` — this is a host-machine conflict, not a BizPilot AI issue.

### Authentication (Phase 4) & Organizations (Phase 5)

```bash
# Register (auto-provisions a new organization — see organizationName)
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"Passw0rd!","firstName":"You","lastName":"Test","organizationName":"Your Company"}'

# Login (returns accessToken + refreshToken)
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"Passw0rd!"}'

# Current user (replace $TOKEN with the accessToken above)
curl -s http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN"

# Current organization
curl -s http://localhost:8080/api/v1/organizations/current -H "Authorization: Bearer $TOKEN"

# Refresh (rotates the refresh token — the old one becomes invalid)
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"

# Logout (revokes the refresh token)
curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
```

New users register with `roles=["EMPLOYEE"]`, `status=ACTIVE`, and a brand-new organization (named after `organizationName`) by default — every other endpoint besides `/register`, `/login`, `/refresh`, and `/actuator/health` requires a valid `Authorization: Bearer <accessToken>` header. The current organization is always derived from that token, never from client input. See [docs/security.md](docs/security.md) for the full token/security/tenancy model.

### Customers / CRM (Phase 7)

```bash
# Create a customer (requires CUSTOMER_CREATE — the default EMPLOYEE role is read-only;
# see docs/security.md for the seeded role → permission mapping)
curl -s -X POST http://localhost:8080/api/v1/customers \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Jane Doe","company":"Acme Ltd","email":"jane@example.com"}'

# List/search/filter/paginate (status defaults to excluding ARCHIVED)
curl -s "http://localhost:8080/api/v1/customers?q=jane&page=0&size=20" -H "Authorization: Bearer $TOKEN"

# Add a note, then view notes/activities/history (replace $CUSTOMER_ID)
curl -s -X POST http://localhost:8080/api/v1/customers/$CUSTOMER_ID/notes \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"content":"Called, follow up next week"}'
curl -s http://localhost:8080/api/v1/customers/$CUSTOMER_ID/history -H "Authorization: Bearer $TOKEN"

# Archive (soft-delete; requires CUSTOMER_DELETE)
curl -s -X DELETE http://localhost:8080/api/v1/customers/$CUSTOMER_ID -H "Authorization: Bearer $TOKEN"
```

See [docs/security.md](docs/security.md) and [docs/database.md](docs/database.md) for the customer status model, archive/soft-delete design, and the activities/notes/history design decision.

### Leads (Phase 8)

```bash
# Create a lead (requires LEAD_CREATE — source is required, priority defaults to MEDIUM)
curl -s -X POST http://localhost:8080/api/v1/leads \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Jane Prospect","company":"Acme Prospects","source":"WEBSITE","priority":"HIGH"}'

# Assign (or unassign with "assigneeUserId": null) — the assignee must be in your organization
curl -s -X POST http://localhost:8080/api/v1/leads/$LEAD_ID/assign \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"assigneeUserId":"'"$USER_ID"'"}'

# Update status (an independent concept from archiving — see docs/database.md)
curl -s -X PATCH http://localhost:8080/api/v1/leads/$LEAD_ID \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"status":"CONTACTED"}'

# List/search/filter/paginate (archived defaults to excluding archived leads)
curl -s "http://localhost:8080/api/v1/leads?status=CONTACTED&priority=HIGH&page=0&size=20" -H "Authorization: Bearer $TOKEN"

# Archive (soft-delete via archivedAt, independent of status; requires LEAD_DELETE)
curl -s -X DELETE http://localhost:8080/api/v1/leads/$LEAD_ID -H "Authorization: Bearer $TOKEN"
```

See [docs/security.md](docs/security.md) and [docs/database.md](docs/database.md) for the lead identifying-fields decision, priority values, assignment/activity model, and the status-vs-archive design decision.

## Running the Frontend

Not yet available. Will be documented starting in Phase 20 once the Vite project is scaffolded (`cd frontend && npm install && npm run dev`).

## Running with Docker

`docker-compose.yml` provides local infrastructure (PostgreSQL/PGVector, Redis, Kafka) plus the backend service (built from `backend/Dockerfile`). The frontend service remains a placeholder until Phase 20:

```bash
cp .env.example .env   # at minimum set DB_PASSWORD and JWT_SECRET
docker compose up -d --build
curl http://localhost:8080/actuator/health
```

## Running Tests

From the `backend/` directory:

```bash
cd backend
./mvnw test           # unit/context tests
./mvnw clean verify   # full build + tests, produces target/bizpilot-backend.jar
```

Repository/persistence tests use [Testcontainers](https://testcontainers.com/) to start a real, throwaway PostgreSQL automatically — Docker must be running, but no manual database setup is needed; `DB_PASSWORD` and `JWT_SECRET` do not need to be set for `./mvnw test` (the `test` Spring profile supplies its own fixed, non-production JWT secret).

Frontend testing will be introduced starting in Phase 20/27.

## Deployment

See [docs/deployment.md](docs/deployment.md). Deployment pipelines and hardening are addressed in later phases (CI/CD in Phase 29, production hardening in Phase 30, deployment in Phase 31).

## Documentation

- [docs/architecture.md](docs/architecture.md) — System architecture and module design
- [docs/database.md](docs/database.md) — Database schema and conventions
- [docs/ai-architecture.md](docs/ai-architecture.md) — AI/RAG/tool-calling architecture
- [docs/security.md](docs/security.md) — Security model and requirements
- [docs/deployment.md](docs/deployment.md) — Deployment strategy
- [docs/roadmap.md](docs/roadmap.md) — Full phased development roadmap
- [CLAUDE.md](CLAUDE.md) — Master build specification driving this project

## Development Process

This project is built incrementally, one phase at a time, following the process and phase list defined in [CLAUDE.md](CLAUDE.md). Each phase is implemented, tested, security-reviewed, and documented before the next phase begins. See [docs/roadmap.md](docs/roadmap.md) for current progress.
