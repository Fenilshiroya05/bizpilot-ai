# AI Architecture

> Status: Phase 15 — RAG ingestion (extraction/chunking/embedding/PGVector storage) and tenant-safe retrieval are now real. Tool calling, action confirmation, structured output/lead scoring, the AI assistant itself, and conversation persistence remain target architecture, not yet implemented — see §0 below for exactly what's real today.

## 0. Implementation Status (Phase 15)

What actually exists today, as opposed to the target design described in §1–§9 below:

- **Real (Phase 14)**: `ai/config/AiProperties` + `AiConfiguration`, `ai/service/AiChatService` + `DefaultAiChatService` (wraps Spring AI's `ChatClient`), `ai/service/AiEmbeddingService` + `DefaultAiEmbeddingService` (wraps `EmbeddingModel`), `ai/exception/AiProviderException`. Provider: OpenAI only (`spring-ai-starter-model-openai`, Spring AI 1.1.8). Disabled by default (`bizpilot.ai.enabled=false`, and Spring AI's own `spring.ai.model.chat`/`embedding` also default to `none`) — no OpenAI API key is required to build, test, or start the application.
- **Real (Phase 14)**: a `backend/src/main/resources/prompts/` placeholder directory exists (empty, `.gitkeep` only) as the future home for versioned prompt templates (§7), consistent with §7's "dedicated, versioned location within the `ai` module."
- **Real (Phase 15) — the RAG pipeline, §3, is now implemented, matching the design below almost exactly**: text extraction (PDF/TXT/DOCX) → `TokenTextSplitter` chunking (no overlap — a disclosed, accepted limitation of the pinned Spring AI version) → `AiEmbeddingService.embedBatch` → Spring AI `PgVectorStore` (`document_chunks` for relational integrity, `vector_store` for the actual vectors), triggered asynchronously after upload, with a recovery sweep for crash resilience. Retrieval (`ai/retrieval/DocumentRetrievalService`) is a tenant-scoped, fail-closed internal service — no REST endpoint yet, since nothing (no assistant, no tool calling) consumes it this phase.
- **Not yet real**: tool calling (§4), action confirmation (§5), structured output / lead scoring (§6), the AI assistant itself, conversation persistence, an AI REST endpoint, and any UI. These remain exactly as designed below.
- **Full implementation account**: see [architecture.md §1n](architecture.md) and [security.md §3m](security.md#3m-implementation-notes-phase-15--rag-tenant-isolation-and-a-second-instance-of-the-phase-14-startup-bug-class) (tenant isolation in full, the vector-store-write-bypasses-`VectorStore.add()` design decision, and a second instance of Phase 14's startup-bug class, this time caught via source verification before ever running).

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

The assistant uses Spring AI's tool-calling support to invoke a fixed set of backend tools rather than freeform actions. Planned initial tools:

- `searchCustomers`, `getCustomer`
- `searchLeads`, `getLead`
- `searchProducts`
- `getSalesSummary`
- `getOutstandingInvoices`
- `createTask`
- `createQuotation`
- `getCustomerHistory`

Every tool invocation must:

1. Validate input (schema/shape).
2. Validate the authenticated user and their organization.
3. Validate the caller has the required permission (RBAC).
4. Apply the same business rules the equivalent REST endpoint would apply.
5. Execute through the existing application **service** layer (never a repository directly).
6. Log the call (`ai_tool_calls`) — inputs, result, outcome.
7. Return a structured result to the model.

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
