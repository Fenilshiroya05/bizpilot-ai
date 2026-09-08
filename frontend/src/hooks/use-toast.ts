import { useSyncExternalStore } from 'react'

/**
 * Minimal toast store (module-level, framework-agnostic). No Redux/Zustand —
 * this is a ~40-line pub-sub, which is all a "show a transient message" list
 * needs. `useToast()` subscribes a component to it via `useSyncExternalStore`.
 */

export interface ToastItem {
  id: string
  title: string
  description?: string
  variant?: 'default' | 'success' | 'destructive'
}

let toasts: ToastItem[] = []
const listeners = new Set<() => void>()

function notify() {
  for (const listener of listeners) listener()
}

export function toast(item: Omit<ToastItem, 'id'>): void {
  const id = crypto.randomUUID()
  toasts = [...toasts, { ...item, id }]
  notify()
}

export function dismissToast(id: string): void {
  toasts = toasts.filter((t) => t.id !== id)
  notify()
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function getSnapshot(): ToastItem[] {
  return toasts
}

export function useToast(): {
  toasts: ToastItem[]
  toast: typeof toast
  dismiss: typeof dismissToast
} {
  const current = useSyncExternalStore(subscribe, getSnapshot)
  return { toasts: current, toast, dismiss: dismissToast }
}
