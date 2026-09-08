# BizPilot AI

BizPilot AI is an AI-powered business operations platform for small and medium businesses (SMBs). It combines a traditional CRM/sales/operations backend with an AI assistant that can search business data, search uploaded documents (RAG), call approved backend tools, and create business records under explicit authorization and confirmation.

This is being built as a production-grade, multi-tenant SaaS application — not a prototype.

> **Status:** Phases 1–19 (backend) complete, production-audited, and tested. Phase 20 (frontend foundation) complete. See [docs/roadmap.md](docs/roadmap.md) for the full build plan and current progress.

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

### Products (Phase 9)

```bash
# Create a product category
curl -s -X POST http://localhost:8080/api/v1/products/categories \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"name":"Electronics"}'

# Create a product (requires PRODUCT_CREATE — SKU is normalized to uppercase, tax defaults to 0)
curl -s -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"sku":"widget-100","name":"Widget","unit":"pcs","price":19.99,"taxPercentage":18,"categoryId":"'"$CATEGORY_ID"'"}'

# List/search/filter/paginate (unlike Customers/Leads, INACTIVE products are not hidden by default)
curl -s "http://localhost:8080/api/v1/products?unit=pcs&status=ACTIVE&page=0&size=20" -H "Authorization: Bearer $TOKEN"

# Delete (soft — transitions to INACTIVE; freely reversible via PATCH, unlike Customer/Lead archiving)
curl -s -X DELETE http://localhost:8080/api/v1/products/$PRODUCT_ID -H "Authorization: Bearer $TOKEN"
```

See [docs/security.md](docs/security.md) and [docs/database.md](docs/database.md) for the SKU/price/tax decisions, the category relationship, and why Products have no archive mechanism.

### Quotations (Phase 10)

```bash
# Create a quotation (requires QUOTATION_CREATE — unitPrice/taxPercentage are
# always snapshotted from the product, never accepted from the client)
curl -s -X POST http://localhost:8080/api/v1/quotations \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"customerId":"'"$CUSTOMER_ID"'","discountPercentage":10,"items":[{"productId":"'"$PRODUCT_ID"'","quantity":2}]}'

# Update status (SENT/ACCEPTED/REJECTED/EXPIRED — CANCELLED is rejected here, see below)
curl -s -X PATCH http://localhost:8080/api/v1/quotations/$QUOTATION_ID \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"status":"SENT"}'

# List/search/filter/paginate
curl -s "http://localhost:8080/api/v1/quotations?status=DRAFT&customerId=$CUSTOMER_ID&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"

# Download the quotation as a PDF (generated on demand, never persisted)
curl -s http://localhost:8080/api/v1/quotations/$QUOTATION_ID/pdf -H "Authorization: Bearer $TOKEN" -o quotation.pdf

# Cancel (soft — transitions to the existing CANCELLED status; requires QUOTATION_DELETE)
curl -s -X DELETE http://localhost:8080/api/v1/quotations/$QUOTATION_ID -H "Authorization: Bearer $TOKEN"
```

Every monetary total (`subtotal`, `discountAmount`, `taxAmount`, `grandTotal`) is always calculated by the backend — there is no request field for any of them, and a raw request body that tries to include one is silently ignored. See [docs/security.md](docs/security.md) and [docs/database.md](docs/database.md) for the calculation formula, rounding strategy, product-snapshot rule, and the cross-tenant customer/product reference validation.

### Invoices (Phase 11)

