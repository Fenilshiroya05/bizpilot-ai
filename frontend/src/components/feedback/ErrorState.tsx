import { AlertTriangle } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { ApiError, NetworkError } from '@/lib/api-client'

/**
 * Maps any thrown error into a human-readable message. Never renders a raw
 * backend exception name, SQL, stack trace, or provider error — the backend
 * (`ApiError.message`) already produces a safe, user-facing string for every
 * documented error case; anything else (a network failure, an unrecognized
 * shape) falls back to one fixed generic sentence.
 */
function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  if (error instanceof NetworkError) {
    return error.message
  }
  return 'Something went wrong on our end. Please try again.'
}

export function ErrorState({
  title = "We couldn't load this page",
  error,
  onRetry,
}: {
  title?: string
  error: unknown
  onRetry?: () => void
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-destructive/40 p-10 text-center">
      <AlertTriangle className="h-8 w-8 text-destructive" aria-hidden="true" />
      <div className="space-y-1">
        <p className="text-sm font-medium text-foreground">{title}</p>
        <p className="text-sm text-muted-foreground">{describeError(error)}</p>
      </div>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          Retry
        </Button>
      )}
    </div>
  )
}
