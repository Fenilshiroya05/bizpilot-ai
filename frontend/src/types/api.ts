/**
 * Types in this file mirror the actual backend DTOs (`com.bizpilot.*.dto.*`)
 * field-for-field, verified against the current controller/DTO source —
 * never invented or assumed. Extend this file only when a new backend DTO
 * is actually consumed; do not pre-declare types for endpoints the frontend
 * doesn't call yet.
 */

// ---- common/response/ApiError.java ----
export interface ApiErrorBody {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
  fieldErrors?: Record<string, string>
}

// ---- Spring Data's native Page<T> JSON shape ----
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
  empty: boolean
}

// ---- security/dto/* ----
export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  organizationName: string
}

export interface RefreshTokenRequest {
  refreshToken: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
}

// ---- identity/dto/UserResponse.java ----
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED'

export interface UserResponse {
  id: string
  email: string
  firstName: string
  lastName: string
  roles: string[]
  status: UserStatus
  organizationId: string
  createdAt: string
}

// ---- organization/dto/OrganizationResponse.java ----
export interface OrganizationResponse {
  id: string
  name: string
  createdAt: string
}

// ---- analytics/dto/AnalyticsSummaryResponse.java ----
// Jackson serializes BigDecimal fields as bare JSON numbers (e.g. 40.00,
// 12500.0000 — see the example response in README.md's Phase 19 section),
// not as strings. `number` here is therefore the accurate wire type; the
// backend's NUMERIC(19,4) precision is preserved on the wire, but any value
// this large is far below JS's safe-integer/float precision limits, so no
// information is actually lost by JSON.parse. The rule this app follows is
// narrower and still correct: never re-derive or recompute one of these
// figures client-side — only format the number the backend already computed
// for display (see lib/money.ts).
export interface AnalyticsSummaryResponse {
  totalCustomers: number
  newLeads: number
  qualifiedLeads: number
  conversionRate: number
  revenue: number
  outstandingInvoicesCount: number
  outstandingInvoicesTotal: number
  pendingFollowUps: number
}

// ---- crm/entity/CustomerStatus.java ----
export type CustomerStatus = 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'

// ---- crm/entity/CustomerActivityType.java ----
export type CustomerActivityType = 'CREATED' | 'STATUS_CHANGED' | 'ARCHIVED' | 'NOTE'

// ---- crm/dto/CustomerResponse.java ----
export interface CustomerResponse {
  id: string
  name: string
  company: string | null
  email: string | null
  phone: string | null
  address: string | null
  gstin: string | null
  status: CustomerStatus
  notes: string | null
  organizationId: string
  createdAt: string
  updatedAt: string
}

// ---- crm/dto/CustomerCreateRequest.java ----
export interface CustomerCreateRequest {
  name: string
  company?: string
  email?: string
  phone?: string
  address?: string
  gstin?: string
  notes?: string
}

// ---- crm/dto/CustomerUpdateRequest.java (PATCH: omitted/undefined = unchanged) ----
export interface CustomerUpdateRequest {
  name?: string
  company?: string
  email?: string
  phone?: string
  address?: string
  gstin?: string
  notes?: string
  status?: 'ACTIVE' | 'INACTIVE'
}

// ---- crm/dto/CustomerNoteRequest.java ----
export interface CustomerNoteRequest {
  content: string
}

// ---- crm/dto/CustomerActivityResponse.java ----
export interface CustomerActivityResponse {
  id: string
  type: CustomerActivityType
  content: string
  createdByUserId: string
  createdAt: string
}

export interface CustomerListParams {
  q?: string
  status?: CustomerStatus
  page?: number
  size?: number
  sort?: string
}

// ---- sales/entity/{LeadStatus,LeadSource,LeadPriority}.java ----
export type LeadStatus = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'PROPOSAL' | 'NEGOTIATION' | 'WON' | 'LOST'
export type LeadSource = 'WEBSITE' | 'REFERRAL' | 'SOCIAL_MEDIA' | 'EMAIL' | 'PHONE' | 'OTHER'
export type LeadPriority = 'LOW' | 'MEDIUM' | 'HIGH'

// ---- sales/entity/LeadActivityType.java ----
export type LeadActivityType = 'CREATED' | 'STATUS_CHANGED' | 'ASSIGNED' | 'ARCHIVED' | 'NOTE'

