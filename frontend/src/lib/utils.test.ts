import { describe, expect, it } from 'vitest'

import { buildQueryString } from './utils'

describe('buildQueryString', () => {
  it('builds a query string from provided values', () => {
    expect(buildQueryString({ q: 'acme', page: 0, size: 20 })).toBe('?q=acme&page=0&size=20')
  })

  it('omits undefined, null, and empty-string values', () => {
    expect(buildQueryString({ q: undefined, status: null, name: '', page: 0 })).toBe('?page=0')
  })

  it('returns an empty string when every value is omitted', () => {
    expect(buildQueryString({ q: undefined })).toBe('')
  })

  it('serializes booleans as plain strings', () => {
    expect(buildQueryString({ unassigned: true })).toBe('?unassigned=true')
  })
})
