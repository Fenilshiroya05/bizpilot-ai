import { z } from 'zod'

/** Mirrors `AiChatRequest`'s `@NotBlank @Size(max = 2000)` exactly — UX-only, the backend remains authoritative. */
export const MAX_CHAT_MESSAGE_LENGTH = 2000

export const chatMessageSchema = z
  .string()
  .trim()
  .min(1, { message: 'Message is required' })
  .max(MAX_CHAT_MESSAGE_LENGTH, { message: `Message must be ${MAX_CHAT_MESSAGE_LENGTH} characters or fewer` })
