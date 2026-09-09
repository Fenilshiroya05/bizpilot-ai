import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi, beforeEach } from 'vitest'

import { renderWithClient } from '@/test/render'
import { ApiError } from '@/lib/api-client'
import type { CustomerResponse, LeadResponse, Page, TaskResponse } from '@/types/api'
import { TaskFormDialog } from './TaskFormDialog'
import * as tasksApi from '@/features/tasks/api'
import * as customersApi from '@/features/customers/api'
import * as leadsApi from '@/features/leads/api'

// See ProductFormDialog.test.tsx for why the real key builders must survive mocking.
vi.mock('@/features/tasks/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/tasks/api')>()
  return { ...actual, createTask: vi.fn(), updateTask: vi.fn() }
})
vi.mock('@/features/customers/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/customers/api')>()
  return { ...actual, searchCustomers: vi.fn(), getCustomer: vi.fn() }
})
vi.mock('@/features/leads/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/leads/api')>()
  return { ...actual, searchLeads: vi.fn(), getLead: vi.fn() }
})

const CUSTOMER: CustomerResponse = {
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
}

const LEAD: LeadResponse = {
  id: 'l1',
  name: 'Priya Sharma',
  company: 'Sharma Textiles',
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

const TASK: TaskResponse = {
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
}

function customerPage(content: CustomerResponse[]): Page<CustomerResponse> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 10, first: true, last: true, empty: content.length === 0 }
}

function leadPage(content: LeadResponse[]): Page<LeadResponse> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 10, first: true, last: true, empty: content.length === 0 }
}

