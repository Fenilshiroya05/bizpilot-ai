import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '@/app/providers/AuthProvider'
import { getMe } from '@/features/auth/api'
import { getCurrentOrganization } from '@/features/settings/api'
import { ApiError, clearTokens, setTokens } from '@/lib/api-client'
import { createTestQueryClient } from '@/test/render'
import type { LeadResponse } from '@/types/api'
import { LeadScorePanel } from './LeadScorePanel'
import * as leadsApi from '../api'

vi.mock('../api')
vi.mock('@/features/auth/api')
vi.mock('@/features/settings/api')

const LEAD: LeadResponse = {
  id: 'l1',
  name: 'Jane',
  company: null,
  email: null,
  phone: null,
  status: 'NEW',
  source: 'WEBSITE',
  priority: 'MEDIUM',
  followUpDate: null,
  assignedToUserId: null,
  archivedAt: null,
  organizationId: 'org-1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function renderPanel() {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <LeadScorePanel lead={LEAD} />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

describe('LeadScorePanel mutation safety (mandatory)', () => {
  beforeEach(() => {
    clearTokens()
    vi.mocked(getMe).mockResolvedValue({
      id: 'u1',
      email: 'a@b.com',
      firstName: 'A',
      lastName: 'B',
      roles: ['OWNER'],
      status: 'ACTIVE',
      organizationId: 'org-1',
      createdAt: '2026-01-01T00:00:00Z',
    })
    vi.mocked(getCurrentOrganization).mockResolvedValue({
      id: 'org-1',
      name: 'Acme',
      createdAt: '2026-01-01T00:00:00Z',
    })
    setTokens({ accessToken: 't', refreshToken: 'r' })
  })
  afterEach(() => clearTokens())

  it("never changes the lead's displayed actual priority even when the AI suggests a different one", async () => {
    vi.mocked(leadsApi.scoreLead).mockResolvedValue({
      leadId: 'l1',
      score: 87,
      priority: 'HIGH',
      reasoning: 'Strong buying signals.',
      recommendedAction: 'Call today.',
      generatedAt: '2026-01-02T00:00:00Z',
    })
    renderPanel()

    // The lead's real, authoritative priority (MEDIUM) is shown before scoring.
    await waitFor(() => expect(screen.getByText('MEDIUM')).toBeInTheDocument())

    await userEvent.click(await screen.findByRole('button', { name: 'Score with AI' }))

    await waitFor(() => expect(screen.getByText('87 / 100')).toBeInTheDocument())
    expect(screen.getByText('Strong buying signals.')).toBeInTheDocument()
    expect(screen.getByText('Call today.')).toBeInTheDocument()

    // Both the unchanged actual priority (MEDIUM) and the AI's separate
    // suggestion (HIGH) are visible at once — the mutation never merged
    // into or overwrote the lead's own field.
    expect(screen.getAllByText('MEDIUM')).toHaveLength(1)
    expect(screen.getAllByText('HIGH')).toHaveLength(1)
  })

  it('clears the previous result when scoring again', async () => {
    vi.mocked(leadsApi.scoreLead)
      .mockResolvedValueOnce({
        leadId: 'l1',
        score: 40,
        priority: 'LOW',
        reasoning: 'First pass.',
        recommendedAction: 'Wait.',
        generatedAt: '2026-01-02T00:00:00Z',
      })
      .mockResolvedValueOnce({
        leadId: 'l1',
        score: 90,
        priority: 'HIGH',
        reasoning: 'Second pass.',
        recommendedAction: 'Call now.',
        generatedAt: '2026-01-03T00:00:00Z',
      })
    renderPanel()

    await userEvent.click(await screen.findByRole('button', { name: 'Score with AI' }))
    await waitFor(() => expect(screen.getByText('40 / 100')).toBeInTheDocument())

    await userEvent.click(await screen.findByRole('button', { name: 'Score with AI' }))
    await waitFor(() => expect(screen.getByText('90 / 100')).toBeInTheDocument())
    expect(screen.queryByText('40 / 100')).not.toBeInTheDocument()
    expect(screen.queryByText('First pass.')).not.toBeInTheDocument()
  })

  it('shows a calm informational state when AI is disabled (503), not an alarming error', async () => {
    vi.mocked(leadsApi.scoreLead).mockRejectedValue(
      new ApiError({
        timestamp: '2026-01-01T00:00:00Z',
        status: 503,
        code: 'AI_DISABLED',
        message: 'AI is disabled for this organization',
        path: '/api/v1/leads/l1/score',
      }),
    )
    renderPanel()

    await userEvent.click(await screen.findByRole('button', { name: 'Score with AI' }))
    expect(await screen.findByText("AI features aren’t enabled for this workspace.")).toBeInTheDocument()
  })

  it('shows a retryable error for a provider/scoring failure (502), with no internal detail', async () => {
    vi.mocked(leadsApi.scoreLead).mockRejectedValue(
      new ApiError({
        timestamp: '2026-01-01T00:00:00Z',
        status: 502,
        code: 'AI_PROVIDER_ERROR',
        message: 'upstream timeout from provider xyz',
        path: '/api/v1/leads/l1/score',
      }),
    )
    renderPanel()

    await userEvent.click(await screen.findByRole('button', { name: 'Score with AI' }))
    expect(await screen.findByText('Unable to generate an AI score right now. Try again.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
    expect(screen.queryByText(/upstream timeout/)).not.toBeInTheDocument()
  })
})
