import { Sheet, SheetContent } from '@/components/ui/sheet'
import { SidebarNavList } from '@/components/layout/SidebarNavList'

export function MobileNav({
  open,
  onOpenChange,
  onOpenAi,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onOpenAi: () => void
}) {
  // Radix only auto-restores focus to a <Dialog.Trigger>; this Sheet is
  // opened by a plain button in TopBar (controlled externally, not wrapped
  // in SheetTrigger), so focus-return has to be wired explicitly here.
  function handleOpenChange(next: boolean) {
    onOpenChange(next)
    if (!next) {
      requestAnimationFrame(() => {
        document.getElementById('mobile-nav-trigger')?.focus()
      })
    }
  }

  return (
    <Sheet open={open} onOpenChange={handleOpenChange}>
      <SheetContent title="Navigation">
        <div className="flex h-14 items-center border-b border-border px-1">
          <span className="text-base font-semibold text-foreground">BizPilot AI</span>
        </div>
        <div className="flex flex-1 flex-col gap-2 overflow-y-auto pt-3">
          <SidebarNavList
            onNavigate={() => onOpenChange(false)}
            onOpenAi={() => {
              onOpenChange(false)
              onOpenAi()
            }}
          />
        </div>
      </SheetContent>
    </Sheet>
  )
}
