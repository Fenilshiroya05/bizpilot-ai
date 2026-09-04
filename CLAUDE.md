# MASTER PROMPT — BUILD BIZPILOT AI

You are the lead software architect and senior full-stack engineer responsible for building a **production-ready SaaS web application** called **BizPilot AI**.

Do not build a prototype, toy application, proof of concept, or mock UI.

Build a real, maintainable, secure, testable and deployable application that can eventually be used by real businesses.

---

# 1. PRODUCT

## Product Name

BizPilot AI

## Product Description

BizPilot AI is an AI-powered business operations platform for small and medium businesses.

The application should allow a business to manage:

- Organizations
- Users
- Roles and permissions
- Customers
- Leads
- Products
- Sales pipeline
- Quotations
- Invoices
- Tasks
- Documents
- Business analytics
- AI assistant

The AI assistant should not be just a chatbot.

It must be able to:

- Understand business questions
- Search business data
- Search uploaded documents
- Use RAG
- Call approved backend tools
- Return structured information
- Create business records when authorized
- Ask for confirmation before sensitive/irreversible actions
- Explain the source of important document-based answers

---

# 2. IMPORTANT DEVELOPMENT RULE

DO NOT generate the entire application at once.

Build the application incrementally.

For every phase:

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

Never destroy or rewrite working functionality unnecessarily.

Before modifying an existing module, understand how it currently works.

---

# 3. TECHNOLOGY STACK

## Backend

Use:

- Java 21
- Spring Boot 3.x
- Spring Web
- Spring Security
- Spring Data JPA
- Hibernate
- Bean Validation
- Spring AI
- Spring Actuator
- Maven
- Flyway
- PostgreSQL
- PGVector
- Redis
- Apache Kafka

Do not use unnecessary frameworks.

Follow modern Spring Boot practices.

---

# 4. FRONTEND

Use:

- React
- TypeScript
- Vite
- Tailwind CSS
- shadcn/ui
- React Query / TanStack Query
- React Hook Form
- Zod
- Recharts

The UI must be:

- Responsive
- Clean
- Professional
- Accessible
- Desktop-friendly
- Mobile-friendly
- Consistent

Do not create a generic AI-looking interface.

The application should look like a serious modern SaaS product.

---

# 5. AI STACK

Use Spring AI as the AI abstraction layer.

The architecture must support configurable providers.

Support configuration for:

- OpenAI
- Anthropic
- Google Gemini
- Ollama/local models

Do not hard-code business logic to a single model provider.

Use Spring AI for:

- ChatClient
- Chat interactions
- Embeddings
- RAG
- Vector search
- Tool calling
- Structured output
- Conversation memory
- AI observability

The AI layer must be modular.

---

# 6. DATABASE

Use PostgreSQL.

Use PGVector for vector storage.

Use Flyway for database migrations.

Every database table must be designed carefully.

Initial entities should include:

- organizations
- users
- roles
- permissions
- user_roles
- customers
- leads
- lead_activities
- products
- product_categories
- quotations
- quotation_items
- invoices
- invoice_items
- tasks
- documents
- document_chunks
- conversations
- conversation_messages
- notifications
- audit_logs
- ai_tool_calls
- ai_usage

Use UUID identifiers where appropriate.

Use created_at and updated_at timestamps.

Use proper foreign keys.

Use indexes for frequently queried fields.

Do not expose database entities directly through REST APIs.

Use DTOs.

---

# 7. MULTI-TENANCY

BizPilot AI is a multi-tenant SaaS.

Every business belongs to an organization.

Example:

Organization A:

- Customers
- Leads
- Products
- Quotations
- Documents

Organization B:

- Customers
- Leads
- Products
- Quotations
- Documents

Organization A must NEVER be able to access Organization B data.

Implement tenant isolation at the application/service/repository level.

Never trust organization IDs sent directly by the frontend.

Derive the current organization from the authenticated user/security context.

Write automated tests specifically for tenant isolation.

---

# 8. AUTHENTICATION

Implement production-quality authentication.

Features:

- Registration
- Login
- Logout
- JWT access token
- Refresh token
- Password hashing
- Password validation
- Account status
- Email verification architecture
- Forgot password architecture
- Reset password architecture
- Session/token management

