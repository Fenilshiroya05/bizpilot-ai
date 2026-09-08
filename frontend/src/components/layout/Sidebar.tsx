import { PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import { useState } from 'react'

import { SidebarNavList } from '@/components/layout/SidebarNavList'
import { cn } from '@/lib/utils'

export function Sidebar({ onOpenAi }: { onOpenAi: () => void }) {
  const [collapsed, setCollapsed] = useState(false)

  return (
    <aside
      className={cn(
        'hidden flex-col border-r border-border bg-background transition-[width] duration-150 lg:flex',
        collapsed ? 'w-[68px]' : 'w-60',
      )}
    >
      <div
        className={cn(
          'flex h-14 items-center border-b border-border px-4',
          collapsed && 'justify-center px-0',
        )}
      >
        {collapsed ? (
          <span className="text-lg font-semibold text-primary">B</span>
        ) : (
          <span className="text-base font-semibold text-foreground">BizPilot AI</span>
        )}
      </div>

      <div className="flex flex-1 flex-col gap-2 overflow-y-auto p-3">
        <SidebarNavList collapsed={collapsed} onOpenAi={onOpenAi} />
      </div>

      <div className="border-t border-border p-2">
        <button
          type="button"
          onClick={() => setCollapsed((value) => !value)}
          className="flex w-full items-center justify-center gap-2 rounded-md px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {collapsed ? (
            <PanelLeftOpen className="h-4 w-4" aria-hidden="true" />
          ) : (
            <>
              <PanelLeftClose className="h-4 w-4" aria-hidden="true" />
              <span>Collapse</span>
            </>
          )}
        </button>
      </div>
    </aside>
  )
}
