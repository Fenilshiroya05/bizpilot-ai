# Architecture

> Status: Phase 18 — a new `POST /api/v1/leads/{id}/score` endpoint (on the existing `LeadController`, not a new `ai`-module endpoint) produces an advisory, AI-generated lead scoring assessment using Spring AI's structured output — the first non-prose AI capability in this codebase. Never persisted, never written back to the lead. Still stateless, still no mutating actions, no conversation persistence. Remaining business modules (`analytics`, `notifications`, `audit`) remain empty placeholders until their respective phases. No frontend code exists yet.

## 1a. Backend Foundation (Phase 2)

- Entry point: `com.bizpilot.BizPilotApplication` (`@SpringBootApplication`), positioned at the root package so component scanning covers every module package under `com.bizpilot.*` as they're populated in later phases.
- Configuration: `application.yml` (defaults, env-var driven) with a `local` profile (`application-local.yml`) active by default, and a `test` profile (`application-test.yml`) used by the test suite. No secrets or environment-specific URLs are hard-coded.
- Actuator: only the `health` endpoint is exposed, with `show-details: never` — no internal details leak through `/actuator/health`.
- `common/exception/GlobalExceptionHandler` + `common/response/ApiError` implement the centralized error-response contract defined in [security.md](security.md) (`timestamp`, `status`, `code`, `message`, `path`), with a validation-specific handler and a catch-all handler that logs server-side but never returns stack traces to the client.

## 1b. Database Foundation (Phase 3)

- Datasource, JPA/Hibernate, and Flyway are configured in `application.yml`, driven entirely by `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` environment variables — no defaults for `DB_PASSWORD`.
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate never creates/alters schema; Flyway (`backend/src/main/resources/db/migration/`) is the sole migration authority, per [database.md](database.md).
- `common/persistence/BaseEntity` + `common/config/JpaConfig` provide the UUID id / `created_at` / `updated_at` foundation every future entity builds on.
- Full details, including the deferred tenant-scoped base entity and the Testcontainers-based test setup, are in [database.md](database.md).

## 1c. Authentication & Identity Foundation (Phase 4)