describe('TaskFormDialog', () => {
  beforeEach(() => {
    vi.mocked(customersApi.searchCustomers).mockResolvedValue(customerPage([CUSTOMER]))
    vi.mocked(leadsApi.searchLeads).mockResolvedValue(leadPage([LEAD]))
  })

  it('shows a validation error when title is missing', async () => {
    renderWithClient(<TaskFormDialog open onOpenChange={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Create task' }))
    expect(await screen.findByText('Title is required')).toBeInTheDocument()
    expect(tasksApi.createTask).not.toHaveBeenCalled()
  })

  it('rejects a title longer than 255 characters', async () => {
    renderWithClient(<TaskFormDialog open onOpenChange={vi.fn()} />)

    await userEvent.type(screen.getByLabelText('Title'), 'a'.repeat(256))
    await userEvent.click(screen.getByRole('button', { name: 'Create task' }))
    expect(await screen.findByText('Title must be at most 255 characters')).toBeInTheDocument()
  })

  it('rejects a description longer than 2000 characters', async () => {
    renderWithClient(<TaskFormDialog open onOpenChange={vi.fn()} />)

    await userEvent.type(screen.getByLabelText('Title'), 'Valid title')
    await userEvent.type(screen.getByLabelText('Description'), 'a'.repeat(2001))
    await userEvent.click(screen.getByRole('button', { name: 'Create task' }))
    expect(await screen.findByText('Description must be at most 2000 characters')).toBeInTheDocument()
  })

  it('defaults priority to MEDIUM and does not show a Status or Notes field on create', () => {
    renderWithClient(<TaskFormDialog open onOpenChange={vi.fn()} />)

    expect(screen.getByLabelText('Priority')).toHaveValue('MEDIUM')
    expect(screen.queryByLabelText('Status')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Notes')).not.toBeInTheDocument()
  })

  it('creates a task with title, priority, due date, customer, and lead', async () => {
    vi.mocked(tasksApi.createTask).mockResolvedValue(TASK)
    const onOpenChange = vi.fn()
    renderWithClient(<TaskFormDialog open onOpenChange={onOpenChange} />)

    await userEvent.type(screen.getByLabelText('Title'), 'Call the customer')
    await userEvent.selectOptions(screen.getByLabelText('Priority'), 'URGENT')
    await userEvent.type(screen.getByLabelText('Due date'), '2026-11-01')

    await userEvent.type(screen.getByRole('combobox', { name: 'Customer' }), 'Acme')
    await userEvent.click(await screen.findByRole('option', { name: /Acme Corp/ }))

    await userEvent.type(screen.getByRole('combobox', { name: 'Lead' }), 'Priya')
    await userEvent.click(await screen.findByRole('option', { name: /Priya Sharma/ }))

    await userEvent.click(screen.getByRole('button', { name: 'Create task' }))

    await waitFor(() =>
      expect(tasksApi.createTask).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'Call the customer',
          priority: 'URGENT',
          dueDate: '2026-11-01',
          customerId: 'c1',
          leadId: 'l1',
        }),
      ),
    )
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
  })

  it('surfaces an API error as a generic alert', async () => {
    vi.mocked(tasksApi.createTask).mockRejectedValue(
      new ApiError({
        timestamp: '2026-01-01T00:00:00Z',
        status: 400,
        code: 'INVALID_TASK_DATA',
        message: 'title cannot be blank',
        path: '/api/v1/tasks',
      }),
    )
    renderWithClient(<TaskFormDialog open onOpenChange={vi.fn()} />)

    await userEvent.type(screen.getByLabelText('Title'), 'Call the customer')
    await userEvent.click(screen.getByRole('button', { name: 'Create task' }))

    expect(await screen.findByText('title cannot be blank')).toBeInTheDocument()
  })

  describe('edit mode', () => {
    it('pre-fills the form and shows Status and Notes fields', async () => {
      renderWithClient(<TaskFormDialog open onOpenChange={vi.fn()} task={TASK} />)

      expect(await screen.findByLabelText('Title')).toHaveValue('Follow up with customer')
      expect(screen.getByLabelText('Description')).toHaveValue('Call about the renewal.')
      expect(screen.getByLabelText('Priority')).toHaveValue('HIGH')
      expect(screen.getByLabelText('Due date')).toHaveValue('2026-10-01')
      expect(screen.getByLabelText('Status')).toHaveValue('TODO')
      expect(screen.getByLabelText('Notes')).toHaveValue('Initial note.')
      expect(screen.getByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    })

    it('does not offer CANCELLED as a status option', async () => {
      renderWithClient(<TaskFormDialog open onOpenChange={vi.fn()} task={TASK} />)
      await screen.findByLabelText('Title')

      const statusSelect = screen.getByLabelText('Status') as HTMLSelectElement
      const optionValues = Array.from(statusSelect.options).map((o) => o.value)
      expect(optionValues).not.toContain('CANCELLED')
      expect(optionValues).toEqual(['TODO', 'IN_PROGRESS', 'COMPLETED'])
    })

    it('resolves and shows the related Customer/Lead names while loading, then the form', async () => {
      vi.mocked(customersApi.getCustomer).mockResolvedValue(CUSTOMER)
      vi.mocked(leadsApi.getLead).mockResolvedValue(LEAD)
      const taskWithRelations: TaskResponse = { ...TASK, customerId: 'c1', leadId: 'l1' }

      renderWithClient(<TaskFormDialog open onOpenChange={vi.fn()} task={taskWithRelations} />)

      expect(await screen.findByLabelText('Title')).toHaveValue('Follow up with customer')
      expect(screen.getByRole('combobox', { name: 'Customer' })).toHaveValue('Acme Corp')
      expect(screen.getByRole('combobox', { name: 'Lead' })).toHaveValue('Priya Sharma')
    })

    it('updates a task, editing title/priority/due date/status/notes', async () => {
      vi.mocked(tasksApi.updateTask).mockResolvedValue(TASK)
      const onOpenChange = vi.fn()
      renderWithClient(<TaskFormDialog open onOpenChange={onOpenChange} task={TASK} />)

      await userEvent.clear(await screen.findByLabelText('Title'))
      await userEvent.type(screen.getByLabelText('Title'), 'Updated title')
      await userEvent.selectOptions(screen.getByLabelText('Priority'), 'LOW')
      await userEvent.selectOptions(screen.getByLabelText('Status'), 'IN_PROGRESS')
      await userEvent.clear(screen.getByLabelText('Notes'))
      await userEvent.type(screen.getByLabelText('Notes'), 'Updated note.')
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      await waitFor(() =>
        expect(tasksApi.updateTask).toHaveBeenCalledWith(
          't1',
          expect.objectContaining({
            title: 'Updated title',
            priority: 'LOW',
            status: 'IN_PROGRESS',
            notes: 'Updated note.',
          }),
        ),
      )
      await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
    })

    it('clears the due date when the field is emptied', async () => {
      vi.mocked(tasksApi.updateTask).mockResolvedValue(TASK)
      renderWithClient(<TaskFormDialog open onOpenChange={vi.fn()} task={TASK} />)

      await userEvent.clear(await screen.findByLabelText('Due date'))
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      await waitFor(() =>
        expect(tasksApi.updateTask).toHaveBeenCalledWith(
          't1',
          expect.objectContaining({ clearDueDate: true }),
        ),
      )
    })

    it('clears the related customer when cleared via the combobox', async () => {
      vi.mocked(customersApi.getCustomer).mockResolvedValue(CUSTOMER)
      vi.mocked(tasksApi.updateTask).mockResolvedValue(TASK)
      const taskWithCustomer: TaskResponse = { ...TASK, customerId: 'c1' }
      renderWithClient(<TaskFormDialog open onOpenChange={vi.fn()} task={taskWithCustomer} />)

      await screen.findByLabelText('Title')
      await userEvent.click(screen.getByRole('button', { name: 'Clear Customer' }))
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      await waitFor(() =>
        expect(tasksApi.updateTask).toHaveBeenCalledWith(
          't1',
          expect.objectContaining({ clearCustomerId: true }),
        ),
      )
    })

    it('clears the related lead when cleared via the combobox', async () => {
      vi.mocked(leadsApi.getLead).mockResolvedValue(LEAD)
      vi.mocked(tasksApi.updateTask).mockResolvedValue(TASK)
      const taskWithLead: TaskResponse = { ...TASK, leadId: 'l1' }
      renderWithClient(<TaskFormDialog open onOpenChange={vi.fn()} task={taskWithLead} />)

      await screen.findByLabelText('Title')
      await userEvent.click(screen.getByRole('button', { name: 'Clear Lead' }))
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      await waitFor(() =>
        expect(tasksApi.updateTask).toHaveBeenCalledWith(
          't1',
          expect.objectContaining({ clearLeadId: true }),
        ),
      )
    })
  })
})
