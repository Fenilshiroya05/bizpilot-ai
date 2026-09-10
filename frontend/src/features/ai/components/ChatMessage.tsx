import { Check, Copy, FileText } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import type { AiChatSource } from '@/types/api'
import type { ChatTurn } from '@/features/ai/types'

/**
 * `sources` is one entry per retrieved chunk, not deduplicated by document
 * (verified against `AiChatSource.java` — two chunks from the same
 * document are two distinct entries). The chunk index isn't meaningful to
 * an end user, so this dedupes by `documentId` purely for display —
 * nothing is invented, only repeats of the same real document are
 * collapsed to one link.
 */
function uniqueSources(sources: AiChatSource[]): AiChatSource[] {
  const seen = new Set<string>()
  const result: AiChatSource[] = []
  for (const source of sources) {
    if (seen.has(source.documentId)) continue
    seen.add(source.documentId)
    result.push(source)
  }
  return result
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    await navigator.clipboard.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <Button variant="ghost" size="sm" onClick={handleCopy} aria-label="Copy response">
      {copied ? <Check className="h-3.5 w-3.5" aria-hidden="true" /> : <Copy className="h-3.5 w-3.5" aria-hidden="true" />}
      {copied ? 'Copied' : 'Copy'}
    </Button>
  )
}

const ERROR_TEXT: Record<'disabled' | 'provider' | 'unknown', { variant: 'info' | 'destructive'; message: string }> = {
  disabled: { variant: 'info', message: "AI features aren't enabled for this workspace." },
  provider: { variant: 'destructive', message: 'Unable to get a response right now. Try again.' },
  unknown: { variant: 'destructive', message: 'Unable to get a response right now. Try again.' },
}

export function ChatMessage({ turn, onRetry }: { turn: ChatTurn; onRetry: (turn: ChatTurn) => void }) {
  return (
    <div className="space-y-2">
      <div className="ml-auto max-w-[85%] rounded-md bg-primary/10 px-3 py-2 text-sm text-foreground">
        {turn.userMessage}
      </div>

      {turn.status === 'pending' && (
        <p className="text-sm text-muted-foreground" role="status">
          Thinking...
        </p>
      )}

      {turn.status === 'error' && (
        <Alert variant={ERROR_TEXT[turn.errorKind ?? 'unknown'].variant}>
          <div className="flex items-center justify-between gap-3">
            <span>{ERROR_TEXT[turn.errorKind ?? 'unknown'].message}</span>
            {turn.errorKind !== 'disabled' && (
              <Button variant="outline" size="sm" onClick={() => onRetry(turn)}>
                Retry
              </Button>
            )}
          </div>
        </Alert>
      )}

      {turn.status === 'success' && (
        <div className="max-w-[95%] space-y-2 rounded-md border border-border bg-background px-3 py-2">
          <p className="whitespace-pre-wrap text-sm text-foreground">{turn.answer}</p>
          <CopyButton text={turn.answer ?? ''} />
          {turn.sources && turn.sources.length > 0 && (
            <div className="space-y-1 border-t border-border pt-2">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Sources</p>
              <ul className="space-y-1">
                {uniqueSources(turn.sources).map((source) => (
                  <li key={source.documentId}>
                    <Link
                      to={`/documents/${source.documentId}`}
                      className="flex items-center gap-1.5 text-sm text-primary hover:underline"
                    >
                      <FileText className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                      {source.documentName}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