- **`identity` module**: `User` entity (+ `UserRole`, `UserStatus` enums), `UserRepository`, `UserService` (registration business logic), `UserResponse`/`UserMapper` — the domain model for user accounts.
- **`security` module**: JWT issuance/validation (`jwt/JwtService`, `jwt/JwtProperties`), the stateless bearer-token filter chain (`SecurityConfig`, `JwtAuthenticationFilter`), refresh-token lifecycle (`RefreshTokenService`, `entity/RefreshToken`), and the auth REST API (`AuthController`, `AuthService`, `dto/*`).
- Controllers depend on services, services depend on repositories — same layering as every other module (CLAUDE.md §42). `AuthController` → `AuthService` → (`UserService`, `RefreshTokenService`, `JwtService`).
- `security.CurrentUserProvider` is the one place that reads `SecurityContextHolder` — other modules should depend on it rather than touching Spring Security directly (CLAUDE.md's "keep authentication concerns inside the security/identity layer").
- Full detail (token model, rotation/reuse-detection, enumeration resistance) is in [security.md](security.md).

## 1d. Organizations & Multi-Tenancy Foundation (Phase 5)

- **`organization` module**: `Organization` entity (`name` only), `OrganizationRepository`, `OrganizationService` (`create`, `getCurrentOrganization`), `OrganizationResponse`/`OrganizationMapper`, `OrganizationController` (`GET /api/v1/organizations/current` — the only endpoint), and `TenantContext` (the single reusable "what organization is this request for?" mechanism).
- `identity.User` now has a mandatory `organization` relationship (`organization_id`, NOT NULL, set once at creation). `security.AuthService.register()` auto-provisions a new `Organization` per signup, then creates the `User` with it — see `docs/security.md §3a` for why (no invite/join flow exists yet).
- **Cross-module dependency shape**: `identity` → `organization` (registration needs `OrganizationService` to provision a tenant) and `organization` → `security` (`TenantContext` needs `CurrentUserProvider`/`UserPrincipal`). `security` itself has no direct dependency on `organization` — `AuthService` only ever touches `Organization` indirectly, via `User.getOrganization().getId()`. This keeps the layering acyclic in practice even though, at the whole-system level, tenancy and identity are inherently intertwined.
- The JWT now carries an `orgId` claim alongside `sub`/`role`, so `TenantContext` never needs a database lookup to resolve the current tenant, consistent with the no-DB-hit-per-request design from Phase 4.
- Full detail (tenant resolution mechanism, isolation guarantees, the mandatory cross-tenant test scenario) is in [security.md](security.md) and [database.md](database.md).

## 1e. RBAC Foundation (Phase 6)

- **`identity` module** gains `Role`/`Permission` entities and `RoleRepository`/`PermissionRepository`. `User.roles` is now a `@ManyToMany` set (replacing the Phase 4 single `role` column); `Role.permissions` is a `@ManyToMany` set to `Permission`. Both relationships are `FetchType.LAZY`, accessed only inside an enclosing transaction (`open-in-view: false`).
- **No new module was introduced** — RBAC entities live in `identity` (which already owned `User`) rather than a separate `rbac` module; CLAUDE.md's module list has no dedicated RBAC package, and splitting it out would be a module purely for two small entity classes tightly coupled to `User`.
- **Authorization enforcement is Spring Security method security**, not a bespoke authorization service: `@PreAuthorize("hasAuthority('CUSTOMER_READ')")` for permission checks, `@PreAuthorize("hasRole('ADMIN')")` still available for admin-style checks — both read from the same `GrantedAuthority` set built once per request in `JwtAuthenticationFilter`. No separate `AuthorizationService` abstraction was introduced; Spring's own method security already covers every case this phase needs.
- **Authorities are resolved once, at token issuance** (`security.AuthService.resolveAuthorities`), from the authoritative DB state (`user.getRoles()` → `ROLE_<name>` + each role's `getPermissions()` → raw permission name), and embedded in the JWT's `authorities` claim. `JwtAuthenticationFilter` builds the request's `GrantedAuthority` set directly from that claim — zero database lookups per authenticated request, preserving the Phase 4 no-DB-hit-per-request design. This means a role/permission change takes effect only on the next login or `/refresh`, not on already-issued access tokens (bounded by the access token's short TTL) — see [security.md](security.md) for the full staleness rationale.
- **RBAC and tenant resolution are fully independent JWT claims** (`authorities` vs `orgId`) — a permission never provides an alternate path to another organization's data; both checks are required together, never substitutable for each other. Verified in `RbacAuthorizationTests.havingThePermissionNeverGrantsAccessToAnotherOrganizationsData`.
- No role/permission-assignment REST API was added this phase — CLAUDE.md doesn't assign such an endpoint to Phase 6, and adding one would need its own authorization/audit design. Role changes in this phase happen only via direct DB manipulation (as done in tests) or the Flyway seed data.
- Full detail (role/permission catalog, the role→permission mapping decision, privilege-escalation analysis) is in [security.md](security.md); schema detail is in [database.md](database.md).

## 1f. CRM Foundation — Customers (Phase 7)

- **`crm` module** is populated for the first time: `entity/Customer`, `entity/CustomerStatus`, `entity/CustomerActivity`, `entity/CustomerActivityType`, `repository/CustomerRepository`, `repository/CustomerActivityRepository`, `dto/*`, `mapper/CustomerMapper`, `mapper/CustomerActivityMapper`, `service/CustomerService`, `controller/CustomerController`, `exception/*`. Same layering as every other module: `Controller → Service → Repository → Entity`, DTOs/mappers at the boundary.
- **First real consumer of tenant isolation and RBAC together**: every `CustomerService` method resolves the organization via `organization.TenantContext`/`organization.service.OrganizationService` (never from client input) and every by-id lookup uses the tenant-safe `CustomerRepository.findByIdAndOrganizationId`, never a plain `findById`. Every `CustomerController` endpoint carries an explicit `@PreAuthorize("hasAuthority('CUSTOMER_*')")` using the Phase 6 permission catalog — no new roles/permissions were introduced.
- **One table backs three CLAUDE.md §10 features**: `CustomerActivity` (type `CREATED`/`STATUS_CHANGED`/`ARCHIVED`/`NOTE`) backs "activities," "notes," and "history" without a separate table per feature or any project-wide audit/event-sourcing framework — see [database.md](database.md) for the full reasoning, and [security.md](security.md) §7 for why this is explicitly *not* the still-unbuilt, general-purpose Audit Logging capability (CLAUDE.md §24).
- **Cross-module reuse, not duplication**: `CustomerService` obtains the current `Organization` entity via `organization.service.OrganizationService.getCurrentOrganization()` (going through that module's service, per CLAUDE.md §42), rather than reaching into `OrganizationRepository` directly.
- Full detail (status model, archive/soft-delete decision, uniqueness, validation, tenant-isolation/RBAC test coverage) is in [security.md](security.md) and [database.md](database.md).

## 1g. Sales Foundation — Leads (Phase 8)

- **`sales` module** is populated for the first time: `entity/Lead`, `entity/LeadStatus`, `entity/LeadSource`, `entity/LeadPriority`, `entity/LeadActivity`, `entity/LeadActivityType`, `repository/LeadRepository`, `repository/LeadActivityRepository`, `dto/*`, `mapper/LeadMapper`, `mapper/LeadActivityMapper`, `service/LeadService`, `service/LeadSearchCriteria` (an internal filter-bundling record, not a REST DTO), `controller/LeadController`, `exception/*`. Same layering and conventions as `crm` (Phase 7): `Controller → Service → Repository → Entity`, DTOs/mappers at the boundary, tenant-safe `findByIdAndOrganizationId` lookups everywhere, `@PreAuthorize("hasAuthority('LEAD_*')")` on every endpoint using the Phase 6 permission catalog.
- **New pattern beyond Phase 7: a cross-tenant-validated relationship to another entity.** Lead assignment references a `User` (via a plain `assignedToUserId` UUID, not a JPA relationship), and that reference must itself be tenant-validated — a candidate assignee must belong to the same organization as the lead. This required one small, additive extension to `identity.repository.UserRepository` (`findByIdAndOrganizationId`), following the exact same tenant-safe-lookup convention already established for `Customer`/`Lead` themselves, applied here to validating a *reference* rather than the primary resource.
- **Assignment is a dedicated action endpoint (`POST /api/v1/leads/{id}/assign`), not a field on the general update DTO** — a plain nullable field on a PATCH-semantics DTO can't distinguish "leave unchanged" from "unassign," so assignment needed its own unambiguous request/response contract instead of overloading `LeadUpdateRequest`.
- **Lead status and lead archival are two independent concerns**, unlike Customer (where `ARCHIVED` was one of the status values): CLAUDE.md §11 fixes `LeadStatus` to exactly 7 values with no archived state permitted, so `Lead` has both `status` (the fixed business-outcome enum) and a separate `archivedAt` timestamp (record-lifecycle state) — see [database.md](database.md) for the full reasoning.
- **No Lead → Customer relationship** — a deliberate decision, not an oversight; CLAUDE.md never mentions one, and the only prior hint (this doc's own Phase-1-era indicative relationships diagram) was a non-binding placeholder, now removed. See [database.md](database.md) §0e.
- Full detail (identifying-field decision, priority values, assignment/activity model, archive semantics, validation, tenant-isolation/RBAC test coverage) is in [security.md](security.md) and [database.md](database.md).

## 1h. Products Foundation (Phase 9)

- **`products` module** is populated for the first time: `entity/Product`, `entity/ProductCategory`, `entity/ProductStatus`, `repository/ProductRepository`, `repository/ProductCategoryRepository`, `dto/*`, `mapper/ProductMapper`, `mapper/ProductCategoryMapper`, `service/ProductService`, `service/ProductCategoryService`, `service/ProductSearchCriteria` (an internal filter-bundling record), `controller/ProductController` (`/api/v1/products`), `controller/ProductCategoryController` (`/api/v1/products/categories`), `exception/*`. Same layering and conventions as `crm`/`sales`.
- **First phase to extend the RBAC permission catalog itself, not just consume it.** CLAUDE.md §9 has no `PRODUCT_*` permission, but frames its permission list as "Examples:" — unlike the closed `LeadStatus`/`LeadSource` enums (§11). `V7__create_products.sql` adds `PRODUCT_READ`/`CREATE`/`UPDATE`/`DELETE` to the pre-existing `permissions` table and maps them to the existing 5 roles, carefully scoped so it can never collide with V4's already-committed `role_permissions` rows. See [security.md](security.md) §2d for the full reasoning.
- **Categories reuse the same `PRODUCT_*` permissions** — no dedicated category permission was introduced, consistent with the "don't invent unrequested scope" reasoning already applied in Phases 7–8.
- **`ProductCategoryController` is mounted at a literal path segment (`/api/v1/products/categories`) nested under `ProductController`'s base path** — Spring MVC resolves exact path segments ahead of path-variable segments (`/api/v1/products/{id}`), so there is no routing ambiguity; verified with a live request during manual validation.
- **No archive-lock, unlike Customer/Lead.** CLAUDE.md §13 names no delete/archive feature for products, only "CRUD" — `ProductStatus.INACTIVE` is a normal, freely-editable business state, and `ProductService` has no `rejectIfInactive`-style guard. This is a deliberate divergence from the Phase 7/8 archive convention, not an inconsistency — see [database.md](database.md) for the full reasoning.
- **Category is one-per-product, not many-to-many** — a plain nullable `category_id` FK, validated tenant-safe (a category from another organization can never be assigned to a product) exactly the way Lead's assignee reference is validated.
- Full detail (field decisions, SKU/price/tax rules, category relationship, delete semantics, validation, tenant-isolation/RBAC test coverage) is in [security.md](security.md) and [database.md](database.md).

## 1i. Sales Foundation — Quotations (Phase 10)

- **`sales` module** gains `entity/Quotation`, `entity/QuotationItem`, `entity/QuotationStatus`, `repository/QuotationRepository`, `dto/*`, `mapper/QuotationMapper`, `mapper/QuotationItemMapper`, `service/QuotationService`, `service/QuotationCalculator` (a pure, dependency-free calculation class), `service/QuotationSearchCriteria`, `service/QuotationPdfService`, `controller/QuotationController` (`/api/v1/quotations`), `exception/*`. Same layering and conventions as `crm`/`products`.
- **First entity referencing two other business modules at once.** `Quotation.customer` (`crm.entity.Customer`) and `QuotationItem.product` (`products.entity.Product`) are direct cross-module JPA `@ManyToOne` relationships — the established pattern (`identity.entity.User` → `organization.entity.Organization`) extended to a resource that genuinely needs two different foreign business entities, not a new architectural exception.
- **No new RBAC migration** — unlike Products (Phase 9), CLAUDE.md's original permission catalog already named `QUOTATION_READ`/`CREATE`/`UPDATE`/`DELETE`, and Phase 6's V4 migration already seeded them with the correct role mapping. Confirmed directly against the database before writing any code. See [security.md](security.md) §2e.
- **First backend-owned, persisted financial calculation chain**: `QuotationCalculator` (subtotal → discount → tax → grand total) is a plain, Spring-free class specifically so it stays trivially unit-testable — see [security.md](security.md) §3f and [database.md](database.md) §0g for the full precision/rounding/allocation reasoning.
- **Product snapshot rule, mirroring the general "point-in-time business document" principle**: `QuotationItem` copies the product's name/price/tax at creation/replacement time and never re-reads the live product afterward.
- **No archive field — CLAUDE.md's own status enum already has `CANCELLED`**, so "delete" is modeled as a status transition (`DELETE /api/v1/quotations/{id}`), not a new lifecycle column — unlike Lead, which needed a separate `archivedAt` because its status enum has no cancellation-equivalent value.
- **PDF generation is a dedicated, Spring-managed service (`QuotationPdfService`) using Apache PDFBox**, generated entirely on demand from the already-backend-calculated `Quotation` — never persisted, since no storage abstraction exists until the Documents module (Phase 13), and building one now would be speculative infrastructure ahead of its assigned phase.
- **List responses omit line items (`QuotationSummaryResponse`)**; only the single-resource endpoints (`get`/`create`/`update`) include the full item list (`QuotationResponse`), via a dedicated `JOIN FETCH` repository query — a deliberate response-shape split to avoid the well-known JPA "collection fetch join breaks pagination" trap. See [database.md](database.md) §0g.
- Full detail (field decisions, discount/tax model, snapshot rule, delete semantics, validation, tenant-isolation/RBAC test coverage) is in [security.md](security.md) and [database.md](database.md).

## 1j. Sales Foundation — Invoices (Phase 11)

- **`sales` module** gains `entity/Invoice`, `entity/InvoiceItem`, `entity/InvoiceStatus`, `repository/InvoiceRepository`, `dto/*`, `mapper/InvoiceMapper`, `mapper/InvoiceItemMapper`, `service/InvoiceService`, `service/InvoiceCalculator`, `service/InvoiceSearchCriteria`, `service/InvoicePdfService`, `controller/InvoiceController` (`/api/v1/invoices`), `exception/InvoiceNotFoundException`/`InvalidInvoiceDataException`/`InvoiceNotEditableException` — structurally a near-mirror of §1i's Quotation foundation, reusing the exact same shared `InvalidCustomerReferenceException`/`InvalidProductReferenceException` classes rather than duplicating them.
- **First resource with a hard, service-enforced immutability rule.** `InvoiceService.update` rejects the entire request with a `409 CONFLICT` (`InvoiceNotEditableException`) unless the invoice's current status is `DRAFT` — CLAUDE.md §15 states this rule three times (in the API, status-handling, and validation sections) without an explicit exception for the `status` field itself, so no partial "status-only" carve-out was built even though CLAUDE.md's immutability *field list* doesn't explicitly name `status`. See [security.md](security.md) §3h for the full reasoning and its one real consequence: no in-phase way exists to progress `ISSUED → PARTIALLY_PAID → PAID` once issued (only to `CANCELLED`).
- **First resource with a permission set that had to be created from scratch this phase** (unlike Quotation, Phase 10) — `INVOICE_READ`/`CREATE`/`UPDATE`/`DELETE`, added in `V9__create_invoices.sql` following the Phase 9/V7 precedent exactly.
- **No discount, no quotation reference** — both confirmed, deliberate scope exclusions (CLAUDE.md §15 never mentions either); `total = subtotal + taxAmount` only, and invoices are created independently of any quotation.
- **PDF generation (`InvoicePdfService`) reuses the exact Apache PDFBox usage pattern from `QuotationPdfService`**, including the WinAnsiEncoding-sanitization fix from Phase 10's security review — no new dependency was added.
- Full detail is in [security.md](security.md) §2f/§3h/§3i and [database.md](database.md) §0h.

## 1k. Task Foundation (Phase 12)

- **First business module in its own top-level package.** CLAUDE.md §42 assigns `tasks` a dedicated top-level package (alongside `documents`/`ai`/`analytics`/`notifications`/`audit`), unlike Quotations/Invoices, which were explicitly folded into `sales` by their own phase instructions. `tasks` gains `entity/Task`, `entity/TaskStatus`, `entity/TaskPriority`, `repository/TaskRepository`, `dto/*`, `mapper/TaskMapper`, `service/TaskService`, `service/TaskSearchCriteria`, `controller/TaskController` (`/api/v1/tasks`), `exception/TaskNotFoundException`/`InvalidTaskDataException`/`InvalidLeadReferenceException`.
- **First tenant-scoped entity with no cross-module JPA relationships beyond `Organization`.** `Task.assignedToUserId`/`customerId`/`leadId` are plain `UUID` columns, not `@ManyToOne` relationships — the same attribution-style-reference pattern already established by `sales.entity.Lead.assignedToUserId` (Phase 8), extended here to all three of a Task's references since nothing in this module ever needs to load the full `User`/`Customer`/`Lead` entity.
- **No immutability, unlike Invoice.** `TaskService.update` never rejects a request based on the task's current status — a task remains fully editable at every status, including reopening a `COMPLETED`/`CANCELLED` task back to `TODO`/`IN_PROGRESS` (an explicit, approved Phase 12 decision, deliberately the opposite of Invoice's `DRAFT`-only editability).
- **Dedicated assignment endpoint**, mirroring `LeadController`'s `/assign` exactly: `POST /api/v1/tasks/{id}/assign`, gated by `TASK_UPDATE` (no separate assign permission), supporting unassignment via `assigneeUserId: null`.
- **First resource with a non-default RBAC role mapping.** Unlike every prior resource (SALES = CRUD minus delete, EMPLOYEE = read-only), Tasks grant EMPLOYEE and SALES both `TASK_CREATE`/`TASK_UPDATE` — only `TASK_DELETE` (cancellation) is restricted to OWNER/ADMIN/MANAGER. An approved Phase 12 decision: a Task is a general-purpose operational to-do every role manages for themselves, not a sales/finance document.
- **No notes/activity table** — CLAUDE.md §23 lists a single "Notes" feature bullet with no accompanying "activities"/"history" bullet (contrast Lead/Customer, which each list both), so `notes` is a single plain text column on `Task` itself.
- Full detail is in [security.md](security.md) §2g/§3j and [database.md](database.md) §0i.

## 1l. Document Foundation (Phase 13)

- **`documents` module** gains `entity/Document`, `entity/DocumentStatus`, `repository/DocumentRepository`, `dto/DocumentResponse`, `mapper/DocumentMapper`, `service/DocumentService`, `service/DocumentValidator` (a pure, dependency-free upload-validation class, mirroring `sales.service.QuotationCalculator`'s architecture), `service/DocumentSearchCriteria`, `service/DocumentContent`, `service/DocumentStorageService` (the storage abstraction interface) + `LocalDocumentStorageService` (its only Phase 13 implementation), `controller/DocumentController` (`/api/v1/documents`), `exception/DocumentNotFoundException`/`InvalidDocumentException`/`DocumentStorageException`.
- **First module doing real file I/O.** `DocumentStorageService` is a small, swappable abstraction (CLAUDE.md §16: "so local filesystem can be used for development and S3-compatible storage can be used in production") — business logic in `DocumentService` depends only on the interface, never on `java.nio.file.Files` directly. Only a local-filesystem implementation is built now; no S3/MinIO client exists, and none is added to `docker-compose.yml` — building one now, with no production deployment target decided, would be speculative infrastructure.
- **First hard delete in the project.** Unlike Quotation/Invoice/Task's soft status-transition "delete," a deleted `Document`'s physical file and database row are both genuinely removed — CLAUDE.md §16 gives documents no status value analogous to `CANCELLED`, and the approved Phase 13 decision explicitly rules out inventing a soft-delete flag. Storage delete happens before the database row delete (the reverse of upload's ordering), making retries self-healing since storage deletion is idempotent — see [security.md](security.md) §3k.
- **First mandatory content-validation pipeline.** `DocumentValidator` checks filename extension, declared `Content-Type`, and (for PDF/DOCX) a magic-byte signature all agree, closing the gap where a client could rename an arbitrary file to `.pdf` and lie about its `Content-Type` simultaneously. No heavyweight content-sniffing dependency (e.g. Apache Tika) was added — three fixed, simple formats don't justify one.
- **No business-entity associations** — CLAUDE.md §16 names none (unlike Task's explicit "Related customer"/"Related lead"); a `Document` is a standalone, organization-owned file with only an attribution-style `uploadedByUserId` (plain UUID, mirroring `Lead.assignedToUserId`).
- **RBAC**: `DOCUMENT_READ`/`DOCUMENT_UPLOAD` already existed since Phase 6 (V4) and are unmodified; only `DOCUMENT_DELETE` is new (V11), restricted to OWNER/ADMIN/MANAGER — narrower than Task's unusually permissive mapping, since documents may hold sensitive business files.
- Full detail is in [security.md](security.md) §2h/§3k and [database.md](database.md) §0j.

## 1m. Spring AI Foundation (Phase 14)

- **`ai` module** gains `config/AiProperties`, `config/AiConfiguration`, `service/AiChatService` + `DefaultAiChatService`, `service/AiEmbeddingService` + `DefaultAiEmbeddingService`, `exception/AiProviderException`. No `controller/`, `repository/`, `entity/`, or `mapper/` package — there is no REST API, no persistence, and no entity in this phase.
- **BizPilot business code depends on `AiChatService`/`AiEmbeddingService`, never on `org.springframework.ai.*` types or a provider SDK directly** (CLAUDE.md §5). `DefaultAiChatService` wraps Spring AI's `ChatClient` (the fluent, higher-level API CLAUDE.md names explicitly); `DefaultAiEmbeddingService` wraps `EmbeddingModel`. Neither class branches on a provider name — Spring AI's own auto-configuration (`spring.ai.model.chat`/`spring.ai.model.embedding`) is the entire "provider selection" mechanism; BizPilot does not reimplement a provider registry.
- **OpenAI is the sole configured provider** (`spring-ai-starter-model-openai`, the only starter providing both `ChatModel` and `EmbeddingModel` from one artifact/one API key) — Spring AI 1.1.8, the last line still built against Spring Boot 3.4.x/3.5.x (2.x requires Spring Boot 4, out of scope). Swapping to Anthropic/Google/Ollama later is a `pom.xml` + configuration change only, per the locked architecture decision.
- **Disabled by default at two independent levels**: `bizpilot.ai.enabled=false` gates `DefaultAiChatService`/`DefaultAiEmbeddingService` via a single `@ConditionalOnProperty` each (no custom auto-configuration class); separately, Spring AI's own `spring.ai.model.chat`/`spring.ai.model.embedding` default to `none` in `application.yml`, so Spring AI's own `ChatClient`/`EmbeddingModel` auto-configuration doesn't even activate out of the box. Enabling AI requires setting both together (`AI_ENABLED=true` *and* `AI_CHAT_PROVIDER=openai`/`AI_EMBEDDING_PROVIDER=openai` plus a real `OPENAI_API_KEY`) — see the bug below for why the second layer is required, not redundant.
- **A real startup bug, caught by manual Docker validation (not by the test suite)**: `OpenAiChatAutoConfiguration`/`OpenAiEmbeddingAutoConfiguration` each build an `OpenAiApi` bean that validates a non-blank API key *eagerly*, at bean-creation time — not lazily at first call. Since these beans are gated by Spring AI's own `spring.ai.model.*` properties, not by `bizpilot.ai.enabled`, an initial config that defaulted `spring.ai.model.chat`/`embedding` to `openai` broke the whole application's startup with no key configured, in Docker specifically (the test suite's `application-test.yml` already forced these to `none` and so never caught it). `spring-ai-starter-model-openai` also auto-configures OpenAI text-to-speech/transcription/image/moderation, none of which CLAUDE.md asks for, and audio-speech has the identical eager-validation behavior. Fixed by defaulting `spring.ai.model.chat`/`embedding` to `none` (not `openai`) and disabling audio/image/moderation unconditionally — see [security.md](security.md) §3l for the full account.
- **Prompt management placeholder**: `backend/src/main/resources/prompts/.gitkeep` — no template content yet (nothing needs one before Phase 17); mirrors the same empty-placeholder convention already used for `ai`/`tasks`/`documents` Java packages before their own phases.
- Full detail is in [security.md](security.md) §2i/§3l and [docs/ai-architecture.md](ai-architecture.md).

## 1n. RAG Foundation (Phase 15)

- **`documents` module** gains `extraction/{DocumentTextExtractor, PdfTextExtractor, TxtTextExtractor, DocxTextExtractor, TextExtractionService}`, `processing/{DocumentUploadedEvent, DocumentProcessingListener, DocumentProcessingService, DocumentProcessingResultService, DocumentChunkingService, DocumentRecoveryScheduler, TextChunk}`, `config/{DocumentProcessingProperties, DocumentProcessingConfig}`, `entity/DocumentChunk`, `repository/DocumentChunkRepository`, `exception/DocumentProcessingException`. **`ai` module** gains `vectorstore/{DocumentVectorStoreService, DefaultDocumentVectorStoreService, VectorChunk}` and `retrieval/{DocumentRetrievalService, DefaultDocumentRetrievalService, RetrievedChunk}`, plus a batch method on the existing `AiEmbeddingService`. No `controller/` in either — still no REST API this phase.
- **Extraction**: PDFBox `PDFTextStripper` (already a dependency, Phase 10) for PDF; a plain UTF-8 read for TXT; Apache POI's `XWPFWordExtractor` (new, minimal dependency — not Apache Tika, for the same "three fixed formats don't justify a format-detecting library" reasoning as Phase 13's `DocumentValidator`) for DOCX. Dispatch is strict, by the document's already-validated `contentType` — never a permissive fallback.
- **Chunking**: Spring AI's own `TokenTextSplitter` (no custom algorithm), with the locked configuration (800-token chunks, 350 min chars, 5 min length-to-embed, 10000 max chunks, keep-separator) — no chunk overlap, since `TokenTextSplitter` has no configurable overlap as of 1.1.8 (verified against Spring AI's own issue tracker), an accepted trade-off, not an oversight.
- **Embedding**: `AiEmbeddingService` gained `embedBatch(List<String>)` (internal sub-batches of 100, never one OpenAI call per chunk), still `text-embedding-3-small`/1536 dimensions, still OpenAI-only.
- **Storage**: Spring AI's `PgVectorStore` for similarity search and delete; a `document_chunks` table (real, FK'd, Flyway-managed) for relational tenant/document integrity. **Vector-store writes deliberately bypass `VectorStore.add()`** — verified against 1.1.8 source that it always re-embeds internally via its own `EmbeddingModel`, which would silently double every embedding call if used alongside `AiEmbeddingService.embedBatch`; a plain, schema-matching `JdbcTemplate` insert is used instead, so every embedding call still goes through the one wrapper CLAUDE.md §5 requires. See [security.md](security.md) §3m for the full reasoning and [database.md](database.md) §0k for the schema.
- **Processing trigger**: `DocumentService.upload` publishes `DocumentUploadedEvent` (document id only) after its transaction commits; a `@TransactionalEventListener(AFTER_COMMIT)` dispatches to `DocumentProcessingService.process`, itself `@Async` on a dedicated bounded executor (2–4 threads, bounded queue — never the common pool, never unbounded). A `DocumentRecoveryScheduler` periodically re-triggers documents stuck in `UPLOADED`/`PROCESSING` past a configured threshold — the accepted mitigation for this trigger's known limitation (non-durable: a crash between commit and listener execution loses the event).
- **Concurrency/idempotency**: a single atomic `UPDATE ... WHERE status IN ('UPLOADED','FAILED')` (`DocumentRepository.transitionToProcessing`) is the sole concurrency guard — never an in-memory flag — safe across the async executor and the recovery scheduler racing for the same document. Every processing attempt (first-run or reprocess alike) clears any prior `document_chunks`/`vector_store` rows before rebuilding, so retrying is safe by construction.
- **Transaction boundaries**: only the atomic status transition and the final persist-results step (`DocumentProcessingResultService`, a separate bean from `DocumentProcessingService` specifically so its `@Transactional` methods are invoked through a real Spring proxy, not a self-invoked no-op) are transactional — extraction, chunking, and the OpenAI embedding call all happen with no open transaction.
- **Disabled by default, transitively.** All new beans in both modules are gated behind `bizpilot.ai.enabled=true` (unchanged from Phase 14) — additionally, `spring.ai.vectorstore.type` now defaults to `none` too (a second instance of Phase 14's exact startup-bug class, caught this time via source verification before ever running, not after a failure — see [security.md](security.md) §3m). `DocumentService` itself needs no conditional logic beyond an `ObjectProvider<DocumentVectorStoreService>` — publishing an event with no listener, or calling `ifAvailable` on an absent bean, are both safe no-ops.
- Full detail is in [security.md](security.md) §3m and [database.md](database.md) §0k.

## 1o. AI Assistant Foundation (Phase 16)

- **`ai` module gains `chat/{controller/AiAssistantController, dto/{AiChatRequest, AiChatResponse, AiChatSource}, AiAssistantService, DefaultAiAssistantService, AssistantContextBuilder, AssistantPromptService}`**, plus a new `config/AiHttpClientConfig` and `exception/AiDisabledException`. `AiChatService` (Phase 14) gains one new overload, `chat(String systemPrompt, String userMessage)` — the existing `chat(String prompt)` method's body is untouched (a second, separate method body, not a shared refactor, specifically so Phase 14's own tests/behavior can't regress).
- **Endpoint**: `POST /api/v1/ai/chat` (`AI_USE`-gated, the CLAUDE.md §28-reserved `/api/v1/ai` prefix) — the first real REST surface either `ai` submodule has ever exposed. Flow: retrieve (Phase 15's `DocumentRetrievalService`, unchanged, `topK=5`) → zero results → deterministic answer, LLM never called → build delimited context (`AssistantContextBuilder`) → chat with the fixed system prompt + question-and-context user message → map sources from the same `RetrievedChunk`s, never from model output.
- **System/user role separation is the actual prompt-injection control**, not wording alone — verified against Spring AI 1.1.8's `ChatClientRequestSpec` that `.system(String)` and `.user(String)` are genuinely distinct calls (mapped to the provider's own system/user message roles). The system prompt is a fixed, versioned resource (`prompts/assistant-system-v1.txt`, loaded once by `AssistantPromptService`) that never incorporates user input or retrieved content; document content and the user's question both live exclusively in the `.user(...)` call.
- **`DefaultAiAssistantService` always exists as a bean**, regardless of `bizpilot.ai.enabled` — its AI-gated collaborators (`DocumentRetrievalService`, `AiChatService`) are held via `ObjectProvider`, mirroring `DocumentService`'s existing pattern for `DocumentVectorStoreService` (Phase 15). AI disabled → `AiDisabledException` → `503`/`AI_DISABLED`, checked before any retrieval or provider call.
- **No new `GlobalExceptionHandler` mapping for missing tenant context** — a deliberate decision, not an oversight: `IllegalStateException` is thrown, unrelated, all over the codebase for generic "unreachable state" invariants (see `docs/security.md` §3n for the full reasoning); the existing generic `Exception.class` fallback already behaves correctly (fails closed, logs full details server-side, returns a safe generic 500) for this case without adding a new exception type.
- **LLM timeout**: verified no `spring.ai.openai.*` timeout property exists in 1.1.8; a `RestClient.Builder` bean (`AiHttpClientConfig`, using Spring Boot 3.5's `ClientHttpRequestFactoryBuilder`/`ClientHttpRequestFactorySettings`) bounds the OpenAI HTTP client to 30 seconds connect/read — the officially-supported customization point (`OpenAiChatAutoConfiguration.openAiApi(...)` takes `ObjectProvider<RestClient.Builder>`), not an invented property.
- **No new dependency, no new migration, no new RBAC permission** — `AI_USE` (existing since Phase 6) is reused as-is.
- Full detail is in [security.md](security.md) §3n.

## 1p. Read-Only AI Tool Calling (Phase 17)

- **`ai` module gains `tools/{CustomerTools, LeadTools, ProductTools, InvoiceTools}` and `tools/dto/*`**, plus a new `config/AiToolExecutionConfig`. `AiChatService` gains a second new overload, `chat(String systemPrompt, String userMessage, List<Object> tools)` — again a separate method body, not a refactor of the two existing overloads. `DefaultAiAssistantService` now constructor-injects all four tool beans directly (unconditional `@Component`s, no `ObjectProvider`) and passes the fixed four-tool list to every chat call.
- **Each tool class is a thin translation layer over an existing domain service** — `CustomerTools` → `CustomerService`, `LeadTools` → `LeadService`, `ProductTools` → `ProductService`, `InvoiceTools` → `InvoiceService` (via a new `InvoiceService.getOutstanding`, backed by an additive `statuses` filter on `InvoiceSearchCriteria`/`InvoiceRepository`) — never a parallel or duplicated business-logic path, and never direct repository access.
- **`@PreAuthorize` on each tool method is the real, empirically-verified enforcement mechanism** (see `docs/security.md` §3o) — `CUSTOMER_READ`/`LEAD_READ`/`PRODUCT_READ`/`INVOICE_READ` respectively, independent of `AI_USE`. Tenant scoping requires no tool-specific code at all: it's inherited from each domain service's own existing `TenantContext` resolution, since no tool accepts an organization/tenant parameter.
- **A custom `ToolExecutionExceptionProcessor` bean** (`AiToolExecutionConfig`, the same `@ConditionalOnMissingBean` override pattern as Phase 16's `RestClient.Builder`) replaces Spring AI's default, which would otherwise return a denied/failed tool call's raw exception message to the model.
- **No new REST endpoint** — the existing `POST /api/v1/ai/chat` is unchanged at the HTTP layer; tools are only reachable through it. **No new migration** (Flyway remains at `V12`), **no new RBAC permission**, **no new Maven dependency**.
- Full detail is in [security.md](security.md) §3o and [ai-architecture.md](ai-architecture.md) §4/§0.

## 1q. AI Lead Scoring (Phase 18)

- **`ai` module gains `scoring/{LeadScoringService, LeadScoringContextBuilder, LeadScoringPromptService, dto/{LeadScoreAiOutput, LeadScoreResponse}}`**, plus a new `exception/LeadScoringValidationException`. `AiChatService` gains a third new overload, `chatForStructuredOutput(String systemPrompt, String userMessage, Class<T> responseType)` — again a separate method body, not a refactor of the three existing overloads — built on Spring AI's `ChatClient.CallResponseSpec.entity(Class)` structured output (verified against actual 1.1.8 bytecode, not assumed from general docs).
- **`LeadScoringService` is a thin translation layer over the existing `LeadService`** — fetches the lead and its 5 most recent activities/notes via `LeadService.getById`/`getHistory` (unmodified, tenant-scoped), never `LeadRepository` directly; contains no call to any save/update method. `LeadScoringContextBuilder` is a pure, deterministic string builder (no AI logic, no database access) that turns the fetched data into a bounded, delimited user-message context.
- **Endpoint**: `POST /api/v1/leads/{id}/score` — deliberately on the existing `LeadController` (a lead-resource action, mirroring its existing `/{id}/assign`/`/{id}/notes` sub-resource endpoints), not a new `ai`-module endpoint, and not reachable through `POST /api/v1/ai/chat`. Requires both `LEAD_READ` and `AI_USE`.
- **AI output is untrusted and validated explicitly** — `LeadScoreAiOutput` (the raw structured-output target) declares every field as unvalidated/nullable; `LeadScoringService` checks score range, exact `LeadPriority` match, and bounded non-blank text before ever constructing the client-facing `LeadScoreResponse`. Any failure raises the new `LeadScoringValidationException` (`502`/`AI_SCORING_FAILED`) — never silently repaired.
- **Advisory only, verified live, not just by code inspection.** The response is never persisted and never written back to the `Lead` entity; a database-backed regression test reloads the lead after scoring and asserts every field, especially `priority`, is byte-for-byte unchanged.
- **No new REST endpoint beyond the one above, no new migration** (Flyway remains at `V12`), **no new RBAC permission** (reuses `LEAD_READ`/`AI_USE`), **no new Maven dependency**, **no change to** `POST /api/v1/ai/chat`, `DefaultAiAssistantService`, or the four Phase 17 tool classes.
- Full detail is in [security.md](security.md) §3p and [ai-architecture.md](ai-architecture.md) §6/§0.

## 1. Style

BizPilot AI is built as a **modular monolith** on the backend, not a microservices system. Business capabilities are separated into clearly bounded Java packages (modules) inside a single Spring Boot application. This gives most of the maintainability benefits of modular design (clear boundaries, independent evolution, testability) without the operational overhead of distributed systems, which is not justified at this stage.

The frontend is a single-page application (SPA) that talks to the backend exclusively through a versioned REST API.

## 2. High-Level Diagram (target state)

```text
                    ┌─────────────────────────────┐
                    │        Frontend (SPA)        │
                    │  React + TS + Vite + Tailwind│
                    └───────────────┬──────────────┘
                                    │ HTTPS / REST (JSON)
                                    ▼
                    ┌─────────────────────────────┐
                    │      Backend (Spring Boot)   │
                    │  ┌─────────────────────────┐ │
                    │  │ Web layer (Controllers)  │ │
                    │  ├─────────────────────────┤ │
                    │  │ Security (JWT, RBAC)     │ │
                    │  ├─────────────────────────┤ │
                    │  │ Service layer            │ │
                    │  ├─────────────────────────┤ │
                    │  │ AI layer (Spring AI)     │ │
                    │  ├─────────────────────────┤ │
                    │  │ Repository layer (JPA)   │ │
                    │  └─────────────────────────┘ │
                    └───┬──────────┬──────────┬────┘
                        │          │          │
                        ▼          ▼          ▼
                 ┌───────────┐ ┌───────┐ ┌──────────┐
                 │PostgreSQL │ │ Redis │ │  Kafka   │
                 │ +PGVector │ │       │ │          │
                 └───────────┘ └───────┘ └──────────┘
```

## 3. Backend Modules

Each module owns its own controller/service/repository/entity/dto/mapper/exception classes and should depend on other modules only through their public service interfaces — never reach into another module's repository or entity directly.

| Module | Responsibility |
|---|---|
| `common` | Shared utilities, base entities, pagination helpers, cross-cutting configuration |
| `security` | Authentication, JWT issuance/validation, security filters, password handling |
| `identity` | Users, roles, permissions, user-role assignment ✅ (Phase 6) |
| `organization` | Organizations and multi-tenancy context resolution |
| `crm` | Customers, customer activities/notes ✅ (Phase 7) |
| `sales` | Leads ✅ (Phase 8), quotations ✅ (Phase 10), invoices, sales pipeline |
| `products` | Product catalog, categories ✅ (Phase 9) |
| `documents` | Document upload, metadata, local storage abstraction ✅ (Phase 13); extraction/chunking/async processing pipeline ✅ (Phase 15) |
| `ai` | Spring AI integration: chat ✅/embeddings ✅ foundation (Phase 14); PGVector storage + tenant-safe retrieval ✅ (Phase 15); RAG assistant chat ✅ (Phase 16); read-only business-data tool calling ✅ (Phase 17); structured-output AI lead scoring ✅ (Phase 18); mutating tools, conversation memory deferred |
| `analytics` | Dashboards, aggregated reporting |
| `tasks` | Task management ✅ (Phase 12) |
| `notifications` | Notification delivery |
| `audit` | Audit logging for sensitive/important operations |

Layering within a module:

```text
Controller -> Service -> Repository -> Entity
                 │
                 ▼
                DTO / Mapper (never expose entities over REST)
```

## 4. Multi-Tenancy

BizPilot AI is multi-tenant: every business is an `Organization`, and every business record (customers, leads, products, quotations, documents, etc.) belongs to exactly one organization.

Principles:

- The current organization is **derived from the authenticated user's security context** on every request — never accepted as a client-supplied parameter.
- Tenant scoping is enforced at the **service and repository level**, not only via UI filtering.
- Every tenant-scoped repository query must filter by organization ID.
- Cross-tenant access (including vector search results in RAG) must be structurally prevented, not just tested for.
- Automated tests specifically targeting tenant isolation are mandatory (see [security.md](security.md)).

## 5. AI Layer

The AI layer sits behind the same service boundaries as the rest of the application:

- The AI assistant calls **application services**, never repositories directly.
- All AI tool calls are validated for authentication, organization scope, and permissions before execution, and are logged.
- Sensitive/irreversible actions require explicit user confirmation before the backend executes them.

Full detail in [ai-architecture.md](ai-architecture.md).

## 6. API Design

- Versioned REST APIs under `/api/v1/...` (e.g. `/api/v1/customers`, `/api/v1/ai`).
- Consistent JSON error envelope for all error responses (see [security.md](security.md)).
- DTOs at the API boundary; entities are never serialized directly.
- Pagination, filtering, and sorting are first-class concerns on list endpoints.

## 7. Cross-Cutting Concerns

- **Error handling:** centralized exception handling, no stack traces leaked to clients.
- **Observability:** Spring Actuator + Micrometer, structured logging, health checks (see [deployment.md](deployment.md)).
- **Rate limiting:** Redis-backed limits on authentication, AI, and upload endpoints.
- **Audit logging:** significant state-changing operations and AI tool executions are recorded (see [security.md](security.md)).

## 8. Frontend Architecture

- Feature-based organization under `frontend/src/features/*`, mirroring backend business capabilities.
- Shared UI primitives in `components/`, page shells in `layouts/`, routed views in `pages/`.
- Server state managed via TanStack Query; forms via React Hook Form + Zod validation.
- No business calculations (e.g. quotation/invoice totals) are performed on the frontend — the backend is always authoritative.
