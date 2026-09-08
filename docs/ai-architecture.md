# AI Architecture

> Status: Phase 18 — AI lead scoring exists: `POST /api/v1/leads/{id}/score` (§6's "Structured Output & AI-Assisted Data (e.g. Lead Scoring)" now has a real, minimal implementation). Advisory only — never persisted, never written back to the lead. Still no chat-tool exposure for scoring, no mutating tools, no action confirmation, no conversation persistence — see §0 below for exactly what's real today.

## 0. Implementation Status (Phase 18)

What actually exists today, as opposed to the target design described in §1–§9 below:

- **Real (Phase 14)**: `ai/config/AiProperties` + `AiConfiguration`, `ai/service/AiChatService` + `DefaultAiChatService` (wraps Spring AI's `ChatClient`), `ai/service/AiEmbeddingService` + `DefaultAiEmbeddingService` (wraps `EmbeddingModel`), `ai/exception/AiProviderException`. Provider: OpenAI only. Disabled by default — no OpenAI API key is required to build, test, or start the application.
- **Real (Phase 15)**: the full RAG pipeline (§3) — text extraction → `TokenTextSplitter` chunking (no overlap, a disclosed pinned-version limitation) → `AiEmbeddingService.embedBatch` → Spring AI `PgVectorStore`, triggered asynchronously after upload, with a recovery sweep. `ai/retrieval/DocumentRetrievalService` is tenant-scoped and fails closed.
- **Real (Phase 16)**: `ai/chat/{AiAssistantService, DefaultAiAssistantService, AssistantContextBuilder, AssistantPromptService, controller/AiAssistantController, dto/*}`. `AiChatService` gained a `chat(systemPrompt, userMessage)` overload — the system/user role separation is the real prompt-injection boundary (§8's "must never be able to alter system instructions" now has a concrete mechanism, not just a principle). The system prompt (§7) now lives at `prompts/assistant-system-v1.txt`. Sources (§2's "explain the source of important document-based answers") are backend-generated from the same `RetrievedChunk`s retrieval already returns — never LLM-generated.
- **Real (Phase 17)**: `ai/tools/{CustomerTools, LeadTools, ProductTools, InvoiceTools}` (all `@Component`, four `*_READ`-permission-gated tools total). `AiChatService` gained a second overload, `chat(systemPrompt, userMessage, List<Object> tools)`; `DefaultAiAssistantService` passes the fixed four-tool-bean list to every chat call. A custom `ai/config/AiToolExecutionConfig` (`ToolExecutionExceptionProcessor`) ensures a denied/failed tool call never leaks a raw exception message/class to the model.
- **Real (Phase 18) — structured AI output, previously entirely target architecture, is now a real, minimal implementation of §6, exercised by exactly one capability (lead scoring)**: `ai/scoring/{LeadScoringService, LeadScoringContextBuilder, LeadScoringPromptService, dto/{LeadScoreAiOutput, LeadScoreResponse}}`, plus `ai/exception/LeadScoringValidationException`. `AiChatService` gained a third overload, `chatForStructuredOutput(systemPrompt, userMessage, Class<T>)` — Spring AI's `ChatClient.CallResponseSpec.entity(Class)`, verified against the actual 1.1.8 bytecode to auto-attach a JSON schema format instruction to the request. Exposed via `POST /api/v1/leads/{id}/score` on the existing `LeadController` — a lead-resource action, not a new `ai`-module endpoint, and not reachable through `POST /api/v1/ai/chat`. The system prompt (§7) now also lives at `prompts/lead-scoring-system-v1.txt`.
- **Not yet real**: mutating tools (§4's `createTask`/`createQuotation`), `getSalesSummary` (an analytics aggregation, deliberately deferred), action confirmation (§5), a `scoreLead` chat tool (deliberately deferred — CLAUDE.md's own phase ordering keeps this a direct endpoint only, for now), persisted score history, conversation persistence, streaming, multi-turn tool chaining, and any UI. The assistant today can retrieve documents (RAG), read four kinds of business records (customers, leads, products, invoices), and — via a separate, direct endpoint, not through the assistant chat — produce an advisory lead score; it still cannot create, update, delete, or take any action, by design (CLAUDE.md's own phase ordering).
- **Full implementation account**: see [architecture.md §1o](architecture.md)/[§1p](architecture.md) and [security.md §3n](security.md#3n-implementation-notes-phase-16--ai-assistant-role-separation-as-the-prompt-injection-boundary-and-the-deliberately-not-added-illegalstateexception-handler)/[§3o](security.md#3o-implementation-notes-phase-17--read-only-ai-tool-calling-preauthorize-verified-as-the-real-enforcement-mechanism)/[§3p](security.md#3p-implementation-notes-phase-18--ai-lead-scoring-structured-output-as-an-advisory-only-boundary) (role separation, the `@PreAuthorize`-on-tool-method security spike, the `AI_USE`-is-insufficient design, and the advisory-only/mutation-safety boundary).

## 1. Principles

- **Spring AI is the abstraction layer.** Business code never talks to a specific model provider's SDK directly.
- **Provider-agnostic by design.** Configuration must support OpenAI, Anthropic, Google Gemini, and Ollama/local models without code changes — only configuration changes.
- **The AI never bypasses application services.** All data access and mutations performed by the AI go through the same service layer used by REST controllers.
- **The backend owns authorization and business rules.** The AI decides *what to ask for*; the backend decides *whether it's allowed and correct*.
- **No hallucinated business data.** If data can't be retrieved via a tool, the assistant must say so — never guess or invent figures.

## 2. Capabilities

The AI assistant is not a generic chatbot. It must be able to:

- Understand natural-language business questions
- Search structured business data via approved tools
- Search uploaded documents via RAG
- Call approved backend tools to fetch or create data
- Return structured, verifiable information
- Create business records **when authorized**, and only after confirmation for sensitive actions
- Explain the source of document-based answers (citations)

## 3. RAG Pipeline

```text
Upload document
      │
      ▼
Extract text (PDF / TXT / DOCX)
      │
      ▼
Clean text
      │
      ▼
Split into chunks
      │
      ▼
Generate embeddings (Spring AI EmbeddingModel)
      │
      ▼
Store vectors in PGVector, tagged with organization_id + document_id
      │
      ▼
User asks a question
      │
      ▼
Generate query embedding
      │
      ▼
Retrieve relevant chunks (vector search, scoped to organization_id)
      │
      ▼
Send retrieved context + question to the LLM (ChatClient)
      │
      ▼
Generate answer with source references
```

Tenant isolation is enforced at retrieval time: a similarity search **must** filter by the requesting user's `organization_id` before returning any chunk. A user from Organization A must never receive vectors belonging to Organization B, regardless of similarity score. This is treated as a security boundary, not a convenience filter — see [security.md](security.md).

## 4. Tool Calling

The assistant uses Spring AI's tool-calling support (`MethodToolCallbackProvider`, the mechanism behind `ChatClient.tools(Object...)`) to invoke a fixed set of backend tools rather than freeform actions.

**Implemented (Phase 17), all read-only:**

- `CustomerTools.searchCustomers`, `.getCustomer`, `.getCustomerHistory` — requires `CUSTOMER_READ`
- `LeadTools.searchLeads`, `.getLead` — requires `LEAD_READ`
- `ProductTools.searchProducts` — requires `PRODUCT_READ`
- `InvoiceTools.getOutstandingInvoices` — requires `INVOICE_READ`

**Planned, not yet implemented** (a later phase, alongside §5's confirmation flow, since every one of these either mutates data or aggregates across records):

- `getSalesSummary` (an analytics aggregation — deliberately deferred, not a §17 read-only single-entity lookup)
- `createTask`, `createQuotation` (mutating — require the §5 confirmation flow first)

Every tool invocation must, and every implemented tool above does:

1. Validate input (schema/shape) — free-text queries are checked non-blank and length-bounded; IDs must be valid UUIDs.
2. Validate the authenticated user and their organization — inherited for free from the domain service's own `TenantContext` resolution; no tool accepts an organization/tenant id as a parameter.
3. Validate the caller has the required permission (RBAC) — `@PreAuthorize("hasAuthority('...')")` directly on the tool method, empirically verified to be enforced when Spring AI invokes the bean (see [security.md §3o](security.md#3o-implementation-notes-phase-17--read-only-ai-tool-calling-preauthorize-verified-as-the-real-enforcement-mechanism)). Never `AI_USE` alone.
4. Apply the same business rules the equivalent REST endpoint would apply — each tool calls the same service method the REST controller calls (e.g. `CustomerService.search`), never a parallel implementation.
5. Execute through the existing application **service** layer (never a repository directly) — true for all four implemented tools.
6. Return a structured result to the model — a small tool-facing DTO (e.g. `CustomerToolResult`), never a raw JPA entity; not-found/cross-tenant lookups return a normal `found=false` result, never an exception, so a denied or missing record is never distinguishable from the other.
7. **Not yet implemented: a persisted `ai_tool_calls` audit table.** Phase 17 logs each call at `INFO` (tool name, result count, duration — metadata only, never query text or record contents) via the existing structured-logging convention; a dedicated database-backed audit trail remains target architecture for a later phase, consistent with `audit_logs` more broadly (see [security.md](security.md)).

## 5. Action Confirmation

Sensitive or irreversible actions (creating a quotation, sending an email/quotation, deleting a record, other significant data modification) follow a propose-then-confirm flow:

```text
User: "Create a quotation for ABC Industries."
AI:   "I found ABC Industries and prepared the quotation:
       Subtotal / Tax / Discount / Total ...
       Do you want me to create this quotation?"
       [Cancel]  [Confirm]
```

The backend only executes the action after explicit user confirmation is received; the confirmation step cannot be skipped by the model.

## 6. Structured Output & AI-Assisted Data (e.g. Lead Scoring)

**Implemented (Phase 18)**: AI lead scoring — `POST /api/v1/leads/{id}/score` (`LEAD_READ` + `AI_USE`). Given one existing lead's fields plus its 5 most recent activities/notes (via the existing `LeadService`, bounded and truncated), the model returns a structured assessment (`score` 0–100, `priority`, `reasoning`, `recommendedAction`), built with Spring AI's `ChatClient.CallResponseSpec.entity(Class)` structured output — the first use of this capability in the codebase.

Where the AI produces structured output intended to inform (not directly become) business data — lead scoring is the one instance of this today — that output:

- **Is validated against an expected schema before use.** `LeadScoringService` checks every field explicitly: `score` must be an integer in `[0, 100]`; `priority` (received as a raw string, never trusted as a pre-validated enum) must exactly match an existing `LeadPriority` value; `reasoning`/`recommendedAction` must be non-blank and under a fixed length bound. Any failure raises `LeadScoringValidationException` (`502`/`AI_SCORING_FAILED`) — the value is never clamped, defaulted, or otherwise silently repaired into something merely valid-looking.
- **Is displayed only, never stored, in this phase** — kept **separate** from authoritative business data by construction: the response exists solely in the HTTP response body; nothing is written to any table. A persisted score/history table remains a possible, separately-scoped future phase, not built here.
- **Never overwrites authoritative fields under any circumstance, let alone without explicit user action.** There is no code path from `LeadScoringService` to any `LeadService`/`LeadRepository` save or update method — verified by a live, database-backed regression test that reloads the lead after scoring and asserts every field, especially `priority`, is byte-for-byte unchanged.

## 7. Prompt Management

- Prompts are not scattered inline across Java classes.
- Prompts live in a dedicated, versioned location within the `ai` module, are testable independently of the surrounding code, and are documented.
- Changes to prompts are tracked like any other code change (reviewed, versioned).

## 8. AI-Specific Security

Covered in detail in [security.md](security.md), but notably:

- User-provided documents and prompts are treated as **untrusted input**.
- Retrieved document content can never override system security rules or instructions (prompt-injection resistance).
- The AI never makes authorization decisions — authorization is always enforced in backend code, independent of what the model outputs.

## 9. Observability & Cost Tracking

Every AI request records: organization, user, model, request type, input/output token counts (where available), estimated cost (where available), duration, success/failure, and timestamp (`ai_usage` table) — enabling future usage limits and billing.
