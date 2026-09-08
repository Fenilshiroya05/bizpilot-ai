import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { StatusBadge } from './StatusBadge'

describe('StatusBadge', () => {
  it('renders a success-styled badge for WON', () => {
    render(<StatusBadge status="WON" />)
    const badge = screen.getByText('WON')
    expect(badge.className).toContain('success')
  })

  it('renders a destructive-styled badge for LOST', () => {
    render(<StatusBadge status="LOST" />)
    const badge = screen.getByText('LOST')
    expect(badge.className).toContain('destructive')
  })

  it('replaces underscores with spaces for readability', () => {
    render(<StatusBadge status="PARTIALLY_PAID" />)
    expect(screen.getByText('PARTIALLY PAID')).toBeInTheDocument()
  })

  it('falls back to a neutral (muted) badge for an unrecognized status', () => {
    render(<StatusBadge status="SOMETHING_UNEXPECTED" />)
    const badge = screen.getByText('SOMETHING UNEXPECTED')
    expect(badge.className).toContain('bg-muted')
  })
})