Never store plain-text passwords.

Use BCrypt or Argon2.

Never expose passwords or sensitive tokens in logs.

---

# 9. AUTHORIZATION

Implement RBAC.

Initial roles:

- OWNER
- ADMIN
- MANAGER
- SALES
- EMPLOYEE

Permissions should be granular.

Examples:

- CUSTOMER_READ
- CUSTOMER_CREATE
- CUSTOMER_UPDATE
- CUSTOMER_DELETE
- LEAD_READ
- LEAD_CREATE
- LEAD_UPDATE
- LEAD_DELETE
- QUOTATION_READ
- QUOTATION_CREATE
- QUOTATION_UPDATE
- QUOTATION_DELETE
- DOCUMENT_READ
- DOCUMENT_UPLOAD
- AI_USE
- USER_MANAGE

Backend authorization is mandatory.

Never rely only on frontend permission checks.

---

# 10. CRM MODULE

Implement customer management.

Features:

- Create customer
- Update customer
- Delete/archive customer
- Search customers
- Filter customers
- Pagination
- Customer details
- Customer activities
- Customer notes
- Customer history

Customer fields should include appropriate business information such as:

- Name
- Company
- Email
- Phone
- Address
- GSTIN
- Status
- Notes

Do not unnecessarily collect personal information.

---

# 11. LEAD MANAGEMENT

Implement:

- Lead creation
- Lead update
- Lead deletion/archive
- Lead assignment
- Lead status
- Lead source
- Lead priority
- Lead activities
- Follow-up date
- Notes
- Search
- Filtering
- Pagination

Lead statuses:

- NEW
- CONTACTED
- QUALIFIED
- PROPOSAL
- NEGOTIATION
- WON
- LOST

Lead sources:

- WEBSITE
- REFERRAL
- SOCIAL_MEDIA
- EMAIL
- PHONE
- OTHER

---

# 12. AI LEAD SCORING

Create an AI-powered lead scoring capability.

Example:

Lead:

Company: ABC Industries
Requirement: 500 units
Budget: ₹10 lakh

AI should produce structured output:

- score
- priority
- reasoning
- recommended_action

Do not allow arbitrary AI-generated values to be saved directly.

Validate structured AI output.

Keep AI-generated recommendations separate from authoritative business data.

---

# 13. PRODUCT MODULE

Implement:

- Product categories
- Products
- SKU
- Name
- Description
- Unit
- Price
- Tax percentage
- Active/inactive status

Features:

- CRUD
- Search
- Filter
- Pagination
- Product details

---

# 14. QUOTATION MODULE

Implement:

- Create quotation
- Update quotation
- View quotation
- Delete/cancel quotation
- Quotation items
- Customer association
- Product association
- Discount
- Tax
- Subtotal
- Grand total
- Status

Quotation statuses:

- DRAFT
- SENT
- ACCEPTED
- REJECTED
- EXPIRED
- CANCELLED

Generate professional quotation PDFs.

The calculation of totals MUST happen on the backend.

Never trust totals received from the frontend.

---

# 15. INVOICE MODULE

Implement:

- Invoice creation
- Invoice items
- Tax calculation
- Total calculation
- Status
- Due date
- Payment status
- PDF generation

Invoice statuses:

- DRAFT
- ISSUED
- PARTIALLY_PAID
- PAID
- OVERDUE
- CANCELLED

Again, all financial calculations must happen on the backend.

---

# 16. DOCUMENT MANAGEMENT

Users should be able to upload documents.

Supported initial formats:

- PDF
- TXT
- DOCX

Implement:

- File upload
- File metadata
- Organization ownership
- Secure storage abstraction
- Document processing
- Text extraction
- Chunking
- Embedding generation
- Vector storage
- Document status

Document statuses:

- UPLOADED
- PROCESSING
- COMPLETED
- FAILED

Do not store uploaded files directly in the database.

Create a storage abstraction so local filesystem can be used for development and S3-compatible storage can be used in production.

---

# 17. RAG

Implement production-quality RAG using Spring AI.

Flow:

User uploads document

↓

Extract text

↓

Clean text

↓

Split into chunks

↓

Generate embeddings

↓

Store vectors in PGVector

↓

User asks question

↓

Generate embedding

