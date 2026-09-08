import { QueryClient } from '@tanstack/react-query'

import { ApiError } from './api-client'

const NON_RETRYABLE_STATUSES = new Set([400, 401, 403, 404, 409, 413, 415, 422])

function shouldRetry(failureCount: number, error: unknown): boolean {
  // Authentication, permission, validation, and not-found failures are
  // never transient — retrying sends the exact same doomed request again.
  if (error instanceof ApiError && NON_RETRYABLE_STATUSES.has(error.status)) {
    return false
  }
  return failureCount < 2
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: shouldRetry,
      refetchOnWindowFocus: false,
      staleTime: 30_000,
    },
    mutations: {
      retry: false,
    },
  },
})