```bash
# Create an invoice (requires INVOICE_CREATE — no discount field exists;
# unitPrice/taxPercentage are always snapshotted from the product)
curl -s -X POST http://localhost:8080/api/v1/invoices \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"customerId":"'"$CUSTOMER_ID"'","dueDate":"2026-10-01","items":[{"productId":"'"$PRODUCT_ID"'","quantity":2}]}'

# Update a DRAFT invoice — e.g. issue it (any other status is rejected here
# with 409 CONFLICT once the invoice has already left DRAFT)
curl -s -X PATCH http://localhost:8080/api/v1/invoices/$INVOICE_ID \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"status":"ISSUED"}'

# List/search/filter/paginate
curl -s "http://localhost:8080/api/v1/invoices?status=DRAFT&customerId=$CUSTOMER_ID&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"

# Download the invoice as a PDF (generated on demand, never persisted)
curl -s http://localhost:8080/api/v1/invoices/$INVOICE_ID/pdf -H "Authorization: Bearer $TOKEN" -o invoice.pdf

# Cancel (soft — transitions to the existing CANCELLED status; requires INVOICE_DELETE)
curl -s -X DELETE http://localhost:8080/api/v1/invoices/$INVOICE_ID -H "Authorization: Bearer $TOKEN"
```

Every monetary total (`subtotal`, `taxAmount`, `total`) is always calculated by the backend — same rule as Quotations. **Immutability**: once an invoice leaves `DRAFT` (e.g. via the `status:"ISSUED"` update above), it becomes fully locked — any further `PATCH` (customer, items, due date, or status) is rejected with `409 CONFLICT`/`INVOICE_NOT_EDITABLE`, regardless of which field is being changed. The only remaining operation on a non-DRAFT invoice is cancellation (`DELETE`), which never alters its financial contents. There is no discount field and no link to a quotation — see [docs/security.md](docs/security.md) and [docs/database.md](docs/database.md) for the full reasoning, including why CLAUDE.md's own wording on this point was read strictly.

### Tasks (Phase 12)

```bash
# Create a task (requires TASK_CREATE — every role, including EMPLOYEE and
# SALES, has this permission; only cancellation is restricted)
curl -s -X POST http://localhost:8080/api/v1/tasks \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Follow up with customer","priority":"HIGH","dueDate":"2026-10-01","customerId":"'"$CUSTOMER_ID"'"}'

# Assign (or unassign with assigneeUserId: null) — a dedicated endpoint,
# never the general update endpoint, to avoid "omitted = no change" vs.
# "null = unassign" ambiguity
curl -s -X POST http://localhost:8080/api/v1/tasks/$TASK_ID/assign \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"assigneeUserId":"'"$USER_ID"'"}'

# Update status (TODO/IN_PROGRESS/COMPLETED — CANCELLED is rejected here, see below).
# Unlike Invoices, a task remains fully editable at every status: reopening a
# COMPLETED or CANCELLED task via the same endpoint is a normal, supported update.
curl -s -X PATCH http://localhost:8080/api/v1/tasks/$TASK_ID \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"status":"COMPLETED"}'

# List/search/filter/paginate
curl -s "http://localhost:8080/api/v1/tasks?status=TODO&assignedToUserId=$USER_ID&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"

# Cancel (soft — transitions to the existing CANCELLED status; requires TASK_DELETE,
# restricted to OWNER/ADMIN/MANAGER)
curl -s -X DELETE http://localhost:8080/api/v1/tasks/$TASK_ID -H "Authorization: Bearer $TOKEN"
```

Tasks live in their own top-level `tasks` module (not `sales`/`crm`), per CLAUDE.md's own architecture section. The assignee, customer, and lead references are plain UUID fields validated tenant-safe on every write — never trusted from the client — but are not JPA relationships, since nothing here ever needs to load the full referenced entity. See [docs/security.md](docs/security.md) and [docs/database.md](docs/database.md) for the full reasoning behind the non-default RBAC mapping and the no-immutability decision.

### Documents (Phase 13)