↓

Retrieve relevant chunks

↓

Send context to LLM

↓

Generate answer

The response should include document/source references where appropriate.

Prevent cross-tenant document retrieval.

A user from Organization A must never retrieve vectors belonging to Organization B.

---

# 18. AI BUSINESS ASSISTANT

Create a central AI assistant.

Example questions:

"How many leads did we receive this month?"

"Which leads haven't been contacted for 7 days?"

"Show my top customers."

"What are my unpaid invoices?"

"What was our revenue last month?"

"Summarize this document."

"Which customers have overdue payments?"

The AI should use appropriate backend tools instead of hallucinating database information.

---

# 19. AI TOOL CALLING

Implement controlled Spring AI tools.

Example tools:

- searchCustomers
- getCustomer
- searchLeads
- getLead
- searchProducts
- getSalesSummary
- getOutstandingInvoices
- createTask
- createQuotation
- getCustomerHistory

Tool execution must:

- Validate input
- Validate authenticated user
- Validate organization
- Validate permission
- Validate business rules
- Log the tool call
- Return structured results

AI must NEVER bypass application services and directly access repositories.

AI tools must use the same business services used by REST APIs.

---

# 20. AI ACTION CONFIRMATION

Sensitive actions require explicit confirmation.

Example:

User:

"Create a quotation for ABC Industries."

AI:

"I found ABC Industries and prepared the quotation."

Show:

Quotation
Subtotal
Tax
Discount
Total

Then:

"Do you want me to create this quotation?"

Buttons:

[Cancel]

[Confirm]

Only after confirmation should the backend execute the action.

The same rule applies to:

- Sending emails
- Sending quotations
- Deleting records
- Important data modifications

---

# 21. AI CHAT UI

Create a modern assistant interface.

Features:

- Conversation list
- New conversation
- Chat messages
- Streaming response where practical
- Tool execution indicators
- Sources
- Errors
- Retry
- Copy response
- Clear conversation
- Confirmation dialogs for actions

Example:

```text
You
Which leads need follow-up?

AI
I found 8 leads that haven't been contacted
for more than 7 days.

[View Leads]
```

---

# 22. BUSINESS DASHBOARD

Create dashboard cards:

- Total customers
- New leads
- Qualified leads
- Conversion rate
- Revenue
- Outstanding invoices
- Pending follow-ups

Charts:

- Revenue trend
- Lead funnel
- Lead sources
- Sales pipeline
- Top customers

Also add:

## AI Business Insights

Example:

"Revenue increased by 14% compared with the previous period."

"5 high-priority leads require follow-up."

"3 invoices are overdue."

AI insights must be based on actual business data.

Do not invent statistics.

---

# 23. TASK MANAGEMENT

Implement:

- Create task
- Assign task
- Due date
- Priority
- Status
- Related customer
- Related lead
- Notes

Statuses:

- TODO
- IN_PROGRESS
- COMPLETED
- CANCELLED

Priorities:

- LOW
- MEDIUM
- HIGH
- URGENT

---

# 24. AUDIT LOGGING

Implement audit logs for important operations.

Track:

- User
- Organization
- Action
- Entity type
- Entity ID
- Timestamp
- IP where appropriate
- Result

Examples:

- LOGIN
- CREATE_CUSTOMER
- UPDATE_CUSTOMER
- DELETE_CUSTOMER
- CREATE_QUOTATION
- AI_TOOL_EXECUTION
- DOCUMENT_UPLOAD

Never log:

- Passwords
- JWTs
- API keys
- Secrets
- Sensitive document contents

---

# 25. AI USAGE TRACKING

Track AI usage.

Store:

- Organization
- User
- Model
- Request type
- Input token count if available
- Output token count if available
- Estimated cost if available
- Duration
- Success/failure
- Timestamp

This will allow future SaaS billing and usage limits.

---

# 26. RATE LIMITING

Design rate limiting for:

- Login
- Registration
- Password reset
- AI endpoints
- File upload
- Public APIs

Use Redis where appropriate.

Return proper HTTP status codes.

---

# 27. ERROR HANDLING

Implement centralized exception handling.

Use consistent API error responses.

Example:

```json
{
  "timestamp": "...",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Invalid request",
  "path": "/api/customers"
}
```

