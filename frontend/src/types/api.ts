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