```bash
# Upload a document (requires DOCUMENT_UPLOAD — PDF/TXT/DOCX only, up to 20 MB;
# multipart/form-data with a single "file" part, no JSON metadata)
curl -s -X POST http://localhost:8080/api/v1/documents \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@contract.pdf;type=application/pdf"

# List/search/filter/paginate
curl -s "http://localhost:8080/api/v1/documents?status=UPLOADED&contentType=application/pdf&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"

# Download (streamed; safe Content-Disposition using the original filename)
curl -s http://localhost:8080/api/v1/documents/$DOCUMENT_ID/download \
  -H "Authorization: Bearer $TOKEN" -o downloaded.pdf

# Delete (hard delete — the physical file and the metadata row are both
# removed; requires DOCUMENT_DELETE, restricted to OWNER/ADMIN/MANAGER)
curl -s -X DELETE http://localhost:8080/api/v1/documents/$DOCUMENT_ID -H "Authorization: Bearer $TOKEN"
```

Only PDF, TXT, and DOCX are accepted — the filename extension, declared `Content-Type`, and (for PDF/DOCX) a magic-byte signature must all agree, so an executable renamed to `.pdf` with a spoofed `Content-Type` header is still rejected. Storage is local filesystem only in this phase, behind a small, swappable `DocumentStorageService` abstraction (CLAUDE.md §16) — no S3/MinIO client exists yet, though the abstraction is designed so one can be added later without changing any calling code. Documents have no association with customers/leads/quotations/invoices/tasks (CLAUDE.md §16 names none) and no metadata-update endpoint. Document processing (text extraction, chunking, embeddings, vector storage) is explicitly a later phase (Phase 15, RAG) — this phase only stores and serves the raw file securely. See [docs/security.md](docs/security.md) and [docs/database.md](docs/database.md) for the full storage-isolation, upload-validation, and hard-delete reasoning.

### AI Foundation (Phase 14)

Phase 14 adds a minimal Spring AI foundation with **no REST endpoint and no persistence** — it exists purely so future phases (Phase 15 RAG, Phase 16 the AI assistant, Phase 17 read-only tool calling) have a working `AiChatService`/`AiEmbeddingService` to build on. There is nothing to `curl` yet.

- **Provider: OpenAI only**, via `spring-ai-starter-model-openai` (Spring AI 1.1.8, BOM-managed; Spring Boot stays at 3.5.16, unchanged). This is the only starter that provides both chat and embeddings from one artifact/API key.
- **Disabled by default.** Set `AI_ENABLED=true` in your local `.env` to activate BizPilot's own `DefaultAiChatService`/`DefaultAiEmbeddingService` beans. That alone is **not** sufficient — Spring AI's own model auto-configuration is independently gated by `AI_CHAT_PROVIDER`/`AI_EMBEDDING_PROVIDER`, which must also be set to `openai` (not left at their `none` default), together with a real `OPENAI_API_KEY`.
- With none of the above set (the out-of-the-box default), the application builds, tests, and starts exactly as before — no network call is made and no API key is required. This was verified live: starting the `backend` Docker container with zero AI environment variables and no API key configured starts cleanly with no errors.
- See [docs/ai-architecture.md](docs/ai-architecture.md) §0 for exactly what is and isn't implemented, and [docs/security.md](docs/security.md) §3l for a real startup bug (an eager API-key check in Spring AI's own OpenAI auto-configuration) that was caught and fixed during this phase.

### RAG Foundation (Phase 15)

Phase 15 adds document ingestion (text extraction → chunking → embeddings → PGVector) and tenant-safe semantic retrieval — still **no REST endpoint** (there's nothing new to `curl` — retrieval is an internal service Phase 16+'s assistant will call) and no chatbot/tool calling/conversation persistence.

