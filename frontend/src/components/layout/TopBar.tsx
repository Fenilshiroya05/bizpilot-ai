import { Bot, Menu } from 'lucide-react'

import { useAuth } from '@/app/providers/AuthProvider'
import { NotificationButton } from '@/components/layout/NotificationButton'
import { UserMenu } from '@/components/layout/UserMenu'
import { Skeleton } from '@/components/ui/skeleton'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

export function TopBar({
  onOpenMobileNav,
  onOpenAi,
}: {
  onOpenMobileNav: () => void
  onOpenAi: () => void
}) {
  const { organization, isBootstrapping } = useAuth()

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-border bg-background px-4">
      <div className="flex items-center gap-3">
        <button
          id="mobile-nav-trigger"
          type="button"
          onClick={onOpenMobileNav}
          className="flex h-9 w-9 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground lg:hidden"
          aria-label="Open navigation menu"
        >
          <Menu className="h-5 w-5" aria-hidden="true" />
        </button>

        {isBootstrapping ? (
          <Skeleton className="h-4 w-32" />
        ) : (
          <span className="text-sm font-medium text-foreground">{organization?.name}</span>
        )}
      </div>

      <div className="flex items-center gap-1">
        <Tooltip delayDuration={200}>
          <TooltipTrigger asChild>
            <button
              type="button"
              onClick={onOpenAi}
              className="flex h-9 w-9 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
              aria-label="Open AI Assistant"
            >
              <Bot className="h-4 w-4" aria-hidden="true" />
            </button>
          </TooltipTrigger>
          <TooltipContent>AI Assistant</TooltipContent>
        </Tooltip>
        <NotificationButton />
        <UserMenu />
      </div>
    </header>
  )
}
