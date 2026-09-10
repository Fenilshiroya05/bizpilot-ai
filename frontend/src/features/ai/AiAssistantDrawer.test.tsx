import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AiAssistantDrawer } from './AiAssistantDrawer'
import * as aiApi from './api'
import { ApiError } from '@/lib/api-client'
import { MAX_CHAT_MESSAGE_LENGTH } from './schemas'

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>()
  return { ...actual, sendChatMessage: vi.fn() }
})

function renderDrawer() {
  return render(
    <MemoryRouter>
      <AiAssistantDrawer open onOpenChange={vi.fn()} />
    </MemoryRouter>,
  )
}

async function sendMessage(text: string) {
  await userEvent.type(screen.getByLabelText('Message'), text)
  await userEvent.click(screen.getByRole('button', { name: 'Send' }))
}

describe('AiAssistantDrawer', () => {
  beforeEach(() => {
    Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } })
  })
  afterEach(() => vi.clearAllMocks())

  it('1. shows the empty state before any message is sent', () => {
    renderDrawer()
    expect(screen.getByText('Ask about your business')).toBeInTheDocument()
  })

  it('2. renders the user message after sending', async () => {
    vi.mocked(aiApi.sendChatMessage).mockResolvedValue({ answer: 'Here is your answer.', sources: [] })
    renderDrawer()
    await sendMessage('What customers are active?')
    expect(await screen.findByText('What customers are active?')).toBeInTheDocument()
  })

  it('3. renders the assistant response on success', async () => {
    vi.mocked(aiApi.sendChatMessage).mockResolvedValue({ answer: 'You have 12 active customers.', sources: [] })
    renderDrawer()
    await sendMessage('How many active customers?')
    expect(await screen.findByText('You have 12 active customers.')).toBeInTheDocument()
  })

  it('4. renders document sources', async () => {
    vi.mocked(aiApi.sendChatMessage).mockResolvedValue({
      answer: 'See the attached policy.',
      sources: [{ documentId: 'd1', documentName: 'refund-policy.pdf', chunkIndex: 0 }],
    })
    renderDrawer()
    await sendMessage('What is the refund policy?')
    expect(await screen.findByText('refund-policy.pdf')).toBeInTheDocument()
  })

  it('5. a source link points to /documents/:id', async () => {
    vi.mocked(aiApi.sendChatMessage).mockResolvedValue({
      answer: 'See the attached policy.',
      sources: [{ documentId: 'd1', documentName: 'refund-policy.pdf', chunkIndex: 0 }],
    })
    renderDrawer()
    await sendMessage('What is the refund policy?')
    const link = await screen.findByRole('link', { name: /refund-policy\.pdf/ })
    expect(link).toHaveAttribute('href', '/documents/d1')
  })

  it('6. shows a loading indicator while the request is in flight', async () => {
    let resolveFn!: (value: { answer: string; sources: [] }) => void
    vi.mocked(aiApi.sendChatMessage).mockImplementation(() => new Promise((resolve) => { resolveFn = resolve }))
    renderDrawer()
    await sendMessage('Hello')
    expect(await screen.findByRole('status')).toHaveTextContent('Thinking...')
    resolveFn({ answer: 'Hi there.', sources: [] })
  })

  it('7. disables Send while a request is pending', async () => {
    let resolveFn!: (value: { answer: string; sources: [] }) => void
    vi.mocked(aiApi.sendChatMessage).mockImplementation(() => new Promise((resolve) => { resolveFn = resolve }))
    renderDrawer()
    await sendMessage('Hello')
    expect(screen.getByRole('button', { name: 'Thinking...' })).toBeDisabled()
    resolveFn({ answer: 'Hi there.', sources: [] })
  })

  it('8. blocks Send and shows validation for an empty message', async () => {
    renderDrawer()
    await userEvent.click(screen.getByRole('button', { name: 'Send' }))
    expect(await screen.findByText('Message is required')).toBeInTheDocument()
    expect(aiApi.sendChatMessage).not.toHaveBeenCalled()
  })

  it('9. blocks Send and shows validation for a message over 2000 characters', async () => {
    renderDrawer()
    const textarea = screen.getByLabelText('Message') as HTMLTextAreaElement
    // Simulate a paste of an overlong value — there is deliberately no
    // native `maxLength` attribute (it would silently truncate pasted text
    // and make this Zod validation path unreachable).
    await userEvent.click(textarea)
    await userEvent.paste('x'.repeat(MAX_CHAT_MESSAGE_LENGTH + 1))
    await userEvent.click(screen.getByRole('button', { name: 'Send' }))
    expect(await screen.findByText(`Message must be ${MAX_CHAT_MESSAGE_LENGTH} characters or fewer`)).toBeInTheDocument()
    expect(aiApi.sendChatMessage).not.toHaveBeenCalled()
  })

  it('10. each send transmits only the latest message, never prior turns concatenated', async () => {
    vi.mocked(aiApi.sendChatMessage)
      .mockResolvedValueOnce({ answer: 'First answer.', sources: [] })
      .mockResolvedValueOnce({ answer: 'Second answer.', sources: [] })
    renderDrawer()
    await sendMessage('first question')
    await screen.findByText('First answer.')
    await sendMessage('second question')
    await screen.findByText('Second answer.')

    expect(aiApi.sendChatMessage).toHaveBeenNthCalledWith(1, 'first question')
    expect(aiApi.sendChatMessage).toHaveBeenNthCalledWith(2, 'second question')
    expect(vi.mocked(aiApi.sendChatMessage).mock.calls[1]?.[0]).not.toContain('first question')
  })

  it('11. handles 503 AI_DISABLED with an informational message', async () => {
    vi.mocked(aiApi.sendChatMessage).mockRejectedValue(
      new ApiError({ timestamp: '2026-01-01T00:00:00Z', status: 503, code: 'AI_DISABLED', message: 'The AI assistant is currently unavailable', path: '/api/v1/ai/chat' }),
    )
    renderDrawer()
    await sendMessage('Hello')
    expect(await screen.findByText("AI features aren't enabled for this workspace.")).toBeInTheDocument()
    // No Retry for a disabled-AI error — retrying won't change the outcome (LeadScorePanel convention).
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
  })

  it('12. handles 502 AI_PROVIDER_ERROR with a destructive alert and Retry', async () => {
    vi.mocked(aiApi.sendChatMessage).mockRejectedValue(
      new ApiError({ timestamp: '2026-01-01T00:00:00Z', status: 502, code: 'AI_PROVIDER_ERROR', message: 'The AI assistant is temporarily unavailable', path: '/api/v1/ai/chat' }),
    )
    renderDrawer()
    await sendMessage('Hello')
    expect(await screen.findByText('Unable to get a response right now. Try again.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('13. Retry resends the same failed latest message', async () => {
    vi.mocked(aiApi.sendChatMessage)
      .mockRejectedValueOnce(
        new ApiError({ timestamp: '2026-01-01T00:00:00Z', status: 502, code: 'AI_PROVIDER_ERROR', message: 'x', path: '/api/v1/ai/chat' }),
      )
      .mockResolvedValueOnce({ answer: 'Recovered answer.', sources: [] })
    renderDrawer()
    await sendMessage('flaky question')
    await screen.findByRole('button', { name: 'Retry' })
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))

    await waitFor(() => expect(screen.getByText('Recovered answer.')).toBeInTheDocument())
    expect(aiApi.sendChatMessage).toHaveBeenNthCalledWith(1, 'flaky question')
    expect(aiApi.sendChatMessage).toHaveBeenNthCalledWith(2, 'flaky question')
  })

  it('14. Clear conversation resets the view and makes no network request', async () => {
    vi.mocked(aiApi.sendChatMessage).mockResolvedValue({ answer: 'An answer.', sources: [] })
    renderDrawer()
    await sendMessage('a question')
    await screen.findByText('An answer.')
    vi.mocked(aiApi.sendChatMessage).mockClear()

    await userEvent.click(screen.getByRole('button', { name: 'Clear conversation' }))
    expect(screen.getByText('Ask about your business')).toBeInTheDocument()
    expect(aiApi.sendChatMessage).not.toHaveBeenCalled()
  })

  it('15. Copy response copies the assistant answer to the clipboard', async () => {
    vi.mocked(aiApi.sendChatMessage).mockResolvedValue({ answer: 'Copy this text.', sources: [] })
    renderDrawer()
    await sendMessage('a question')
    await screen.findByText('Copy this text.')

    await userEvent.click(screen.getByRole('button', { name: 'Copy response' }))
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('Copy this text.')
  })
})
