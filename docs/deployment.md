# Deployment

> Status: Phase 1 — describes the target deployment strategy. Actual Docker images, CI/CD pipelines, and production hardening are delivered in Phase 28, 29, and 30 respectively; this document is the reference those phases implement against.

## 1. Environments

- **Local development** — Docker Compose running PostgreSQL/PGVector, Redis, Kafka, plus the backend and frontend run either natively (`mvn spring-boot:run`, `npm run dev`) or as containers.
- **CI** — GitHub Actions builds, tests, and (later) performs security checks and Docker builds on every push.
- **Production** — containerized deployment of backend and frontend, backed by managed/production-grade PostgreSQL, Redis, and Kafka.

## 2. Local Development (Docker Compose)

`docker-compose.yml` at the repository root defines local infrastructure:

- `postgres` — PostgreSQL with the PGVector extension
- `redis` — caching, rate limiting
- `kafka` (+ dependency) — async/event-driven workflows
- `backend` — placeholder for the Spring Boot application (built once Phase 2 exists)
- `frontend` — placeholder for the Vite/React application (built once Phase 20 exists)

```bash
cp .env.example .env   # fill in local values, never commit .env
docker compose up -d
```

## 3. Containers

- **Backend:** multi-stage Docker build (Maven build stage → minimal JRE runtime stage), no build tools or source in the final image.
- **Frontend:** multi-stage Docker build (Node build stage producing static assets → served via a minimal web server), no Node toolchain in the final image.
- No secrets are baked into any image; all configuration is supplied via environment variables at container runtime.

## 4. CI/CD (planned — Phase 29)

```text
Push
 │
 ▼
Compile
 │
 ▼
Unit Tests
 │
 ▼
Integration Tests
 │
 ▼
Build
 │
 ▼
Docker Build
 │
 ▼
Security Checks
 │
 ▼
Deploy
```

Deployment never proceeds if any prior stage (tests, security checks) fails.

## 5. Database Migrations in Deployment

- Flyway migrations run automatically as part of application startup (or as an explicit pre-deploy step, to be decided in Phase 3/30).
- Production schema is **never** modified manually; every change is a versioned, immutable migration file.

## 6. Configuration & Secrets in Production

- All configuration (database credentials, JWT secret, AI provider/API key, Redis, Kafka, storage, CORS, application URL) is supplied via environment variables — see [.env.example](../.env.example).
- Real secrets are managed via the deployment platform's secret store (exact platform choice — cloud VM, PaaS, Kubernetes, etc. — to be decided; this document will be updated once chosen).
- Document/file storage uses a storage abstraction: local filesystem in development, S3-compatible object storage in production (see [architecture.md](architecture.md)).

## 7. Observability in Production

- Spring Actuator health/metrics endpoints exposed internally (not publicly, or protected if exposed).
- Metrics designed to be scraped by Prometheus and visualized in Grafana (introduced when the production environment is set up).
- Structured logs suitable for shipping to a centralized log aggregator.

## 8. Rollback Strategy

- Immutable Docker images tagged by commit/version, enabling rollback to a previous known-good image.
- Flyway migrations are additive/forward-only in normal operation; destructive schema changes require an explicit, reviewed migration plan (to be detailed once the schema exists, Phase 3+).

## 9. Open Decisions

The following are intentionally undecided at Phase 1 and will be resolved in later phases as the application matures:

- Target hosting platform (cloud VM / managed container service / Kubernetes).
- Managed vs. self-hosted PostgreSQL, Redis, and Kafka in production.
- Object storage provider for production document storage.
- Centralized logging/metrics stack specifics.
