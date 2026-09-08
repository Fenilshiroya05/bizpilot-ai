import { Bot, Sparkles } from 'lucide-react'

import { Sheet, SheetContent } from '@/components/ui/sheet'

/**
 * A polished, non-functional placeholder. Phase 25 implements the real
 * assistant against the existing `POST /api/v1/ai/chat` endpoint — this
 * drawer deliberately makes no network call of any kind, per the Phase 20
 * scope decision.
 */
export function AiAssistantDrawer({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent title="AI Assistant" side="right">
        <div className="flex h-14 items-center gap-2 border-b border-border px-1">
          <Bot className="h-5 w-5 text-primary" aria-hidden="true" />
          <span className="text-base font-semibold text-foreground">AI Assistant</span>
        </div>
        <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
          <Sparkles className="h-8 w-8 text-primary" aria-hidden="true" />
          <p className="text-sm font-medium text-foreground">Coming in Phase 25</p>
          <p className="text-sm text-muted-foreground">
            BizPilot's AI assistant will answer questions about your business data and documents
            here, with sources and permission-aware tools. The backend endpoint already exists —
            this panel will connect to it in a later phase.
          </p>
        </div>
      </SheetContent>
    </Sheet>
  )
}
