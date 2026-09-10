import { describe, expect, it } from 'vitest'

import { chatMessageSchema, MAX_CHAT_MESSAGE_LENGTH } from './schemas'

describe('chatMessageSchema', () => {
  it('rejects an empty message', () => {
    const result = chatMessageSchema.safeParse('')
    expect(result.success).toBe(false)
  })

  it('rejects a whitespace-only message', () => {
    const result = chatMessageSchema.safeParse('   ')
    expect(result.success).toBe(false)
  })

  it('accepts a normal message', () => {
    const result = chatMessageSchema.safeParse('What customers are active?')
    expect(result.success).toBe(true)
  })

  it('accepts a message at exactly the 2000-character boundary', () => {
    const result = chatMessageSchema.safeParse('x'.repeat(MAX_CHAT_MESSAGE_LENGTH))
    expect(result.success).toBe(true)
  })

  it('rejects a message over 2000 characters', () => {
    const result = chatMessageSchema.safeParse('x'.repeat(MAX_CHAT_MESSAGE_LENGTH + 1))
    expect(result.success).toBe(false)
  })
})
