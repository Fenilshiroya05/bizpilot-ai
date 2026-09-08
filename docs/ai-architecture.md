# AI Architecture

> Status: Phase 16 — the first real AI Assistant + RAG Chat capability exists: `POST /api/v1/ai/chat`. RAG-only (§2's "no hallucinated business data" now has a real enforcement point), stateless, no tool calling, no conversation persistence. Action confirmation, structured output/lead scoring, and any business-data tool remain target architecture, not yet implemented — see §0 below for exactly what's real today.

## 0. Implementation Status (Phase 16)

What actually exists today, as opposed to the target design described in §1–§9 below:

- **Real (Phase 14)**: `ai/config/AiProperties` + `AiConfiguration`, `ai/service/AiChatService` + `DefaultAiChatService` (wraps Spring AI's `ChatClient`), `ai/service/AiEmbeddingService` + `DefaultAiEmbeddingService` (wraps `EmbeddingModel`), `ai/exception/AiProviderException`. Provider: OpenAI only. Disabled by default — no OpenAI API key is required to build, test, or start the application.
- **Real (Phase 15)**: the full RAG pipeline (§3) — text extraction → `TokenTextSplitter` chunking (no overlap, a disclosed pinned-version limitation) → `AiEmbeddingService.embedBatch` → Spring AI `PgVectorStore`, triggered asynchronously after upload, with a recovery sweep. `ai/retrieval/DocumentRetrievalService` is tenant-scoped and fails closed.
- **Real (Phase 16) — the AI assistant, previously entirely target architecture, is now a real, minimal, RAG-only implementation**: `ai/chat/{AiAssistantService, DefaultAiAssistantService, AssistantContextBuilder, AssistantPromptService, controller/AiAssistantController, dto/*}`. `AiChatService` gained a `chat(systemPrompt, userMessage)` overload — the system/user role separation is the real prompt-injection boundary (§8's "must never be able to alter system instructions" now has a concrete mechanism, not just a principle). The system prompt (§7) now lives at `prompts/assistant-system-v1.txt`, the first real file in that placeholder directory. Sources (§2's "explain the source of important document-based answers") are backend-generated from the same `RetrievedChunk`s retrieval already returns — never LLM-generated.
- **Not yet real**: tool calling (§4), action confirmation (§5), structured output / lead scoring (§6), conversation persistence, streaming, and any UI. These remain exactly as designed below — the assistant today is strictly "retrieve, ground, answer, cite," with no capability to call a tool or take an action, by design (CLAUDE.md's own phase ordering).
- **Full implementation account**: see [architecture.md §1o](architecture.md) and [security.md §3n](security.md#3n-implementation-notes-phase-16--ai-assistant-role-separation-as-the-prompt-injection-boundary-and-the-deliberately-not-added-illegalstateexception-handler) (role separation verified against actual Spring AI 1.1.8 source, the documented `DOCUMENT_READ`/`AI_USE` coupling, and why no blanket `IllegalStateException` handler was introduced).

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
