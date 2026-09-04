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

Empty directories currently contain a `.gitkeep` placeholder only; no application code has been added yet.

## Local Setup

> These instructions describe the target workflow. Backend and frontend projects (Maven/`pom.xml`, Node/`package.json`) have not been generated yet — that happens in Phase 2 (backend foundation) and Phase 20 (frontend foundation).

Prerequisites (once later phases are implemented):

- Java 21 (e.g. via `sdkman` or Temurin)
- Maven 3.9+
- Node.js 20+ and npm/pnpm
- Docker and Docker Compose
- PostgreSQL client tools (optional, for local inspection)

## Environment Variables

Copy the template and fill in real values locally — **never commit `.env`**:

```bash
cp .env.example .env
```

See [.env.example](.env.example) for the full list of supported variables (database, JWT, AI provider/API key, Redis, Kafka, storage, CORS, application URL).

## Running the Backend

Not yet available. Will be documented starting in Phase 2 once the Spring Boot project is scaffolded (`cd backend && mvn spring-boot:run`).

## Running the Frontend

Not yet available. Will be documented starting in Phase 20 once the Vite project is scaffolded (`cd frontend && npm install && npm run dev`).

## Running with Docker

A baseline `docker-compose.yml` exists for local infrastructure (PostgreSQL/PGVector, Redis, Kafka) with placeholders for the backend and frontend services once they exist:

```bash
docker compose up -d
```

Backend/frontend Docker images and multi-stage builds will be added in Phase 28.

## Running Tests

Not yet available. Backend testing (JUnit 5, Mockito, Testcontainers) and frontend testing will be introduced alongside their respective modules and consolidated in Phase 27.

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
