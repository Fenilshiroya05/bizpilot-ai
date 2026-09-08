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