- **Fully automatic once AI is enabled**: uploading a document (`POST /api/v1/documents`, unchanged from Phase 13) now also triggers background processing — text is extracted, split into ~800-token chunks, embedded in batches, and stored in PostgreSQL/pgvector. Poll `GET /api/v1/documents/{id}` and watch `status` move from `UPLOADED` → `PROCESSING` → `COMPLETED` (or `FAILED`).
- **A third variable is required to actually activate the pipeline**, on top of Phase 14's three — `AI_VECTORSTORE_TYPE=pgvector` (it defaults to `none`, for the identical "don't require a live backend at startup" reason `AI_CHAT_PROVIDER`/`AI_EMBEDDING_PROVIDER` do). All four together, plus a real key, are what's needed:
  ```
  AI_ENABLED=true
  AI_CHAT_PROVIDER=openai
  AI_EMBEDDING_PROVIDER=openai
  AI_VECTORSTORE_TYPE=pgvector
  OPENAI_API_KEY=sk-...your real key...
  ```
- With none of the above set (the out-of-the-box default), the application builds, tests, and starts exactly as before — an uploaded document simply stays `UPLOADED` forever (nothing processes it), no network call is made, and no API key is required. Verified live in Docker.
- **No new infrastructure** — PGVector runs inside the same `pgvector/pgvector:pg16` Postgres image already used since Phase 3; no separate vector database, no Ollama/proxy service.
- Tenant isolation is mandatory and enforced at query time, not as an afterthought — see [docs/security.md](docs/security.md) §3m for the full account, including two real bugs (a startup-configuration issue and a transaction-boundary issue) caught and fixed before this phase was reported complete.

### AI Assistant (Phase 16)

Phase 16 adds the first real AI endpoint — a RAG-only, stateless question-answering assistant grounded strictly in your organization's own ingested documents (Phase 15). No conversation history, no business tools, no ability to take any action.

```bash
# Requires AI_USE (already granted to every role) and the same four AI_*
# variables as Phase 15 (AI_ENABLED, AI_CHAT_PROVIDER, AI_EMBEDDING_PROVIDER,
# AI_VECTORSTORE_TYPE, all =openai/pgvector) plus a real OPENAI_API_KEY.
curl -s -X POST http://localhost:8080/api/v1/ai/chat \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"message": "What does our migration guide say about backups?"}'

# {"answer": "...", "sources": [{"documentId": "...", "documentName": "migration-guide.pdf", "chunkIndex": 3}]}
```

- **If nothing in your organization's documents is relevant**, the response is a deterministic `200` — `{"answer": "I couldn't find enough information in your organization's documents to answer that.", "sources": []}` — the LLM is never called for this case, by design (never a guessed answer).
- **If AI is disabled** (the default — same as Phase 14/15), the endpoint returns `503` with `{"code": "AI_DISABLED", ...}` immediately, no retrieval or OpenAI call attempted. Verified live in Docker with no `OPENAI_API_KEY` at all.
- **Retrieved document content is always treated as data, never as instructions** — enforced structurally via Spring AI's own system/user message role separation, not just prompt wording. See [docs/security.md](docs/security.md) §3n for the full account, including the live cross-tenant and prompt-injection test evidence.
- No conversation memory, no streaming, no tool calling, no business-data access (customers/leads/invoices/etc.) — this endpoint only ever answers from your organization's uploaded documents.

### Read-Only AI Tool Calling (Phase 17)

Phase 17 lets the same `/api/v1/ai/chat` endpoint answer business-data questions (not just document questions) by calling four read-only tools — `searchCustomers`/`getCustomer`/`getCustomerHistory`, `searchLeads`/`getLead`, `searchProducts`, `getOutstandingInvoices` — each gated by its own permission (`CUSTOMER_READ`/`LEAD_READ`/`PRODUCT_READ`/`INVOICE_READ`), independent of `AI_USE`. No configuration changes beyond what Phase 15/16 already require — enabling AI enables the tools automatically.

```bash
# Same AI_* variables as Phase 16. Note this endpoint still requires at
# least one retrieved document chunk before the model (and therefore any
# tool) is ever invoked — see the RAG note below.
curl -s -X POST http://localhost:8080/api/v1/ai/chat \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"message": "What are our outstanding invoices?"}'
```

