import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { SidebarNavList } from './SidebarNavList'
import * as AuthProvider from '@/app/providers/AuthProvider'
import type { Permission } from '@/lib/permissions'

vi.mock('@/app/providers/AuthProvider', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/app/providers/AuthProvider')>()
  return { ...actual, useAuth: vi.fn() }
})

function mockPermissions(permissions: Permission[]) {
  vi.mocked(AuthProvider.useAuth).mockReturnValue({
    permissions: new Set(permissions),
  } as ReturnType<typeof AuthProvider.useAuth>)
}

function renderNav() {
  return render(
    <MemoryRouter>
      <SidebarNavList onOpenAi={vi.fn()} />
    </MemoryRouter>,
  )
}

describe('SidebarNavList — AI Assistant permission gating', () => {
  it('shows the AI Assistant trigger when the user has AI_USE', () => {
    mockPermissions(['AI_USE'])
    renderNav()
    expect(screen.getByRole('button', { name: 'Open AI Assistant' })).toBeInTheDocument()
  })

  it('hides the AI Assistant trigger when the user lacks AI_USE', () => {
    mockPermissions([])
    renderNav()
    expect(screen.queryByRole('button', { name: 'Open AI Assistant' })).not.toBeInTheDocument()
  })
})
