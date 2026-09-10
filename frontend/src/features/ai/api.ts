import { apiFetch } from '@/lib/api-client'
import type { AiChatResponse } from '@/types/api'

/**
 * Deliberately no query key / TanStack Query cache entry — a chat turn is
 * not a stable resource with an identity to invalidate, exactly like
 * `leads/api.ts`'s `scoreLead` (Phase 18/21). The backend is stateless
 * (verified directly against `AiChatRequest.java`): only `message` is ever
 * sent — never a conversationId, history, or context field.
 */
export function sendChatMessage(message: string): Promise<AiChatResponse> {
  return apiFetch<AiChatResponse>('/api/v1/ai/chat', { method: 'POST', body: { message } })
}