- **Read-only, and a fixed set of four tools only** — no tool can create, update, or delete anything. `createTask`/`createQuotation`/`getSalesSummary` remain unbuilt (a later phase, alongside mandatory action confirmation for anything that mutates data).
- **Every tool independently enforces its own permission** via `@PreAuthorize`, empirically verified to be the real enforcement mechanism Spring AI's tool-calling machinery respects (not assumed to work) — see [docs/security.md](docs/security.md) §3o. A principal with `AI_USE` but not the matching `*_READ` permission is denied by the tool itself.
- **Tenant isolation requires no new code** — every tool calls the same tenant-scoped domain service the equivalent REST endpoint already uses, so a cross-tenant lookup by exact ID returns the tool's normal "not found" result, never another organization's data.
- **Fixed at 5 results per search tool**, never client/model-configurable. Free-text queries are length-bounded; IDs are validated as UUIDs.
- See [docs/ai-architecture.md](docs/ai-architecture.md) §0/§4 for exactly which tools exist today versus what remains planned.

### AI Lead Scoring (Phase 18)

Phase 18 adds a new, direct endpoint — `POST /api/v1/leads/{id}/score` — that produces an AI-generated scoring assessment for one existing lead: a score (0–100), a suggested priority, reasoning, and a recommended next action. This is the first *structured* (non-prose) AI capability in BizPilot — it is a separate endpoint from `/api/v1/ai/chat` and is not exposed as a chat tool.

```bash
# Requires LEAD_READ and AI_USE, plus the same AI_* variables as Phase 15/16/17.
curl -s -X POST http://localhost:8080/api/v1/leads/$LEAD_ID/score \
  -H "Authorization: Bearer $TOKEN"

# {"leadId": "...", "score": 78, "priority": "HIGH",
#  "reasoning": "...", "recommendedAction": "...", "generatedAt": "..."}
```

- **Advisory only, never persisted.** The response exists only in this HTTP call — nothing is written to any table, and `priority` here is the AI's *suggestion*, never the lead's actual, authoritative `priority` field. Live-verified: scoring a lead never changes any of its stored fields.
- **Requires both `LEAD_READ` and `AI_USE`** — neither permission alone is sufficient, matching Phase 17's precedent.
- **Built only from the lead's own existing data** — its fields plus its 5 most recent notes/activities (bounded and truncated) — never RAG, never another lead, never cross-organization data.
- **AI output is validated, never silently repaired.** An out-of-range score or an unrecognized priority value from the model is rejected outright (`502`/`AI_SCORING_FAILED`), never clamped or defaulted into something that merely looks valid.
- **No conversation/history** — a fresh assessment is generated on every call; nothing about a previous scoring request is remembered.
- See [docs/ai-architecture.md](docs/ai-architecture.md) §0/§6 and [docs/security.md](docs/security.md) §3p for the full account.

### Analytics Summary API (Phase 19)

Phase 19 adds the `analytics` module's first real code: `GET /api/v1/analytics/summary`, a read-only, deterministic, tenant-scoped business summary — no AI involved.

```bash
# Requires ANALYTICS_READ (seeded for every role — OWNER/ADMIN/MANAGER/SALES/EMPLOYEE).
curl -s http://localhost:8080/api/v1/analytics/summary \
  -H "Authorization: Bearer $TOKEN"

# {"totalCustomers": 12, "newLeads": 4, "qualifiedLeads": 2, "conversionRate": 40.00,
#  "revenue": 12500.0000, "outstandingInvoicesCount": 3, "outstandingInvoicesTotal": 4200.0000,
#  "pendingFollowUps": 2}
```