Do not expose internal stack traces to clients.

---

# 28. REST API DESIGN

Use versioned APIs:

/api/v1/auth

/api/v1/users

/api/v1/organizations

/api/v1/customers

/api/v1/leads

/api/v1/products

/api/v1/quotations

/api/v1/invoices

/api/v1/tasks

/api/v1/documents

/api/v1/ai

/api/v1/analytics

/api/v1/audit

Use:

- Proper HTTP methods
- Proper status codes
- Pagination
- Filtering
- Sorting
- Validation
- DTOs

---

# 29. FRONTEND DESIGN

Create a professional SaaS layout.

Main navigation:

Dashboard
CRM
  Customers
  Leads

Sales
  Products
  Quotations
  Invoices

Tasks

Documents

AI Assistant

Analytics

Settings

The design should be:

- Minimal
- Modern
- Professional
- Fast
- Responsive

Avoid excessive gradients, animations, oversized cards and unnecessary visual effects.

---

# 30. FRONTEND SECURITY

Never store sensitive credentials insecurely.

Do not put secrets in frontend code.

Implement proper authentication handling.

Handle:

- 401
- 403
- 404
- 409
- 422
- 429
- 500

Display user-friendly error messages.

---

# 31. API DOCUMENTATION

Generate OpenAPI documentation.

Document:

- Authentication
- Request models
- Response models
- Error responses
- Pagination
- Security requirements

Keep documentation updated as APIs change.

---

# 32. TESTING

Testing is mandatory.

Backend:

- Unit tests
- Repository tests
- Service tests
- Controller tests
- Security tests
- Integration tests
- Multi-tenancy tests
- AI tool tests

Use:

- JUnit 5
- Mockito
- Testcontainers

Frontend:

- Unit tests
- Component tests
- API integration tests
- Critical end-to-end tests

Critical flows must have automated tests.

---

# 33. DATABASE MIGRATIONS

Use Flyway.

Never manually modify production schema.

Every schema change must be represented by a migration.

Example:

V1__initial_schema.sql

V2__add_customer_fields.sql

V3__add_ai_usage.sql

Keep migrations immutable.

---

# 34. DOCKER

Create Docker configuration for:

- Backend
- Frontend
- PostgreSQL
- PGVector
- Redis
- Kafka

Create:

docker-compose.yml

for local development.

Production containers should be optimized.

Use multi-stage Docker builds where appropriate.

Do not put secrets into Dockerfiles.

---

# 35. ENVIRONMENT CONFIGURATION

Create:

.env.example

Never commit real secrets.

Configuration should support:

- Database URL
- Database username
- Database password
- JWT secret
- AI provider
- AI API key
- Redis
- Kafka
- Storage
- CORS
- Application URL

Use environment variables.

---

# 36. CI/CD

Create GitHub Actions workflows.

Pipeline:

```text
Push
 ↓
Compile
 ↓
Unit Tests
 ↓
Integration Tests
 ↓
Build
 ↓
Docker Build
 ↓
Security Checks
 ↓
Deploy
```

Do not deploy if tests fail.

---

# 37. OBSERVABILITY

Use:

- Spring Actuator
- Micrometer
- Structured logging
- Health checks

Expose appropriate health information.

Never expose secrets through actuator endpoints.

Design the application so metrics can later be consumed by Prometheus/Grafana.

---

# 38. SECURITY REQUIREMENTS

Follow secure coding practices.

Protect against:

- SQL injection
- XSS
- CSRF where applicable
- Broken access control
- IDOR
- File upload attacks
- Prompt injection
- Data leakage
- Tenant isolation failures
- Excessive API usage

AI-specific security is mandatory.

Treat user-provided documents and prompts as untrusted input.

Never allow retrieved document content to override system security rules.

Never let the AI decide authorization.

Authorization must always happen in backend code.

---

# 39. PROMPT MANAGEMENT

Do not scatter large AI prompts throughout Java classes.

Create a maintainable prompt strategy.

Prompts should be:

- Versioned
- Testable
- Easy to modify
- Separated from business logic

Document important prompts.

---

# 40. AI HALLUCINATION CONTROL

For business data questions:

The AI must never invent:

