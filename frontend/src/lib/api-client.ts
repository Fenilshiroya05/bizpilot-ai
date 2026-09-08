import type { ApiErrorBody } from '@/types/api'

/**
 * The one centralized HTTP layer for BizPilot AI. No feature component
 * calls `fetch` directly — everything goes through `apiFetch` so
 * authentication, refresh, and error normalization live in exactly one
 * place.
 *
 * Token storage (Phase 20 approved decision): both the access token and the
 * refresh token live in memory only (this module-level variable), never in
 * `localStorage`/`sessionStorage`. The current backend hands both tokens
 * back in a JSON response body — there is no httpOnly refresh-cookie
 * mechanism to lean on instead — so memory-only is the safest storage this
 * backend contract allows. The direct, accepted consequence: a full page
 * reload always loses the session and returns the user to `/auth/login`.
 * This is a deliberate trade-off, not an oversight — see docs/architecture.md
 * (Phase 20 note) — and is not something this phase changes by modifying the
 * backend.
 */

const BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080'

export interface Tokens {
  accessToken: string
  refreshToken: string
}

let currentTokens: Tokens | null = null
type TokenListener = (tokens: Tokens | null) => void
const listeners = new Set<TokenListener>()

export function getTokens(): Tokens | null {
  return currentTokens
}

export function setTokens(tokens: Tokens | null): void {
  currentTokens = tokens
  for (const listener of listeners) listener(tokens)
}

export function clearTokens(): void {
  setTokens(null)
}

export function subscribeTokens(listener: TokenListener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors?: Record<string, string>

  constructor(body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiError'
    this.status = body.status
    this.code = body.code
    this.fieldErrors = body.fieldErrors
  }
}

/** A network-level failure (no response at all) — distinct from a real ApiError. */
export class NetworkError extends Error {
  constructor() {
    super('Unable to reach the server. Check your connection and try again.')
    this.name = 'NetworkError'
  }
}

async function toApiError(res: Response, path: string): Promise<ApiError> {
  try {
    const data = (await res.json()) as unknown
    if (data && typeof data === 'object' && 'code' in data && 'message' in data) {
      return new ApiError(data as ApiErrorBody)
    }
  } catch {
    // Response body wasn't valid ApiError JSON — fall through to a safe default.
  }
  return new ApiError({
    timestamp: new Date().toISOString(),
    status: res.status,
    code: 'UNKNOWN_ERROR',
    message: 'Something went wrong. Please try again.',
    path,
  })
}

// Concurrent-refresh de-duplication: if several requests 401 at once, only
// one POST /auth/refresh is ever sent. Every caller awaits this same
// in-flight promise instead of racing the refresh-token rotation.
let refreshPromise: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const tokens = currentTokens
  if (!tokens) return null

  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const res = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: tokens.refreshToken }),
        })
        if (!res.ok) {
          clearTokens()
          return null
        }
        const data = (await res.json()) as Tokens
        setTokens(data)
        return data.accessToken
      } catch {
        clearTokens()
        return null
      } finally {
        refreshPromise = null
      }
    })()
  }

  return refreshPromise
}

export interface ApiFetchOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  /** Internal flag — prevents a second automatic retry after a refreshed request still 401s. */
  _isRetry?: boolean
}

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const { body, headers, _isRetry, ...rest } = options
  const isFormData = body instanceof FormData
  const tokens = currentTokens

  const finalHeaders = new Headers(headers)
  if (!isFormData && body !== undefined) {
    finalHeaders.set('Content-Type', 'application/json')
  }
  if (tokens?.accessToken) {
    finalHeaders.set('Authorization', `Bearer ${tokens.accessToken}`)
  }

  let res: Response
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      ...rest,
      headers: finalHeaders,
      body: body === undefined ? undefined : isFormData ? (body as FormData) : JSON.stringify(body),
    })
  } catch {
    throw new NetworkError()
  }

  if (res.status === 401 && !_isRetry && tokens) {
    const newAccessToken = await refreshAccessToken()
    if (newAccessToken) {
      return apiFetch<T>(path, { ...options, _isRetry: true })
    }
  }

  if (!res.ok) {
    throw await toApiError(res, path)
  }

  if (res.status === 204) {
    return undefined as T
  }

  return (await res.json()) as T
}

/**
 * For binary responses (currently: quotation PDF download) — same
 * auth/refresh/error handling as `apiFetch`, but resolves to a `Blob`
 * instead of parsing JSON (a PDF response body is never valid JSON).
 */
export async function apiFetchBlob(path: string, isRetry = false): Promise<Blob> {
  const tokens = currentTokens
  const headers = new Headers()
  if (tokens?.accessToken) {
    headers.set('Authorization', `Bearer ${tokens.accessToken}`)
  }

  let res: Response
  try {
    res = await fetch(`${BASE_URL}${path}`, { headers })
  } catch {
    throw new NetworkError()
  }

  if (res.status === 401 && !isRetry && tokens) {
    const newAccessToken = await refreshAccessToken()
    if (newAccessToken) {
      return apiFetchBlob(path, true)
    }
  }

  if (!res.ok) {
    throw await toApiError(res, path)
  }

  return res.blob()
}
