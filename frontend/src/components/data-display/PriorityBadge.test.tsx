import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { PriorityBadge } from './PriorityBadge'

describe('PriorityBadge', () => {
  it('renders the priority as visible text, not color alone', () => {
    render(<PriorityBadge priority="HIGH" />)
    expect(screen.getByText('HIGH')).toBeInTheDocument()
  })

  it('uses a dot + text treatment distinct from StatusBadge (no pill background)', () => {
    render(<PriorityBadge priority="MEDIUM" />)
    const badge = screen.getByText('MEDIUM')
    expect(badge.className).not.toContain('bg-')
  })
})