- **Every number is computed on demand by SQL aggregation** (`COUNT`/`SUM`) against existing Customer/Lead/Invoice data — nothing is cached, persisted, or estimated, and no entity list is ever loaded into memory.
- **Fixed definitions, not configurable**: "new leads" and "revenue" both use a fixed last-30-days window; there is no `?since=`/`?days=` parameter. Revenue means *collected* revenue — the sum of `PAID` invoices only, not all issued invoices.
- **No AI** — this endpoint works identically whether `AI_ENABLED` is set or not, and requires no `OPENAI_API_KEY`. "AI Business Insights" (natural-language commentary on these numbers) is explicitly deferred to a later phase.
- **No charts, no date ranges, no dashboard UI** — this phase is the summary-card numbers only; revenue trend/lead funnel/lead sources/pipeline/top-customers endpoints and the frontend dashboard itself (Phase 26) are separate, later phases.
- See [docs/architecture.md](docs/architecture.md) §1r and [docs/security.md](docs/security.md) §2j/§3q for the full account.

## Running the Frontend

Phase 20 scaffolds the frontend foundation: React + TypeScript + Vite, Tailwind CSS, the design system, the application shell (sidebar/top bar/mobile drawer), authentication, RBAC-aware navigation, and the dashboard KPI cards (`GET /api/v1/analytics/summary`). Phase 21 adds real Customers (`/customers`) and Leads (`/leads`) — list/search/filter/pagination, create/edit/archive, notes/history, self-assign/unassign, and an AI Lead Scoring panel on the Lead detail page (advisory only — see [docs/roadmap.md](docs/roadmap.md#phase-21--completed-scope) for the full account, including the locked "no teammate picker" decision: the backend has no user-directory endpoint, so lead assignment only ever acts on the current user's own id). Every remaining module (Products, Quotations, Invoices, Tasks, Documents, full AI Assistant) is still an honest "coming in a later phase" placeholder behind its real nav link — see [docs/roadmap.md](docs/roadmap.md) for the exact frontend phase plan.

```bash
cd frontend
npm install
cp .env.example .env.local   # VITE_API_BASE_URL=http://localhost:8080 by default
npm run dev                  # http://localhost:5173, matches the backend's CORS_ALLOWED_ORIGINS default
```

The backend must already be running (see [Running the Backend](#running-the-backend)) — the frontend has no mock/fake backend mode. Token storage is memory-only (never `localStorage`); a full page reload always returns to `/auth/login`, since the current backend hands both tokens back in a JSON body rather than an httpOnly refresh cookie.

```bash
npm run build   # tsc -b && vite build — production bundle in dist/
npm run test    # Vitest + React Testing Library
npm run lint    # ESLint
```

### Browser Smoke Tests (Playwright)

```bash
npm run test:e2e          # headless
npm run test:e2e:headed   # watch it run in a real browser window
npm run test:e2e:report   # open the last HTML report
```

`npm run test:e2e` starts the Vite dev server itself (Playwright's `webServer` config), waits until it's reachable, launches Chromium, runs the smoke suite (`frontend/e2e/`), captures screenshots at the required breakpoints into `test-results/screenshots/` (gitignored), generates an HTML report (`playwright-report/`, gitignored), and shuts the dev server down automatically — no manual browser or backend setup needed. Every test mocks the backend entirely at the browser network layer (`page.route`, see `e2e/mocks.ts`) with realistic fixtures for all seven analytics metrics; a real backend is never required for this suite, and no production/API code is touched by the mocks. This is a lightweight foundation-verification layer (auth, dashboard, navigation, responsive, basic accessibility) — the full Phase 26 E2E strategy (complete Customer/Lead/Quotation/Invoice/Document/AI flows, a role matrix, performance, visual regression) is a separate, later scope.

## Running with Docker

`docker-compose.yml` provides local infrastructure (PostgreSQL/PGVector, Redis, Kafka) plus the backend service (built from `backend/Dockerfile`). Uploaded documents (Phase 13) are persisted in a named `document_storage` volume mounted at `/app/uploads` inside the backend container, so they survive a container restart; no MinIO/S3 service is included. The frontend service remains a placeholder until Phase 20:

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