- Customers
- Leads
- Revenue
- Invoice amounts
- Product prices
- Quotation totals
- Business statistics

If required data cannot be retrieved:

Say that the data could not be found.

Do not guess.

---

# 41. BUSINESS RULES

Important business calculations must be deterministic.

For example:

Quotation:

subtotal = sum(quantity × unitPrice)

discount = business rule

tax = backend calculation

total = backend calculation

AI may assist with understanding the request, but the backend owns the calculation.

---

# 42. PROJECT STRUCTURE

Use a clean modular backend architecture.

Suggested structure:

backend/

src/main/java/com/bizpilot/

    common/

    security/

    identity/

    organization/

    crm/

    sales/

    products/

    documents/

    ai/

    analytics/

    tasks/

    notifications/

    audit/

Each module should have appropriate:

- controller
- service
- repository
- entity
- dto
- mapper
- exception

Do not create unnecessarily deep package structures.

Frontend:

frontend/src/

    components/

    layouts/

    pages/

    features/

        auth/

        dashboard/

        customers/

        leads/

        products/

        quotations/

        invoices/

        documents/

        ai/

        tasks/

        analytics/

        settings/

    hooks/

    services/

    types/

    utils/

---

# 43. GIT

Use clean Git practices.

Commit examples:

feat: add customer management

feat: implement lead scoring

feat: add document ingestion

feat: implement spring ai rag

fix: prevent cross tenant document access

test: add quotation integration tests

Do not commit:

- .env
- API keys
- passwords
- generated secrets
- local database files

---

# 44. DOCUMENTATION

Maintain:

README.md

docs/

Architecture documentation
API documentation
Database documentation
AI documentation
Deployment documentation
Security documentation
Development guide

The README must explain:

- What BizPilot AI is
- Features
- Architecture
- Tech stack
- Local setup
- Environment variables
- Running backend
- Running frontend
- Running Docker
- Running tests
- Deployment

---

# 45. DEVELOPMENT PHASES

Build in this order.

## Phase 1

Repository structure + documentation + CLAUDE.md

## Phase 2

Backend foundation

## Phase 3

Database + Flyway

## Phase 4

Authentication

## Phase 5

Organizations + multi-tenancy

## Phase 6

RBAC

## Phase 7

Customers

## Phase 8

Leads

## Phase 9

Products

## Phase 10

Quotations

## Phase 11

Invoices

## Phase 12

Tasks

## Phase 13

Document management

## Phase 14

Spring AI foundation

## Phase 15

RAG + PGVector

## Phase 16

AI tool calling

## Phase 17

AI assistant

## Phase 18

AI lead scoring

## Phase 19

Analytics

## Phase 20

Frontend foundation

## Phase 21

Frontend authentication

## Phase 22

CRM UI

## Phase 23

Sales UI

## Phase 24

Document UI

## Phase 25

AI assistant UI

## Phase 26

Dashboard

## Phase 27

Testing

## Phase 28

Docker

## Phase 29

CI/CD

## Phase 30

Production hardening

## Phase 31

Deployment

---

# 46. FIRST TASK

Do NOT start implementing all modules.

Start only with Phase 1.

Your first task is to:

1. Create the project repository structure.
2. Create CLAUDE.md.
3. Create README.md.
4. Create docs/architecture.md.
5. Create docs/database.md.
6. Create docs/ai-architecture.md.
7. Create docs/security.md.
8. Create docs/deployment.md.
9. Create backend/frontend/infrastructure directory structure.
10. Create appropriate .gitignore.
11. Create .env.example.
12. Create a basic docker-compose.yml structure.
13. Document the development roadmap.

Do not implement business functionality yet.

After Phase 1 is complete:

- Check all files.
- Validate the structure.
- Report exactly what was created.
- Explain any architectural decisions.
- Wait for my instruction before starting Phase 2.

# IMPORTANT

Always prioritize:

Security > Correctness > Maintainability > Testability > Performance > Convenience.

Never hide errors.

Never fabricate successful implementation.

If something cannot be implemented correctly, explain why.

Do not replace real backend functionality with mock data.

Do not use hardcoded business data.

Do not use fake API responses.

Do not mark functionality as completed unless it actually works.

Build this as if the application will be reviewed by a senior engineering team and deployed to real customers.