// ---- sales/dto/LeadResponse.java ----
export interface LeadResponse {
  id: string
  name: string
  company: string | null
  email: string | null
  phone: string | null
  status: LeadStatus
  source: LeadSource
  priority: LeadPriority
  followUpDate: string | null
  assignedToUserId: string | null
  archivedAt: string | null
  organizationId: string
  createdAt: string
  updatedAt: string
}

// ---- sales/dto/LeadCreateRequest.java ----
export interface LeadCreateRequest {
  name: string
  company?: string
  email?: string
  phone?: string
  source: LeadSource
  priority?: LeadPriority
  followUpDate?: string
}

// ---- sales/dto/LeadUpdateRequest.java (PATCH: omitted/undefined = unchanged) ----
export interface LeadUpdateRequest {
  name?: string
  company?: string
  email?: string
  phone?: string
  status?: LeadStatus
  source?: LeadSource
  priority?: LeadPriority
  followUpDate?: string
  clearFollowUpDate?: boolean
}

// ---- sales/dto/LeadAssignRequest.java ----
export interface LeadAssignRequest {
  assigneeUserId: string | null
}

// ---- sales/dto/LeadNoteRequest.java ----
export interface LeadNoteRequest {
  content: string
}

// ---- sales/dto/LeadActivityResponse.java ----
export interface LeadActivityResponse {
  id: string
  type: LeadActivityType
  content: string
  createdByUserId: string
  createdAt: string
}

export interface LeadListParams {
  q?: string
  status?: LeadStatus
  source?: LeadSource
  priority?: LeadPriority
  assignedToUserId?: string
  unassigned?: boolean
  followUpBefore?: string
  archived?: boolean
  page?: number
  size?: number
  sort?: string
}

// ---- ai/scoring/dto/LeadScoreResponse.java ----
export interface LeadScoreResponse {
  leadId: string
  score: number
  priority: LeadPriority
  reasoning: string
  recommendedAction: string
  generatedAt: string
}

// ---- products/entity/ProductStatus.java ----
export type ProductStatus = 'ACTIVE' | 'INACTIVE'

// ---- products/dto/ProductResponse.java ----
// Response decimal fields are plain JSON numbers (same Jackson BigDecimal
// convention as every other response in this app — see AnalyticsSummaryResponse's
// note above). Request decimal fields below are deliberately typed `string`
// instead: Jackson's BigDecimal deserializer accepts a JSON string value
// directly and parses it exactly, so submitting the validated string the
// user typed (never `Number(string)` first) means zero JS floating-point
// round-trip for anything actually sent to the backend.
export interface ProductResponse {
  id: string
  sku: string
  name: string
  description: string | null
  unit: string
  price: number
  taxPercentage: number
  status: ProductStatus
  categoryId: string | null
  organizationId: string
  createdAt: string
  updatedAt: string
}

// ---- products/dto/ProductCreateRequest.java ----
export interface ProductCreateRequest {
  sku: string
  name: string
  description?: string
  unit: string
  price: string
  taxPercentage?: string
  categoryId?: string
}

// ---- products/dto/ProductUpdateRequest.java (PATCH: omitted/undefined = unchanged) ----
export interface ProductUpdateRequest {
  sku?: string
  name?: string
  description?: string
  unit?: string
  price?: string
  taxPercentage?: string
  status?: ProductStatus
  categoryId?: string
  clearCategory?: boolean
}

export interface ProductListParams {
  q?: string
  status?: ProductStatus
  categoryId?: string
  unit?: string
  page?: number
  size?: number
  sort?: string
}

// ---- products/dto/ProductCategoryResponse.java ----
export interface ProductCategoryResponse {
  id: string
  name: string
  organizationId: string
  createdAt: string
  updatedAt: string
}

// ---- products/dto/ProductCategoryCreateRequest.java ----
export interface ProductCategoryCreateRequest {
  name: string
}

// ---- products/dto/ProductCategoryUpdateRequest.java ----
export interface ProductCategoryUpdateRequest {
  name?: string
}

// ---- sales/entity/QuotationStatus.java ----
export type QuotationStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED'

// ---- sales/dto/QuotationItemResponse.java ----
export interface QuotationItemResponse {
  id: string
  productId: string
  productNameSnapshot: string
  quantity: number
  unitPrice: number
  taxPercentage: number
  lineSubtotal: number
  lineTaxAmount: number
}

// ---- sales/dto/QuotationResponse.java ----
export interface QuotationResponse {
  id: string
  customerId: string
  status: QuotationStatus
  validUntil: string | null
  discountPercentage: number
  subtotal: number
  discountAmount: number
  taxAmount: number
  grandTotal: number
  items: QuotationItemResponse[]
  organizationId: string
  createdAt: string
  updatedAt: string
}

