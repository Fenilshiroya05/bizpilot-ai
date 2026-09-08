import { useState } from 'react'
import { Outlet } from 'react-router-dom'

import { AiAssistantDrawer } from '@/features/ai/AiAssistantDrawer'
import { MobileNav } from '@/components/layout/MobileNav'
import { Sidebar } from '@/components/layout/Sidebar'
import { TopBar } from '@/components/layout/TopBar'

export function AppShell() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const [aiOpen, setAiOpen] = useState(false)

  return (
    <div className="flex h-screen overflow-hidden bg-surface">
      <Sidebar onOpenAi={() => setAiOpen(true)} />
      <MobileNav
        open={mobileNavOpen}
        onOpenChange={setMobileNavOpen}
        onOpenAi={() => setAiOpen(true)}
      />

      <div className="flex flex-1 flex-col overflow-hidden">
        <TopBar onOpenMobileNav={() => setMobileNavOpen(true)} onOpenAi={() => setAiOpen(true)} />
        <main className="flex-1 overflow-y-auto p-4 sm:p-6">
          <div className="mx-auto w-full max-w-6xl">
            <Outlet />
          </div>
        </main>
      </div>

      <AiAssistantDrawer open={aiOpen} onOpenChange={setAiOpen} />
    </div>
  )
}
