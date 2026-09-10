import type { AiChatSource } from '@/types/api'

/**
 * Local, client-only UI state for one send/response exchange — never
 * persisted (no localStorage/IndexedDB/backend), discarded when the drawer
 * is closed or "Clear conversation" is clicked. Not a backend DTO — this
 * type has no server-side counterpart, unlike everything in `types/api.ts`.
 */
export interface ChatTurn {
  id: string
  userMessage: string
  status: 'pending' | 'success' | 'error'
  answer?: string
  sources?: AiChatSource[]
  errorKind?: 'disabled' | 'provider' | 'unknown'
}
