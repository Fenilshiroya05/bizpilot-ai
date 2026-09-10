import { Bot, Sparkles } from 'lucide-react'
import { useState } from 'react'

import { sendChatMessage } from '@/features/ai/api'
import { ChatMessage } from '@/features/ai/components/ChatMessage'
import { chatMessageSchema, MAX_CHAT_MESSAGE_LENGTH } from '@/features/ai/schemas'
import type { ChatTurn } from '@/features/ai/types'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent } from '@/components/ui/sheet'
import { ApiError } from '@/lib/api-client'

function classifyError(error: unknown): ChatTurn['errorKind'] {
  if (error instanceof ApiError) {
    if (error.status === 503) return 'disabled'
    if (error.status === 502) return 'provider'
  }
  return 'unknown'
}

/**
 * The real Phase 25 assistant against the existing, stateless
 * `POST /api/v1/ai/chat` endpoint (verified directly against
 * `AiChatRequest`/`AiChatResponse`/`AiChatSource.java`). Each send transmits
 * ONLY the latest message — the backend has no conversationId/history
 * field, so no prior turns are ever concatenated into the outgoing
 * request. Turns are held in local component state only: never cached in
 * TanStack Query, never written to localStorage/IndexedDB, never sent to
 * any backend "clear" endpoint (none exists) — closing the drawer or
 * clicking "Clear conversation" simply discards this state, exactly like
 * `LeadScorePanel`'s mutation-safety rule (Phase 18/21).
 */
export function AiAssistantDrawer({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [turns, setTurns] = useState<ChatTurn[]>([])
  const [input, setInput] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)

  const isSending = turns.some((turn) => turn.status === 'pending')

  async function submitMessage(message: string, existingTurnId?: string) {
    const id = existingTurnId ?? crypto.randomUUID()
    setTurns((prev) =>
      existingTurnId
        ? prev.map((turn) => (turn.id === id ? { ...turn, status: 'pending', errorKind: undefined } : turn))
        : [...prev, { id, userMessage: message, status: 'pending' }],
    )

    try {
      const response = await sendChatMessage(message)
      setTurns((prev) =>
        prev.map((turn) =>
          turn.id === id ? { ...turn, status: 'success', answer: response.answer, sources: response.sources } : turn,
        ),
      )
    } catch (error) {
      setTurns((prev) =>
        prev.map((turn) => (turn.id === id ? { ...turn, status: 'error', errorKind: classifyError(error) } : turn)),
      )
    }
  }

  function handleSend() {
    const parsed = chatMessageSchema.safeParse(input)
    if (!parsed.success) {
      setValidationError(parsed.error.issues[0]?.message ?? 'Invalid message')
      return
    }
    setValidationError(null)
    setInput('')
    void submitMessage(parsed.data)
  }

  function handleRetry(turn: ChatTurn) {
    void submitMessage(turn.userMessage, turn.id)
  }

  function handleClear() {
    setTurns([])
    setInput('')
    setValidationError(null)
  }

  function handleKeyDown(event: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      handleSend()
    }
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent title="AI Assistant" side="right" className="w-96 sm:w-[420px]">
        <div className="flex h-14 shrink-0 items-center gap-2 border-b border-border px-1">
          <Bot className="h-5 w-5 text-primary" aria-hidden="true" />
          <span className="text-base font-semibold text-foreground">AI Assistant</span>
        </div>

        {turns.length === 0 ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
            <Sparkles className="h-8 w-8 text-primary" aria-hidden="true" />
            <p className="text-sm font-medium text-foreground">Ask about your business</p>
            <p className="text-sm text-muted-foreground">
              Ask about your customers, leads, products, invoices, or uploaded documents. Answers
              include the documents they were based on.
            </p>
          </div>
        ) : (
          <div
            className="flex-1 space-y-4 overflow-y-auto py-3"
            aria-live="polite"
            aria-relevant="additions"
          >
            {turns.map((turn) => (
              <ChatMessage key={turn.id} turn={turn} onRetry={handleRetry} />
            ))}
          </div>
        )}

        <div className="shrink-0 space-y-2 border-t border-border pt-3">
          <label htmlFor="ai-chat-input" className="sr-only">
            Message
          </label>
          <textarea
            id="ai-chat-input"
            rows={2}
            placeholder="Ask a question..."
            value={input}
            onChange={(e) => {
              setInput(e.target.value)
              if (validationError) setValidationError(null)
            }}
            onKeyDown={handleKeyDown}
            disabled={isSending}
            aria-invalid={!!validationError}
            aria-describedby={validationError ? 'ai-chat-input-error' : undefined}
            className="flex w-full rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive"
          />
          {/* No native `maxLength` — it would silently truncate pasted text
              and make the over-length Zod error unreachable. The Zod check
              is the only gate; this counter is a visual hint only. */}
          <p className={`text-right text-xs ${input.length > MAX_CHAT_MESSAGE_LENGTH ? 'text-destructive' : 'text-muted-foreground'}`}>
            {input.length} / {MAX_CHAT_MESSAGE_LENGTH}
          </p>
          {validationError && (
            <p id="ai-chat-input-error" className="text-sm text-destructive">
              {validationError}
            </p>
          )}
          <div className="flex items-center justify-between gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleClear}
              disabled={turns.length === 0 || isSending}
            >
              Clear conversation
            </Button>
            <Button type="button" size="sm" onClick={handleSend} isLoading={isSending} disabled={isSending}>
              {isSending ? 'Thinking...' : 'Send'}
            </Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  )
}