// ---- sales/dto/QuotationSummaryResponse.java (list endpoint — no items) ----
export interface QuotationSummaryResponse {
  id: string
  customerId: string
  status: QuotationStatus
  validUntil: string | null
  discountPercentage: number
  subtotal: number
  discountAmount: number
  taxAmount: number
  grandTotal: number
  organizationId: string
  createdAt: string
  updatedAt: string
}

// ---- sales/dto/QuotationItemRequest.java ----
export interface QuotationItemRequest {
  productId: string
  quantity: string
}

// ---- sales/dto/QuotationCreateRequest.java ----
export interface QuotationCreateRequest {
  customerId: string
  validUntil?: string
  discountPercentage?: string
  items: QuotationItemRequest[]
}

// ---- sales/dto/QuotationUpdateRequest.java (PATCH: omitted/undefined = unchanged) ----
export interface QuotationUpdateRequest {
  customerId?: string
  validUntil?: string
  clearValidUntil?: boolean
  discountPercentage?: string
  status?: QuotationStatus
  items?: QuotationItemRequest[]
}

export interface QuotationListParams {
  status?: QuotationStatus
  customerId?: string
  validUntilBefore?: string
  page?: number
  size?: number
  sort?: string
}

// ---- tasks/entity/{TaskStatus,TaskPriority}.java ----
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

// ---- tasks/dto/TaskResponse.java ----
// A single response shape for both the single-resource endpoint and the
// paginated list — unlike Quotation/Invoice, Task has no lazy child
// collection, so there is no summary/detail split to make.
export interface TaskResponse {
  id: string
  title: string
  description: string | null
  status: TaskStatus
  priority: TaskPriority
  assignedToUserId: string | null
  customerId: string | null
  leadId: string | null
  dueDate: string | null
  notes: string | null
  organizationId: string
  createdAt: string
  updatedAt: string
}

// ---- tasks/dto/TaskCreateRequest.java ----
// status/assignedToUserId/notes are deliberately absent — status always
// starts at TODO, assignment only ever happens through the dedicated
// /assign endpoint (even at creation), and notes has no field on create at
// all (verified directly against the backend DTO).
export interface TaskCreateRequest {
  title: string
  description?: string
  priority?: TaskPriority
  dueDate?: string
  customerId?: string
  leadId?: string
}

// ---- tasks/dto/TaskUpdateRequest.java (PATCH: omitted/undefined = unchanged) ----
// clearDueDate/clearCustomerId/clearLeadId mirror LeadUpdateRequest's
// clearFollowUpDate pattern — the explicit signal needed to clear an
// optional field. assignedToUserId is deliberately absent (dedicated
// /assign endpoint only). status accepts every value except CANCELLED —
// reaching CANCELLED is only possible via DELETE.
export interface TaskUpdateRequest {
  title?: string
  description?: string
  priority?: TaskPriority
  dueDate?: string
  clearDueDate?: boolean
  customerId?: string
  clearCustomerId?: boolean
  leadId?: string
  clearLeadId?: boolean
  status?: TaskStatus
  notes?: string
}

// ---- tasks/dto/TaskAssignRequest.java ----
export interface TaskAssignRequest {
  assigneeUserId: string | null
}

export interface TaskListParams {
  q?: string
  status?: TaskStatus
  priority?: TaskPriority
  assignedToUserId?: string
  unassigned?: boolean
  customerId?: string
  leadId?: string
  dueDateOnOrBefore?: string
  page?: number
  size?: number
  sort?: string
}

// ---- documents/entity/DocumentStatus.java ----
export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

// ---- documents/dto/DocumentResponse.java ----
// Deliberately excludes storageKey/organizationId/filesystem path — never
// exposed by the backend. No customer/lead/description/tags/versions field
// exists — CLAUDE.md §16 names no such relationship for Documents (verified
// directly against the entity and DTO source, not assumed).
export interface DocumentResponse {
  id: string
  originalFilename: string
  contentType: string
  fileSize: number
  status: DocumentStatus
  uploadedByUserId: string | null
  createdAt: string
  updatedAt: string
}

// ---- documents/controller/DocumentController.search params ----
// Do not add filters beyond these — the backend accepts exactly this set.
export interface DocumentListParams {
  q?: string
  status?: DocumentStatus
  contentType?: string
  uploadedByUserId?: string
  page?: number
  size?: number
  sort?: string
}
