import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '@/app/providers/AuthProvider'
import { getMe } from '@/features/auth/api'
import { getCurrentOrganization } from '@/features/settings/api'
import { clearTokens, setTokens } from '@/lib/api-client'
import { createTestQueryClient } from '@/test/render'
import type { Page, TaskResponse } from '@/types/api'
import { TasksListPage } from './TasksListPage'
import * as tasksApi from './api'

// See ProductsListPage.test.tsx (Phase 22) for why the real key builders
// must survive mocking a resource's own api module.
vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>()
  return { ...actual, searchTasks: vi.fn(), cancelTask: vi.fn() }
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
  vi.mocked(getCurrentOrganization).mockResolvedValue({
    id: 'org-1',
    name: 'Acme',
    createdAt: '2026-01-01T00:00:00Z',
  })
}

const TASK: TaskResponse = {
  id: 't1',
  title: 'Follow up with customer',
  description: null,
  status: 'TODO',
  priority: 'MEDIUM',
  assignedToUserId: null,
  customerId: null,
  leadId: null,
  dueDate: null,
  notes: null,
  organizationId: 'org-1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function taskPage(content: TaskResponse[]): Page<TaskResponse> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20, first: true, last: true, empty: content.length === 0 }
}

function renderPage() {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AuthProvider>
          <TasksListPage />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('TasksListPage', () => {
  beforeEach(() => {
    clearTokens()
    vi.mocked(tasksApi.searchTasks).mockResolvedValue(taskPage([TASK]))
  })
  afterEach(() => clearTokens())

  describe('RBAC visibility — Tasks intentionally differs from the Customer/Lead/Product pattern', () => {
    it('EMPLOYEE sees Create task and Edit, but not Cancel', async () => {
      mockUser(['EMPLOYEE'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await waitFor(() => expect(screen.getAllByText('Follow up with customer').length).toBeGreaterThan(0))
      expect(screen.getByRole('button', { name: /Create task/i })).toBeInTheDocument()

      await userEvent.click(screen.getByRole('button', { name: 'Actions for Follow up with customer' }))
      expect(screen.getByRole('menuitem', { name: 'Edit' })).toBeInTheDocument()
      expect(screen.queryByRole('menuitem', { name: 'Cancel' })).not.toBeInTheDocument()
    })

    it('SALES sees Create task and Edit, but not Cancel', async () => {
      mockUser(['SALES'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await waitFor(() => expect(screen.getAllByText('Follow up with customer').length).toBeGreaterThan(0))
      expect(screen.getByRole('button', { name: /Create task/i })).toBeInTheDocument()

      await userEvent.click(screen.getByRole('button', { name: 'Actions for Follow up with customer' }))
      expect(screen.getByRole('menuitem', { name: 'Edit' })).toBeInTheDocument()
      expect(screen.queryByRole('menuitem', { name: 'Cancel' })).not.toBeInTheDocument()
    })

    it('MANAGER sees Create task, Edit, and Cancel', async () => {
      mockUser(['MANAGER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await waitFor(() => expect(screen.getAllByText('Follow up with customer').length).toBeGreaterThan(0))
      await userEvent.click(screen.getByRole('button', { name: 'Actions for Follow up with customer' }))
      expect(screen.getByRole('menuitem', { name: 'Edit' })).toBeInTheDocument()
      expect(screen.getByRole('menuitem', { name: 'Cancel' })).toBeInTheDocument()
    })

    it('OWNER sees Create task, Edit, and Cancel', async () => {
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await waitFor(() => expect(screen.getAllByText('Follow up with customer').length).toBeGreaterThan(0))
      await userEvent.click(screen.getByRole('button', { name: 'Actions for Follow up with customer' }))
      expect(screen.getByRole('menuitem', { name: 'Edit' })).toBeInTheDocument()
      expect(screen.getByRole('menuitem', { name: 'Cancel' })).toBeInTheDocument()
    })
  })

  describe('search, filters, quick filters, pagination', () => {
    beforeEach(() => {
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
    })

    it('debounces search input into the q query param', async () => {
      renderPage()
      await waitFor(() => expect(screen.getAllByText('Follow up with customer').length).toBeGreaterThan(0))

      await userEvent.type(screen.getByRole('textbox', { name: 'Search tasks' }), 'Follow')
      await waitFor(() =>
        expect(tasksApi.searchTasks).toHaveBeenCalledWith(expect.objectContaining({ q: 'Follow' })),
      )
    })

    it('applies the status filter', async () => {
      renderPage()
      await waitFor(() => expect(screen.getAllByText('Follow up with customer').length).toBeGreaterThan(0))

      await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Status filter' }), 'IN_PROGRESS')
      await waitFor(() =>
        expect(tasksApi.searchTasks).toHaveBeenCalledWith(expect.objectContaining({ status: 'IN_PROGRESS' })),
      )
    })

    it('applies the priority filter', async () => {
      renderPage()
      await waitFor(() => expect(screen.getAllByText('Follow up with customer').length).toBeGreaterThan(0))

      await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Priority filter' }), 'URGENT')
      await waitFor(() =>
        expect(tasksApi.searchTasks).toHaveBeenCalledWith(expect.objectContaining({ priority: 'URGENT' })),
      )
    })

    it('My Tasks quick filter sends assignedToUserId=<current user>', async () => {
      renderPage()
      await waitFor(() => expect(screen.getAllByText('Follow up with customer').length).toBeGreaterThan(0))

      await userEvent.click(screen.getByRole('button', { name: 'My Tasks' }))
      await waitFor(() =>
        expect(tasksApi.searchTasks).toHaveBeenCalledWith(expect.objectContaining({ assignedToUserId: 'u1' })),
      )
    })

    it('Unassigned quick filter sends unassigned=true', async () => {
      renderPage()
      await waitFor(() => expect(screen.getAllByText('Follow up with customer').length).toBeGreaterThan(0))

      await userEvent.click(screen.getByRole('button', { name: 'Unassigned' }))
      await waitFor(() =>
        expect(tasksApi.searchTasks).toHaveBeenCalledWith(expect.objectContaining({ unassigned: true })),
      )
    })

    it('Due Today / Overdue quick filter sends dueDateOnOrBefore=<today>', async () => {
      renderPage()
      await waitFor(() => expect(screen.getAllByText('Follow up with customer').length).toBeGreaterThan(0))

      await userEvent.click(screen.getByRole('button', { name: 'Due Today / Overdue' }))
      await waitFor(() =>
        expect(tasksApi.searchTasks).toHaveBeenCalledWith(
          expect.objectContaining({ dueDateOnOrBefore: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/) }),
        ),
      )
    })

    it('shows pagination when there is more than one page', async () => {
      vi.mocked(tasksApi.searchTasks).mockResolvedValue({
        content: [TASK],
        totalElements: 25,
        totalPages: 2,
        number: 0,
        size: 20,
        first: true,
        last: false,
        empty: false,
      })
      renderPage()
      await waitFor(() => expect(screen.getByText('Page 1 of 2')).toBeInTheDocument())
    })
  })

  describe('loading, empty, and error states', () => {
    beforeEach(() => {
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
    })

    it('shows an empty state with a create CTA when there are no tasks at all', async () => {
      vi.mocked(tasksApi.searchTasks).mockResolvedValue(taskPage([]))
      renderPage()
      expect(await screen.findByText('No tasks yet')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Add your first task' })).toBeInTheDocument()
    })

    it('shows a retryable error state when the list request fails', async () => {
      vi.mocked(tasksApi.searchTasks).mockRejectedValue(new Error('network down'))
      renderPage()
      expect(await screen.findByText("We couldn't load your tasks")).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
    })
  })

  describe('cancel flow', () => {
    it('OWNER can cancel a task after confirming', async () => {
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      vi.mocked(tasksApi.cancelTask).mockResolvedValue(undefined)
      renderPage()

      await waitFor(() => expect(screen.getAllByText('Follow up with customer').length).toBeGreaterThan(0))
      await userEvent.click(screen.getByRole('button', { name: 'Actions for Follow up with customer' }))
      await userEvent.click(screen.getByRole('menuitem', { name: 'Cancel' }))
      expect(screen.getByRole('heading', { name: 'Cancel this task?' })).toBeInTheDocument()

      await userEvent.click(screen.getByRole('button', { name: 'Cancel task' }))
      await waitFor(() => expect(tasksApi.cancelTask).toHaveBeenCalledWith('t1'))
    })
  })
})
