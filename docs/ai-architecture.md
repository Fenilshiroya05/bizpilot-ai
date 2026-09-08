# AI Architecture

> Status: Phase 17 — `POST /api/v1/ai/chat` now supports read-only business-data tool calling in addition to RAG (§2's "search structured business data via approved tools" now has a real, if read-only, implementation). Stateless, no conversation persistence. Mutating tools (create/update/delete), action confirmation, structured output/lead scoring, and multi-tool orchestration beyond a fixed four-tool set remain target architecture, not yet implemented — see §0 below for exactly what's real today.

## 0. Implementation Status (Phase 17)

What actually exists today, as opposed to the target design described in §1–§9 below:

- **Real (Phase 14)**: `ai/config/AiProperties` + `AiConfiguration`, `ai/service/AiChatService` + `DefaultAiChatService` (wraps Spring AI's `ChatClient`), `ai/service/AiEmbeddingService` + `DefaultAiEmbeddingService` (wraps `EmbeddingModel`), `ai/exception/AiProviderException`. Provider: OpenAI only. Disabled by default — no OpenAI API key is required to build, test, or start the application.
- **Real (Phase 15)**: the full RAG pipeline (§3) — text extraction → `TokenTextSplitter` chunking (no overlap, a disclosed pinned-version limitation) → `AiEmbeddingService.embedBatch` → Spring AI `PgVectorStore`, triggered asynchronously after upload, with a recovery sweep. `ai/retrieval/DocumentRetrievalService` is tenant-scoped and fails closed.
- **Real (Phase 16)**: `ai/chat/{AiAssistantService, DefaultAiAssistantService, AssistantContextBuilder, AssistantPromptService, controller/AiAssistantController, dto/*}`. `AiChatService` gained a `chat(systemPrompt, userMessage)` overload — the system/user role separation is the real prompt-injection boundary (§8's "must never be able to alter system instructions" now has a concrete mechanism, not just a principle). The system prompt (§7) now lives at `prompts/assistant-system-v1.txt`. Sources (§2's "explain the source of important document-based answers") are backend-generated from the same `RetrievedChunk`s retrieval already returns — never LLM-generated.
- **Real (Phase 17) — read-only tool calling, previously entirely target architecture, is now a real, minimal implementation of §4's "search structured business data via approved tools"**: `ai/tools/{CustomerTools, LeadTools, ProductTools, InvoiceTools}` (all `@Component`, four `*_READ`-permission-gated tools total — see §4 below for the exact list and why the rest of §4's originally-planned list remains unbuilt). `AiChatService` gained a second overload, `chat(systemPrompt, userMessage, List<Object> tools)`; `DefaultAiAssistantService` passes the fixed four-tool-bean list to every chat call. A custom `ai/config/AiToolExecutionConfig` (`ToolExecutionExceptionProcessor`) ensures a denied/failed tool call never leaks a raw exception message/class to the model. Full account: [roadmap.md — Phase 17](roadmap.md).
- **Not yet real**: mutating tools (§4's `createTask`/`createQuotation`), `getSalesSummary` (an analytics aggregation, deliberately deferred), action confirmation (§5), structured output / lead scoring (§6), conversation persistence, streaming, multi-turn tool chaining, and any UI. The assistant today can retrieve documents (RAG) and read four kinds of business records (customers, leads, products, invoices) — it cannot create, update, delete, or take any action, by design (CLAUDE.md's own phase ordering).
- **Full implementation account**: see [architecture.md §1o](architecture.md) and [security.md §3n](security.md#3n-implementation-notes-phase-16--ai-assistant-role-separation-as-the-prompt-injection-boundary-and-the-deliberately-not-added-illegalstateexception-handler)/[§3o](security.md#3o-implementation-notes-phase-17--read-only-ai-tool-calling-preauthorize-verified-as-the-real-enforcement-mechanism) (role separation, the `@PreAuthorize`-on-tool-method security spike, and the `AI_USE`-is-insufficient design).

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

Where the AI produces structured output intended to inform (not directly become) business data — e.g. lead scoring (`score`, `priority`, `reasoning`, `recommended_action`) — that output:

- Is validated against an expected schema before use.
- Is stored/displayed as an AI-generated recommendation, kept **separate** from authoritative business data.
- Never overwrites authoritative fields without an explicit user action.

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
