import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '@/app/providers/AuthProvider'
import * as authApi from '@/features/auth/api'
import { LoginPage } from '@/features/auth/LoginPage'
import { ApiError, clearTokens } from '@/lib/api-client'
import { renderWithClient } from '@/test/render'

vi.mock('@/features/auth/api')
vi.mock('@/features/settings/api', () => ({ getCurrentOrganization: vi.fn() }))

function renderLoginPage() {
  return renderWithClient(
    <AuthProvider>
      <LoginPage />
    </AuthProvider>,
    { route: '/auth/login' },
  )
}

describe('LoginPage', () => {
  beforeEach(() => clearTokens())
  afterEach(() => clearTokens())

  it('renders email and password fields', () => {
    renderLoginPage()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toBeInTheDocument()
  })

  it('shows a validation error instead of submitting an empty form', async () => {
    renderLoginPage()

    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Email is required')).toBeInTheDocument()
    expect(authApi.login).not.toHaveBeenCalled()
  })

  it('toggles password visibility', async () => {
    renderLoginPage()
    const passwordInput = screen.getByLabelText('Password')
    expect(passwordInput).toHaveAttribute('type', 'password')

    await userEvent.click(screen.getByRole('button', { name: 'Show password' }))
    expect(passwordInput).toHaveAttribute('type', 'text')
  })

  it('shows the backend error message when login fails', async () => {
    vi.mocked(authApi.login).mockRejectedValue(
      new ApiError({
        timestamp: '2026-01-01T00:00:00Z',
        status: 401,
        code: 'INVALID_CREDENTIALS',
        message: 'Invalid email or password',
        path: '/api/v1/auth/login',
      }),
    )
    renderLoginPage()

    await userEvent.type(screen.getByLabelText('Email'), 'a@b.com')
    await userEvent.type(screen.getByLabelText('Password'), 'password123')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Invalid email or password')).toBeInTheDocument()
  })
})
