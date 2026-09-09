import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '@/app/providers/AuthProvider'
import { getMe } from '@/features/auth/api'
import { getCurrentOrganization } from '@/features/settings/api'
import { clearTokens, setTokens } from '@/lib/api-client'
import { createTestQueryClient } from '@/test/render'
import type { TaskResponse } from '@/types/api'
import { TaskDetailPage } from './TaskDetailPage'
import * as tasksApi from './api'
import * as customersApi from '@/features/customers/api'
import * as leadsApi from '@/features/leads/api'

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>()
  return { ...actual, getTask: vi.fn(), cancelTask: vi.fn(), assignTask: vi.fn() }
})
vi.mock('@/features/customers/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/customers/api')>()
  return { ...actual, getCustomer: vi.fn() }
})
vi.mock('@/features/leads/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/leads/api')>()
  return { ...actual, getLead: vi.fn() }
})
vi.mock('@/features/auth/api')
vi.mock('@/features/settings/api')

function mockUser(roles: string[]) {
  vi.mocked(getMe).mockResolvedValue({
    id: 'u1',
    email: 'a@b.com',
    firstName: 'A',
    lastName: 'B',
    roles,
    status: 'ACTIVE',
    organizationId: 'org-1',
    createdAt: '2026-01-01T00:00:00Z',
  })
  vi.mocked(getCurrentOrganization).mockResolvedValue({ id: 'org-1', name: 'Acme', createdAt: '2026-01-01T00:00:00Z' })
}

function baseTask(overrides: Partial<TaskResponse> = {}): TaskResponse {
  return {
    id: 't1',
    title: 'Follow up with customer',
    description: 'Call about the renewal.',
    status: 'TODO',
    priority: 'HIGH',
    assignedToUserId: null,
    customerId: null,
    leadId: null,
    dueDate: '2026-10-01',
    notes: 'Initial note.',
    organizationId: 'org-1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function renderPage() {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/tasks/t1']}>
        <AuthProvider>
          <Routes>
            <Route path="/tasks/:id" element={<TaskDetailPage />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('TaskDetailPage', () => {
  beforeEach(() => {
    clearTokens()
  })
  afterEach(() => clearTokens())

  it('renders the task overview and notes', async () => {
    vi.mocked(tasksApi.getTask).mockResolvedValue(baseTask())
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Follow up with customer' })).toBeInTheDocument()
    expect(screen.getByText('Call about the renewal.')).toBeInTheDocument()
    expect(screen.getByText('Initial note.')).toBeInTheDocument()
    expect(screen.getByText('HIGH')).toBeInTheDocument()
    expect(screen.getByText('TODO')).toBeInTheDocument()
  })

  it('links the related Customer and Lead to their own detail pages', async () => {
    vi.mocked(tasksApi.getTask).mockResolvedValue(baseTask({ customerId: 'c1', leadId: 'l1' }))
    vi.mocked(customersApi.getCustomer).mockResolvedValue({
      id: 'c1',
      name: 'Acme Corp',
      company: null,
      email: null,
      phone: null,
      address: null,
      gstin: null,
      status: 'ACTIVE',
      notes: null,
      organizationId: 'org-1',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    })
    vi.mocked(leadsApi.getLead).mockResolvedValue({
      id: 'l1',
      name: 'Priya Sharma',
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
    })
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await screen.findByRole('heading', { name: 'Follow up with customer' })
    const customerLink = await screen.findByRole('link', { name: 'Acme Corp' })
    expect(customerLink).toHaveAttribute('href', '/customers/c1')
    const leadLink = await screen.findByRole('link', { name: 'Priya Sharma' })
    expect(leadLink).toHaveAttribute('href', '/leads/l1')
  })

  it('assignment: shows Assign to me, then Assigned to you + Unassign after clicking', async () => {
    // getTask is refetched after assignTask invalidates the detail query, so
    // it must reflect the post-assignment state — a static mockResolvedValue
    // would keep returning the stale pre-assignment task on refetch.
    let currentTask = baseTask()
    vi.mocked(tasksApi.getTask).mockImplementation(() => Promise.resolve(currentTask))
    vi.mocked(tasksApi.assignTask).mockImplementation(async (_id, payload) => {
      currentTask = { ...currentTask, assignedToUserId: payload.assigneeUserId }
      return currentTask
    })
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await screen.findByRole('heading', { name: 'Follow up with customer' })
    await userEvent.click(screen.getByRole('button', { name: 'Assign to me' }))

    await waitFor(() => expect(screen.getByText('Assigned to you')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Unassign' })).toBeInTheDocument()
  })

  it('unassignment: clicking Unassign returns to Assign to me', async () => {
    let currentTask = baseTask({ assignedToUserId: 'u1' })
    vi.mocked(tasksApi.getTask).mockImplementation(() => Promise.resolve(currentTask))
    vi.mocked(tasksApi.assignTask).mockImplementation(async (_id, payload) => {
      currentTask = { ...currentTask, assignedToUserId: payload.assigneeUserId }
      return currentTask
    })
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await screen.findByRole('heading', { name: 'Follow up with customer' })
    await userEvent.click(screen.getByRole('button', { name: 'Unassign' }))

    await waitFor(() => expect(screen.getByRole('button', { name: 'Assign to me' })).toBeInTheDocument())
  })

  describe('cancel visibility and confirmation', () => {
    it('OWNER sees Edit and Cancel task', async () => {
      vi.mocked(tasksApi.getTask).mockResolvedValue(baseTask())
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await screen.findByRole('heading', { name: 'Follow up with customer' })
      expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Cancel task' })).toBeInTheDocument()
    })

    it('SALES sees Edit but not Cancel task', async () => {
      vi.mocked(tasksApi.getTask).mockResolvedValue(baseTask())
      mockUser(['SALES'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await screen.findByRole('heading', { name: 'Follow up with customer' })
      expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Cancel task' })).not.toBeInTheDocument()
    })

    it('EMPLOYEE sees Edit but not Cancel task', async () => {
      vi.mocked(tasksApi.getTask).mockResolvedValue(baseTask())
      mockUser(['EMPLOYEE'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await screen.findByRole('heading', { name: 'Follow up with customer' })
      expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Cancel task' })).not.toBeInTheDocument()
    })

    it('requires confirmation before cancelling, then succeeds', async () => {
      vi.mocked(tasksApi.getTask).mockResolvedValue(baseTask())
      vi.mocked(tasksApi.cancelTask).mockResolvedValue(undefined)
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await screen.findByRole('heading', { name: 'Follow up with customer' })
      await userEvent.click(screen.getByRole('button', { name: 'Cancel task' }))
      expect(screen.getByRole('heading', { name: 'Cancel this task?' })).toBeInTheDocument()

      const dialogConfirm = screen.getAllByRole('button', { name: 'Cancel task' })
      await userEvent.click(dialogConfirm[dialogConfirm.length - 1]!)
      await waitFor(() => expect(tasksApi.cancelTask).toHaveBeenCalledWith('t1'))
    })

    it('hides Edit and Cancel once the task is already CANCELLED', async () => {
      vi.mocked(tasksApi.getTask).mockResolvedValue(baseTask({ status: 'CANCELLED' }))
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await screen.findByRole('heading', { name: 'Follow up with customer' })
      expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Cancel task' })).not.toBeInTheDocument()
      expect(screen.getByText(/This task has been cancelled/)).toBeInTheDocument()
    })
  })
})
