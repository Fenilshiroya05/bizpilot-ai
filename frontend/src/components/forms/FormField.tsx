import type { ReactNode } from 'react'

import { Label } from '@/components/ui/label'

/**
 * A layout-only wrapper — it does not clone/inject props into `children`.
 * The caller still wires `id`, `aria-invalid`, and
 * `aria-describedby={error ? \`${id}-error\` : undefined}` directly onto the
 * actual input/select, matching the convention already established in
 * LoginPage/RegisterPage (Phase 20). This keeps the error-to-id contract
 * explicit and visible at the call site instead of implicit "magic" prop
 * injection.
 */
export function FormField({
  label,
  htmlFor,
  error,
  hint,
  children,
}: {
  label: string
  htmlFor: string
  error?: string
  hint?: string
  children: ReactNode
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {error ? (
        <p id={`${htmlFor}-error`} className="text-sm text-destructive">
          {error}
        </p>
      ) : hint ? (
        <p className="text-xs text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  )
}
