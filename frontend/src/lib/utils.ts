import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs))
}

/**
 * Builds a `?a=1&b=2` query string, dropping `undefined`/`null`/`''` values
 * so an omitted filter never becomes a literal `?status=` the backend would
 * have to special-case. Never invents parameter names — callers pass only
 * real backend query parameters.
 */
export function buildQueryString<T extends object>(params: T): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params) as [string, string | number | boolean | undefined | null][]) {
    if (value === undefined || value === null || value === '') continue
    search.set(key, String(value))
  }
  const query = search.toString()
  return query ? `?${query}` : ''
}
