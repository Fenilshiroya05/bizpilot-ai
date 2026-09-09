import { describe, expect, it } from 'vitest'

import { formatBytes } from './fileSize'

describe('formatBytes', () => {
  it('formats byte values under 1 KB as B', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(500)).toBe('500 B')
    expect(formatBytes(1023)).toBe('1023 B')
  })

  it('formats KB values', () => {
    expect(formatBytes(1024)).toBe('1.0 KB')
    expect(formatBytes(2048)).toBe('2.0 KB')
    expect(formatBytes(15 * 1024)).toBe('15.0 KB')
  })

  it('formats MB values', () => {
    expect(formatBytes(1024 * 1024)).toBe('1.0 MB')
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB')
  })

  it('formats the 20 MB upload boundary', () => {
    expect(formatBytes(20 * 1024 * 1024)).toBe('20.0 MB')
  })

  it('formats GB values', () => {
    expect(formatBytes(2 * 1024 * 1024 * 1024)).toBe('2.0 GB')
  })
})
