import type { Page, Route } from '@playwright/test'

/**
 * Deterministic API fixtures for browser smoke tests only. These mocks live
 * entirely in frontend test code (`page.route`, intercepted client-side by
 * the browser before any request leaves the machine) — they never touch
 * production API code, and a real backend is not required to run these
 * tests. Test-only credentials; nothing here is a real secret.
 */

export const TEST_EMAIL = 'e2e-test@example.com'
export const TEST_PASSWORD = 'E2ePassw0rd!'

export const TEST_USER = {
  id: 'e2e-user-1111-2222-3333-444444444444',
  email: TEST_EMAIL,
  firstName: 'Ellie',
  lastName: 'Example',
  roles: ['OWNER'],
  status: 'ACTIVE',
  organizationId: 'e2e-org-1111-2222-3333-444444444444',
  createdAt: '2026-01-01T00:00:00Z',
}

export const TEST_ORGANIZATION = {
  id: TEST_USER.organizationId,
  name: 'Acme Testing Co',
  createdAt: '2026-01-01T00:00:00Z',
}

/** Realistic, non-zero values for all seven backend-supported metrics. */
export const TEST_ANALYTICS = {
  totalCustomers: 128,
  newLeads: 14,
  qualifiedLeads: 6,
  conversionRate: 42.5,
  revenue: 184500.5,
  outstandingInvoicesCount: 5,
  outstandingInvoicesTotal: 32750.75,
  pendingFollowUps: 3,
}

export const TEST_ANALYTICS_ZERO = {
  totalCustomers: 0,
  newLeads: 0,
  qualifiedLeads: 0,
  conversionRate: 0,
  revenue: 0,
  outstandingInvoicesCount: 0,
  outstandingInvoicesTotal: 0,
  pendingFollowUps: 0,
}

// ---------------------------------------------------------------------------
// Phase 26 — Dashboard analytics (charts/insights) fixtures
// ---------------------------------------------------------------------------

export const TEST_REVENUE_TREND = [
  { period: '2026-08-01', revenue: 1000 },
  { period: '2026-08-15', revenue: 2500 },
  { period: '2026-08-30', revenue: 1800 },
]

export const TEST_REVENUE_TREND_ZERO = [
  { period: '2026-08-01', revenue: 0 },
  { period: '2026-08-30', revenue: 0 },
]

export const TEST_LEAD_FUNNEL = [
  { status: 'NEW', count: 5 },
  { status: 'CONTACTED', count: 3 },
  { status: 'QUALIFIED', count: 2 },
  { status: 'PROPOSAL', count: 1 },
  { status: 'NEGOTIATION', count: 1 },
  { status: 'WON', count: 2 },
  { status: 'LOST', count: 1 },
]

export const TEST_LEAD_SOURCES = [
  { source: 'WEBSITE', count: 6 },
  { source: 'REFERRAL', count: 3 },
]

export const TEST_SALES_PIPELINE = [
  { status: 'DRAFT', count: 2, amount: 5000 },
  { status: 'ACCEPTED', count: 1, amount: 8000 },
]

export const TEST_TOP_CUSTOMERS = [
  { customerId: 'e2e-topcust-1', customerName: 'Acme Retail Pvt Ltd', revenue: 8000 },
  { customerId: 'e2e-topcust-2', customerName: 'Bright Traders', revenue: 5000 },
]

function mockJsonEndpoint(path: string, defaultBody: unknown) {
  return async (page: Page, body: unknown = defaultBody, options: { status?: number; delayMs?: number } = {}) => {
    await page.route(`**${path}`, async (route) => {
      if (options.delayMs) {
        await new Promise((resolve) => setTimeout(resolve, options.delayMs))
      }
      if (options.status && options.status >= 400) {
        return json(route, options.status, apiError(options.status, 'INTERNAL_ERROR', 'An unexpected error occurred', path))
      }
      return json(route, 200, body)
    })
  }
}

export const mockRevenueTrend = mockJsonEndpoint('/api/v1/analytics/revenue-trend', TEST_REVENUE_TREND)
export const mockLeadFunnel = mockJsonEndpoint('/api/v1/analytics/lead-funnel', TEST_LEAD_FUNNEL)
export const mockLeadSources = mockJsonEndpoint('/api/v1/analytics/lead-sources', TEST_LEAD_SOURCES)
export const mockSalesPipeline = mockJsonEndpoint('/api/v1/analytics/sales-pipeline', TEST_SALES_PIPELINE)
export const mockTopCustomers = mockJsonEndpoint('/api/v1/analytics/top-customers', TEST_TOP_CUSTOMERS)

/** All five Phase 26 chart/widget endpoints at once, with realistic non-empty defaults. */
export async function mockDashboardAnalytics(page: Page) {
  await mockRevenueTrend(page)
  await mockLeadFunnel(page)
  await mockLeadSources(page)
  await mockSalesPipeline(page)
  await mockTopCustomers(page)
}

/** Same five endpoints, all returning zero/empty data (for the dashboard's empty-state tests). */
export async function mockDashboardAnalyticsEmpty(page: Page) {
  await mockRevenueTrend(page, TEST_REVENUE_TREND_ZERO)
  await mockLeadFunnel(page, [])
  await mockLeadSources(page, [])
  await mockSalesPipeline(page, [])
  await mockTopCustomers(page, [])
}

function json(route: Route, status: number, body: unknown) {
  return route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

function apiError(status: number, code: string, message: string, path: string) {
  return { timestamp: new Date().toISOString(), status, code, message, path }
}

export async function mockLoginSuccess(page: Page) {
  await page.route('**/api/v1/auth/login', (route) =>
    json(route, 200, { accessToken: 'e2e-access-token', refreshToken: 'e2e-refresh-token' }),
  )
}

export async function mockLoginFailure(page: Page) {
  await page.route('**/api/v1/auth/login', (route) =>
    json(route, 401, apiError(401, 'INVALID_CREDENTIALS', 'Invalid email or password', '/api/v1/auth/login')),
  )
}

export async function mockLogoutSuccess(page: Page) {
  await page.route('**/api/v1/auth/logout', (route) => route.fulfill({ status: 204 }))
}

export async function mockSession(page: Page) {
  await page.route('**/api/v1/auth/me', (route) => json(route, 200, TEST_USER))
  await page.route('**/api/v1/organizations/current', (route) => json(route, 200, TEST_ORGANIZATION))
}

export async function mockAnalytics(
  page: Page,
  body: unknown = TEST_ANALYTICS,
  options: { status?: number; delayMs?: number } = {},
) {
  await page.route('**/api/v1/analytics/summary', async (route) => {
    if (options.delayMs) {
      await new Promise((resolve) => setTimeout(resolve, options.delayMs))
    }
    if (options.status && options.status >= 400) {
      return json(
        route,
        options.status,
        apiError(options.status, 'INTERNAL_ERROR', 'An unexpected error occurred', '/api/v1/analytics/summary'),
      )
    }
    return json(route, 200, body)
  })
}

/**
 * Full mock set for a successful, authenticated session landing on the
 * dashboard. Also wires the five Phase 26 chart/widget endpoints (with
 * realistic non-empty defaults) so every existing spec that already calls
 * this helper keeps working without needing its own changes — dashboard
 * child widgets that would otherwise hit an unmocked real network request.
 */
export async function mockAuthenticatedSession(page: Page, analytics: unknown = TEST_ANALYTICS) {
  await mockLoginSuccess(page)
  await mockSession(page)
  await mockAnalytics(page, analytics)
  await mockDashboardAnalytics(page)
  await mockLogoutSuccess(page)
}

/** Same as {@link mockAuthenticatedSession}, but as a specific role set (for RBAC tests). */
export async function mockAuthenticatedSessionAs(page: Page, roles: string[], analytics: unknown = TEST_ANALYTICS) {
  await mockLoginSuccess(page)
  await mockSessionAs(page, roles)
  await mockAnalytics(page, analytics)
  await mockDashboardAnalytics(page)
  await mockLogoutSuccess(page)
}

export async function loginViaUi(page: Page) {
  await page.goto('/auth/login')
  await page.getByLabel('Email').fill(TEST_EMAIL)
  await page.getByLabel('Password', { exact: true }).fill(TEST_PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.waitForURL('**/dashboard')
}

/**
 * Client-side (SPA) navigation via the real sidebar link — never
 * `page.goto('/customers')`/`page.goto('/leads')` after login. Tokens are
 * memory-only (Phase 20's locked decision); a full `page.goto` navigation
 * reloads the page and silently loses the session, bouncing back to
 * `/auth/login`, exactly like a real user's browser reload would.
 */
export async function goToCustomers(page: Page) {
  await page.getByRole('complementary').getByRole('link', { name: 'Customers' }).click()
  await page.waitForURL('**/customers')
}

export async function goToLeads(page: Page) {
  await page.getByRole('complementary').getByRole('link', { name: 'Leads' }).click()
  await page.waitForURL('**/leads')
}

export async function goToProducts(page: Page) {
  await page.getByRole('complementary').getByRole('link', { name: 'Products' }).click()
  await page.waitForURL('**/products')
}

export async function goToQuotations(page: Page) {
  await page.getByRole('complementary').getByRole('link', { name: 'Quotations' }).click()
  await page.waitForURL('**/quotations')
}

export async function goToTasks(page: Page) {
  await page.getByRole('complementary').getByRole('link', { name: 'Tasks' }).click()
  await page.waitForURL('**/tasks')
}

export async function goToDocuments(page: Page) {
  await page.getByRole('complementary').getByRole('link', { name: 'Documents' }).click()
  await page.waitForURL('**/documents')
}

// ---------------------------------------------------------------------------
// Phase 21 — Customers / Leads / AI Lead Scoring fixtures
// ---------------------------------------------------------------------------

/** Mocks `/auth/me` with a specific role set instead of the default OWNER. */
export async function mockSessionAs(page: Page, roles: string[]) {
  await page.route('**/api/v1/auth/me', (route) => json(route, 200, { ...TEST_USER, roles }))
  await page.route('**/api/v1/organizations/current', (route) => json(route, 200, TEST_ORGANIZATION))
}

interface MockPage<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
  empty: boolean
}

/** Slices `all` by `page`/`size`, mirroring Spring Data's real Page<T> shape. */
function pageOf<T>(all: T[], page = 0, size = 20): MockPage<T> {
  const totalPages = Math.max(Math.ceil(all.length / size), all.length > 0 ? 1 : 0)
  const content = all.slice(page * size, page * size + size)
  return {
    content,
    totalElements: all.length,
    totalPages,
    number: page,
    size,
    first: page === 0,
    last: page >= totalPages - 1,
    empty: content.length === 0,
  }
}

interface MockActivity {
  id: string
  type: string
  content: string
  createdByUserId: string
  createdAt: string
}

export const TEST_CUSTOMER = {
  id: 'e2e-customer-1',
  name: 'Acme Retail Pvt Ltd',
  company: 'Acme Retail' as string | null,
  email: 'contact@acmeretail.example' as string | null,
  phone: '+91 98765 43210' as string | null,
  address: '221B, MG Road, Bengaluru' as string | null,
  gstin: null as string | null,
  status: 'ACTIVE' as string,
  notes: null as string | null,
  organizationId: TEST_USER.organizationId,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

/**
 * A small, real, in-memory CRUD simulation behind `page.route` — not a fake
 * UI. Every request/response shape matches the real backend contract
 * exactly (see backend/src/main/java/com/bizpilot/crm/controller/
 * CustomerController.java); this only replaces the network boundary so the
 * suite is deterministic and needs no real backend.
 */
export async function mockCustomersResource(page: Page, initial: (typeof TEST_CUSTOMER)[] = [TEST_CUSTOMER]) {
  const customers = initial.map((c) => ({ ...c }))
  const historyByCustomer = new Map<string, MockActivity[]>(
    customers.map((c) => [
      c.id,
      [{ id: `${c.id}-created`, type: 'CREATED', content: 'Customer created', createdByUserId: TEST_USER.id, createdAt: c.createdAt }],
    ]),
  )
  let nextId = customers.length + 1

  await page.route('**/api/v1/customers*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'GET') {
      const status = url.searchParams.get('status')
      const q = url.searchParams.get('q')?.toLowerCase()
      let filtered = status ? customers.filter((c) => c.status === status) : customers.filter((c) => c.status !== 'ARCHIVED')
      if (q) {
        filtered = filtered.filter(
          (c) =>
            c.name.toLowerCase().includes(q) ||
            (c.company ?? '').toLowerCase().includes(q) ||
            (c.email ?? '').toLowerCase().includes(q),
        )
      }
      const page = Number(url.searchParams.get('page') ?? '0')
      const size = Number(url.searchParams.get('size') ?? '20')
      return json(route, 200, pageOf(filtered, page, size))
    }
    if (request.method() === 'POST') {
      const body = (request.postDataJSON() ?? {}) as Record<string, string | undefined>
      if (!body.name || body.name.trim() === '') {
        return json(route, 400, apiError(400, 'INVALID_CUSTOMER_DATA', 'name cannot be blank', url.pathname))
      }
      if (body.email && customers.some((c) => c.email === body.email)) {
        return json(
          route,
          409,
          apiError(409, 'DUPLICATE_CUSTOMER', `A customer with email '${body.email}' already exists in this organization`, url.pathname),
        )
      }
      const now = new Date().toISOString()
      const created = {
        id: `e2e-customer-${nextId++}`,
        name: body.name,
        company: body.company || null,
        email: body.email || null,
        phone: body.phone || null,
        address: body.address || null,
        gstin: body.gstin || null,
        status: 'ACTIVE',
        notes: body.notes || null,
        organizationId: TEST_USER.organizationId,
        createdAt: now,
        updatedAt: now,
      }
      customers.push(created)
      historyByCustomer.set(created.id, [
        { id: `${created.id}-created`, type: 'CREATED', content: 'Customer created', createdByUserId: TEST_USER.id, createdAt: now },
      ])
      return json(route, 201, created)
    }
    return route.fallback()
  })

  await page.route('**/api/v1/customers/*/history*', (route) => {
    const id = new URL(route.request().url()).pathname.split('/')[4]
    return json(route, 200, pageOf(historyByCustomer.get(id ?? '') ?? []))
  })

  await page.route('**/api/v1/customers/*/notes*', async (route) => {
    if (route.request().method() !== 'POST') return route.fallback()
    const id = new URL(route.request().url()).pathname.split('/')[4] ?? ''
    const body = (route.request().postDataJSON() ?? {}) as { content: string }
    const note: MockActivity = {
      id: `note-${Date.now()}`,
      type: 'NOTE',
      content: body.content,
      createdByUserId: TEST_USER.id,
      createdAt: new Date().toISOString(),
    }
    const history = historyByCustomer.get(id) ?? []
    history.unshift(note)
    historyByCustomer.set(id, history)
    return json(route, 201, note)
  })

  await page.route('**/api/v1/customers/*', async (route) => {
    const request = route.request()
    const pathname = new URL(request.url()).pathname
    const id = pathname.split('/').pop()
    const customer = customers.find((c) => c.id === id)
    if (!customer) return json(route, 404, apiError(404, 'CUSTOMER_NOT_FOUND', 'Customer not found', pathname))

    if (request.method() === 'GET') return json(route, 200, customer)
    if (request.method() === 'PATCH') {
      if (customer.status === 'ARCHIVED') {
        return json(route, 409, apiError(409, 'CUSTOMER_ARCHIVED', 'This customer is archived and cannot be modified', pathname))
      }
      const body = (request.postDataJSON() ?? {}) as Record<string, unknown>
      for (const key of ['name', 'company', 'email', 'phone', 'address', 'gstin', 'notes', 'status'] as const) {
        if (body[key] !== undefined) (customer as Record<string, unknown>)[key] = body[key]
      }
      customer.updatedAt = new Date().toISOString()
      return json(route, 200, customer)
    }
    if (request.method() === 'DELETE') {
      customer.status = 'ARCHIVED'
      return route.fulfill({ status: 204 })
    }
    return route.fallback()
  })

  return { customers }
}

export const TEST_LEAD = {
  id: 'e2e-lead-1',
  name: 'Priya Sharma',
  company: 'Sharma Textiles' as string | null,
  email: 'priya@sharmatextiles.example' as string | null,
  phone: '+91 90000 11111' as string | null,
  status: 'NEW' as string,
  source: 'WEBSITE' as string,
  priority: 'MEDIUM' as string,
  followUpDate: null as string | null,
  assignedToUserId: null as string | null,
  archivedAt: null as string | null,
  organizationId: TEST_USER.organizationId,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

export async function mockLeadsResource(page: Page, initial: (typeof TEST_LEAD)[] = [TEST_LEAD]) {
  const leads = initial.map((l) => ({ ...l }))
  const historyByLead = new Map<string, MockActivity[]>(
    leads.map((l) => [
      l.id,
      [{ id: `${l.id}-created`, type: 'CREATED', content: 'Lead created', createdByUserId: TEST_USER.id, createdAt: l.createdAt }],
    ]),
  )
  let nextId = leads.length + 1

  await page.route('**/api/v1/leads*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'GET') {
      const status = url.searchParams.get('status')
      const source = url.searchParams.get('source')
      const priority = url.searchParams.get('priority')
      const assignedToUserId = url.searchParams.get('assignedToUserId')
      const unassigned = url.searchParams.get('unassigned') === 'true'
      const followUpBefore = url.searchParams.get('followUpBefore')
      const archived = url.searchParams.get('archived') === 'true'
      const q = url.searchParams.get('q')?.toLowerCase()

      let filtered = leads.filter((l) => (archived ? !!l.archivedAt : !l.archivedAt))
      if (status) filtered = filtered.filter((l) => l.status === status)
      if (source) filtered = filtered.filter((l) => l.source === source)
      if (priority) filtered = filtered.filter((l) => l.priority === priority)
      if (assignedToUserId) filtered = filtered.filter((l) => l.assignedToUserId === assignedToUserId)
      if (unassigned) filtered = filtered.filter((l) => l.assignedToUserId === null)
      if (followUpBefore) filtered = filtered.filter((l) => l.followUpDate && l.followUpDate <= followUpBefore)
      if (q) {
        filtered = filtered.filter(
          (l) => l.name.toLowerCase().includes(q) || (l.company ?? '').toLowerCase().includes(q),
        )
      }
      const page = Number(url.searchParams.get('page') ?? '0')
      const size = Number(url.searchParams.get('size') ?? '20')
      return json(route, 200, pageOf(filtered, page, size))
    }
    if (request.method() === 'POST') {
      const body = (request.postDataJSON() ?? {}) as Record<string, string | undefined>
      if (!body.name || body.name.trim() === '') {
        return json(route, 400, apiError(400, 'INVALID_LEAD_DATA', 'name cannot be blank', url.pathname))
      }
      if (!body.source) {
        return json(route, 400, apiError(400, 'VALIDATION_ERROR', 'source is required', url.pathname))
      }
      const now = new Date().toISOString()
      const created = {
        id: `e2e-lead-${nextId++}`,
        name: body.name,
        company: body.company || null,
        email: body.email || null,
        phone: body.phone || null,
        status: 'NEW',
        source: body.source,
        priority: body.priority || 'MEDIUM',
        followUpDate: body.followUpDate || null,
        assignedToUserId: null,
        archivedAt: null,
        organizationId: TEST_USER.organizationId,
        createdAt: now,
        updatedAt: now,
      }
      leads.push(created)
      historyByLead.set(created.id, [
        { id: `${created.id}-created`, type: 'CREATED', content: 'Lead created', createdByUserId: TEST_USER.id, createdAt: now },
      ])
      return json(route, 201, created)
    }
    return route.fallback()
  })

  await page.route('**/api/v1/leads/*/history*', (route) => {
    const id = new URL(route.request().url()).pathname.split('/')[4]
    return json(route, 200, pageOf(historyByLead.get(id ?? '') ?? []))
  })

  await page.route('**/api/v1/leads/*/notes*', async (route) => {
    if (route.request().method() !== 'POST') return route.fallback()
    const id = new URL(route.request().url()).pathname.split('/')[4] ?? ''
    const body = (route.request().postDataJSON() ?? {}) as { content: string }
    const note: MockActivity = {
      id: `note-${Date.now()}`,
      type: 'NOTE',
      content: body.content,
      createdByUserId: TEST_USER.id,
      createdAt: new Date().toISOString(),
    }
    const history = historyByLead.get(id) ?? []
    history.unshift(note)
    historyByLead.set(id, history)
    return json(route, 201, note)
  })

  await page.route('**/api/v1/leads/*/assign', async (route) => {
    const id = new URL(route.request().url()).pathname.split('/')[4] ?? ''
    const lead = leads.find((l) => l.id === id)
    if (!lead) return json(route, 404, apiError(404, 'LEAD_NOT_FOUND', 'Lead not found', route.request().url()))
    const body = (route.request().postDataJSON() ?? {}) as { assigneeUserId: string | null }
    const previous = lead.assignedToUserId
    lead.assignedToUserId = body.assigneeUserId
    const history = historyByLead.get(id) ?? []
    history.unshift({
      id: `assign-${Date.now()}`,
      type: 'ASSIGNED',
      content: body.assigneeUserId ? `Assigned to ${body.assigneeUserId}` : `Unassigned (previously assigned to ${previous})`,
      createdByUserId: TEST_USER.id,
      createdAt: new Date().toISOString(),
    })
    historyByLead.set(id, history)
    return json(route, 200, lead)
  })

  await page.route('**/api/v1/leads/*', async (route) => {
    const request = route.request()
    const pathname = new URL(request.url()).pathname
    const id = pathname.split('/').pop()
    const lead = leads.find((l) => l.id === id)
    if (!lead) return json(route, 404, apiError(404, 'LEAD_NOT_FOUND', 'Lead not found', pathname))

    if (request.method() === 'GET') return json(route, 200, lead)
    if (request.method() === 'PATCH') {
      if (lead.archivedAt) {
        return json(route, 409, apiError(409, 'LEAD_ARCHIVED', 'This lead is archived and cannot be modified', pathname))
      }
      const body = (request.postDataJSON() ?? {}) as Record<string, unknown>
      for (const key of ['name', 'company', 'email', 'phone', 'source', 'priority', 'status'] as const) {
        if (body[key] !== undefined) (lead as Record<string, unknown>)[key] = body[key]
      }
      if (body.clearFollowUpDate) lead.followUpDate = null
      else if (typeof body.followUpDate === 'string') lead.followUpDate = body.followUpDate
      lead.updatedAt = new Date().toISOString()
      return json(route, 200, lead)
    }
    if (request.method() === 'DELETE') {
      lead.archivedAt = new Date().toISOString()
      return route.fulfill({ status: 204 })
    }
    return route.fallback()
  })

  return { leads }
}

export async function mockLeadScoreSuccess(page: Page, leadId: string, overrides: Record<string, unknown> = {}) {
  await page.route(`**/api/v1/leads/${leadId}/score`, (route) =>
    json(route, 200, {
      leadId,
      score: 82,
      priority: 'HIGH',
      reasoning: 'The lead has requested a demo twice and matches our ideal customer profile.',
      recommendedAction: 'Schedule a call within 48 hours.',
      generatedAt: new Date().toISOString(),
      ...overrides,
    }),
  )
}

export async function mockLeadScoreDisabled(page: Page, leadId: string) {
  await page.route(`**/api/v1/leads/${leadId}/score`, (route) =>
    json(route, 503, apiError(503, 'AI_DISABLED', 'AI is disabled for this organization', `/api/v1/leads/${leadId}/score`)),
  )
}

export async function mockLeadScoreFailure(page: Page, leadId: string) {
  await page.route(`**/api/v1/leads/${leadId}/score`, (route) =>
    json(route, 502, apiError(502, 'AI_PROVIDER_ERROR', 'The AI provider returned an error', `/api/v1/leads/${leadId}/score`)),
  )
}

// ---------------------------------------------------------------------------
// Phase 22 — Products / Quotations fixtures
// ---------------------------------------------------------------------------

export const TEST_CATEGORY = {
  id: 'e2e-category-1',
  name: 'Hardware',
  organizationId: TEST_USER.organizationId,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

export const TEST_PRODUCT = {
  id: 'e2e-product-1',
  sku: 'WID-001',
  name: 'Steel Widget',
  description: 'A durable steel widget.' as string | null,
  unit: 'pcs',
  price: 250,
  taxPercentage: 18,
  status: 'ACTIVE' as string,
  categoryId: TEST_CATEGORY.id as string | null,
  organizationId: TEST_USER.organizationId,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

/**
 * A small, real, in-memory CRUD simulation behind `page.route` — same
 * rationale as {@link mockCustomersResource}. Route registration order
 * matters: the bare list/detail routes are registered first, then the
 * `/categories` sub-resource routes are registered afterward so Playwright's
 * last-registered-wins matching gives them priority over the generic
 * `/products/*` detail pattern for that specific subpath.
 */
export async function mockProductsResource(
  page: Page,
  initialProducts: (typeof TEST_PRODUCT)[] = [TEST_PRODUCT],
  initialCategories: (typeof TEST_CATEGORY)[] = [TEST_CATEGORY],
) {
  const products = initialProducts.map((p) => ({ ...p }))
  const categories = initialCategories.map((c) => ({ ...c }))
  let nextProductId = products.length + 1
  let nextCategoryId = categories.length + 1

  await page.route('**/api/v1/products*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'GET') {
      const status = url.searchParams.get('status')
      const categoryId = url.searchParams.get('categoryId')
      const q = url.searchParams.get('q')?.toLowerCase()
      let filtered = status ? products.filter((p) => p.status === status) : products
      if (categoryId) filtered = filtered.filter((p) => p.categoryId === categoryId)
      if (q) {
        filtered = filtered.filter((p) => p.name.toLowerCase().includes(q) || p.sku.toLowerCase().includes(q))
      }
      const page = Number(url.searchParams.get('page') ?? '0')
      const size = Number(url.searchParams.get('size') ?? '20')
      return json(route, 200, pageOf(filtered, page, size))
    }
    if (request.method() === 'POST') {
      const body = (request.postDataJSON() ?? {}) as Record<string, string | undefined>
      if (!body.sku || !body.name || !body.unit || !body.price) {
        return json(route, 400, apiError(400, 'VALIDATION_ERROR', 'Missing required fields', url.pathname))
      }
      if (products.some((p) => p.sku === body.sku)) {
        return json(route, 409, apiError(409, 'DUPLICATE_SKU', 'A product with this SKU already exists.', url.pathname))
      }
      const now = new Date().toISOString()
      const created = {
        id: `e2e-product-${nextProductId++}`,
        sku: body.sku,
        name: body.name,
        description: body.description || null,
        unit: body.unit,
        price: Number(body.price),
        taxPercentage: Number(body.taxPercentage || '0'),
        status: 'ACTIVE',
        categoryId: body.categoryId || null,
        organizationId: TEST_USER.organizationId,
        createdAt: now,
        updatedAt: now,
      }
      products.push(created)
      return json(route, 201, created)
    }
    return route.fallback()
  })

  await page.route('**/api/v1/products/*', async (route) => {
    const request = route.request()
    const pathname = new URL(request.url()).pathname
    const id = pathname.split('/').pop()
    const product = products.find((p) => p.id === id)
    if (!product) return json(route, 404, apiError(404, 'PRODUCT_NOT_FOUND', 'Product not found', pathname))

    if (request.method() === 'GET') return json(route, 200, product)
    if (request.method() === 'PATCH') {
      const body = (request.postDataJSON() ?? {}) as Record<string, unknown>
      if (typeof body.sku === 'string' && products.some((p) => p.sku === body.sku && p.id !== product.id)) {
        return json(route, 409, apiError(409, 'DUPLICATE_SKU', 'A product with this SKU already exists.', pathname))
      }
      for (const key of ['sku', 'name', 'description', 'unit', 'status'] as const) {
        if (body[key] !== undefined) (product as Record<string, unknown>)[key] = body[key]
      }
      if (body.price !== undefined) product.price = Number(body.price)
      if (body.taxPercentage !== undefined) product.taxPercentage = Number(body.taxPercentage)
      if (body.clearCategory) product.categoryId = null
      else if (typeof body.categoryId === 'string') product.categoryId = body.categoryId
      product.updatedAt = new Date().toISOString()
      return json(route, 200, product)
    }
    if (request.method() === 'DELETE') {
      product.status = 'INACTIVE'
      return route.fulfill({ status: 204 })
    }
    return route.fallback()
  })

  await page.route('**/api/v1/products/categories*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'GET') return json(route, 200, pageOf(categories, 0, 100))
    if (request.method() === 'POST') {
      const body = (request.postDataJSON() ?? {}) as { name?: string }
      if (!body.name || body.name.trim() === '') {
        return json(route, 400, apiError(400, 'VALIDATION_ERROR', 'name cannot be blank', url.pathname))
      }
      const now = new Date().toISOString()
      const created = {
        id: `e2e-category-${nextCategoryId++}`,
        name: body.name,
        organizationId: TEST_USER.organizationId,
        createdAt: now,
        updatedAt: now,
      }
      categories.push(created)
      return json(route, 201, created)
    }
    return route.fallback()
  })

  await page.route('**/api/v1/products/categories/*', async (route) => {
    const request = route.request()
    const pathname = new URL(request.url()).pathname
    const id = pathname.split('/').pop()
    const category = categories.find((c) => c.id === id)
    if (!category) return json(route, 404, apiError(404, 'CATEGORY_NOT_FOUND', 'Category not found', pathname))
    if (request.method() === 'PATCH') {
      const body = (request.postDataJSON() ?? {}) as { name?: string }
      if (body.name) category.name = body.name
      category.updatedAt = new Date().toISOString()
      return json(route, 200, category)
    }
    return route.fallback()
  })

  return { products, categories }
}

/** Mirrors QuotationCalculator.java's rounding: line subtotal → per-line discount share → tax on the discounted amount. */
function round4(value: number): number {
  return Math.round(value * 10000) / 10000
}

function computeQuotationTotals(
  items: { productId: string; quantity: number; unitPrice: number; taxPercentage: number; productNameSnapshot: string }[],
  discountPercentage: number,
) {
  let subtotal = 0
  let taxAmount = 0
  const computedItems = items.map((item) => {
    const lineSubtotal = round4(item.quantity * item.unitPrice)
    const lineDiscount = round4((lineSubtotal * discountPercentage) / 100)
    const lineTaxableAmount = lineSubtotal - lineDiscount
    const lineTaxAmount = round4((lineTaxableAmount * item.taxPercentage) / 100)
    subtotal += lineSubtotal
    taxAmount += lineTaxAmount
    return {
      id: `item-${item.productId}-${Math.random().toString(36).slice(2, 8)}`,
      productId: item.productId,
      productNameSnapshot: item.productNameSnapshot,
      quantity: item.quantity,
      unitPrice: item.unitPrice,
      taxPercentage: item.taxPercentage,
      lineSubtotal,
      lineTaxAmount,
    }
  })
  subtotal = round4(subtotal)
  taxAmount = round4(taxAmount)
  const discountAmount = round4((subtotal * discountPercentage) / 100)
  const grandTotal = round4(subtotal - discountAmount + taxAmount)
  return { items: computedItems, subtotal, discountAmount, taxAmount, grandTotal }
}

export const TEST_QUOTATION = {
  id: 'e2e-quotation-1',
  customerId: TEST_CUSTOMER.id,
  status: 'DRAFT' as string,
  validUntil: null as string | null,
  discountPercentage: 0,
}

/**
 * Simulates the Quotation CRUD + status-lifecycle contract. `products` is the
 * same in-memory array returned by {@link mockProductsResource} — new
 * quotation items snapshot each product's current name/price/tax at
 * creation, exactly like the real backend does.
 */
export async function mockQuotationsResource(
  page: Page,
  products: (typeof TEST_PRODUCT)[],
  initialQuotations: (typeof TEST_QUOTATION & { items?: { productId: string; quantity: number }[] })[] = [],
) {
  const quotations = initialQuotations.map((q) => {
    const items = (q.items ?? []).map((i) => {
      const product = products.find((p) => p.id === i.productId)!
      return { productId: i.productId, quantity: i.quantity, unitPrice: product.price, taxPercentage: product.taxPercentage, productNameSnapshot: product.name }
    })
    const totals = computeQuotationTotals(items, q.discountPercentage)
    const now = new Date().toISOString()
    return {
      id: q.id,
      customerId: q.customerId,
      status: q.status,
      validUntil: q.validUntil,
      discountPercentage: q.discountPercentage,
      ...totals,
      organizationId: TEST_USER.organizationId,
      createdAt: now,
      updatedAt: now,
    }
  })
  let nextId = quotations.length + 1

  function buildItems(rawItems: { productId: string; quantity: string }[]) {
    return rawItems.map((i) => {
      const product = products.find((p) => p.id === i.productId)
      if (!product) throw new Error(`Unknown product id in mock: ${i.productId}`)
      return {
        productId: i.productId,
        quantity: Number(i.quantity),
        unitPrice: product.price,
        taxPercentage: product.taxPercentage,
        productNameSnapshot: product.name,
      }
    })
  }

  await page.route('**/api/v1/quotations*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'GET') {
      const status = url.searchParams.get('status')
      const customerId = url.searchParams.get('customerId')
      const validUntilBefore = url.searchParams.get('validUntilBefore')
      let filtered = status ? quotations.filter((q) => q.status === status) : quotations
      if (customerId) filtered = filtered.filter((q) => q.customerId === customerId)
      if (validUntilBefore) filtered = filtered.filter((q) => q.validUntil && q.validUntil <= validUntilBefore)
      const page = Number(url.searchParams.get('page') ?? '0')
      const size = Number(url.searchParams.get('size') ?? '20')
      // The list endpoint returns QuotationSummaryResponse (no `items`).
      const summaries = filtered.map(({ items: _items, ...summary }) => summary)
      return json(route, 200, pageOf(summaries, page, size))
    }
    if (request.method() === 'POST') {
      const body = (request.postDataJSON() ?? {}) as {
        customerId: string
        validUntil?: string
        discountPercentage?: string
        items: { productId: string; quantity: string }[]
      }
      if (!body.customerId || !body.items || body.items.length === 0) {
        return json(route, 400, apiError(400, 'VALIDATION_ERROR', 'customerId and items are required', url.pathname))
      }
      const discountPercentage = Number(body.discountPercentage || '0')
      const totals = computeQuotationTotals(buildItems(body.items), discountPercentage)
      const now = new Date().toISOString()
      const created = {
        id: `e2e-quotation-${nextId++}`,
        customerId: body.customerId,
        status: 'DRAFT',
        validUntil: body.validUntil || null,
        discountPercentage,
        ...totals,
        organizationId: TEST_USER.organizationId,
        createdAt: now,
        updatedAt: now,
      }
      quotations.push(created)
      return json(route, 201, created)
    }
    return route.fallback()
  })

  await page.route('**/api/v1/quotations/*/pdf', async (route) => {
    return route.fulfill({ status: 200, contentType: 'application/pdf', body: Buffer.from('%PDF-1.4 mock quotation pdf') })
  })

  await page.route('**/api/v1/quotations/*', async (route) => {
    const request = route.request()
    const pathname = new URL(request.url()).pathname
    const id = pathname.split('/').pop()
    const quotation = quotations.find((q) => q.id === id)
    if (!quotation) return json(route, 404, apiError(404, 'QUOTATION_NOT_FOUND', 'Quotation not found', pathname))

    if (request.method() === 'GET') return json(route, 200, quotation)
    if (request.method() === 'PATCH') {
      if (quotation.status !== 'DRAFT') {
        return json(route, 409, apiError(409, 'QUOTATION_NOT_EDITABLE', 'Only draft quotations can be edited.', pathname))
      }
      const body = (request.postDataJSON() ?? {}) as {
        customerId?: string
        validUntil?: string
        clearValidUntil?: boolean
        discountPercentage?: string
        status?: string
        items?: { productId: string; quantity: string }[]
      }
      if (body.customerId) quotation.customerId = body.customerId
      if (body.clearValidUntil) quotation.validUntil = null
      else if (body.validUntil) quotation.validUntil = body.validUntil
      const discountPercentage = body.discountPercentage !== undefined ? Number(body.discountPercentage) : quotation.discountPercentage
      const items = body.items ? buildItems(body.items) : quotation.items
      const totals = computeQuotationTotals(items, discountPercentage)
      quotation.discountPercentage = discountPercentage
      Object.assign(quotation, totals)
      if (body.status) quotation.status = body.status
      quotation.updatedAt = new Date().toISOString()
      return json(route, 200, quotation)
    }
    if (request.method() === 'DELETE') {
      quotation.status = 'CANCELLED'
      return route.fulfill({ status: 204 })
    }
    return route.fallback()
  })

  return { quotations }
}

// ---------------------------------------------------------------------------
// Phase 23 — Tasks fixtures
// ---------------------------------------------------------------------------

export const TEST_TASK = {
  id: 'e2e-task-1',
  title: 'Follow up with customer',
  description: 'Call about the renewal.' as string | null,
  status: 'TODO' as string,
  priority: 'MEDIUM' as string,
  assignedToUserId: null as string | null,
  customerId: null as string | null,
  leadId: null as string | null,
  dueDate: null as string | null,
  notes: null as string | null,
  organizationId: TEST_USER.organizationId,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

/**
 * A small, real, in-memory CRUD simulation behind `page.route` — same
 * rationale as {@link mockLeadsResource}. `customerId`/`leadId` on a seed
 * task are trusted verbatim (no cross-check against a customers/leads
 * fixture) since this mock only needs to prove the Task UI round-trips
 * whatever the backend would return, not re-validate referential integrity
 * the real backend already owns.
 */
export async function mockTasksResource(page: Page, initial: (typeof TEST_TASK)[] = [TEST_TASK]) {
  const tasks = initial.map((t) => ({ ...t }))
  let nextId = tasks.length + 1

  await page.route('**/api/v1/tasks*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'GET') {
      const status = url.searchParams.get('status')
      const priority = url.searchParams.get('priority')
      const assignedToUserId = url.searchParams.get('assignedToUserId')
      const unassigned = url.searchParams.get('unassigned') === 'true'
      const dueDateOnOrBefore = url.searchParams.get('dueDateOnOrBefore')
      const q = url.searchParams.get('q')?.toLowerCase()

      let filtered = [...tasks]
      if (status) filtered = filtered.filter((t) => t.status === status)
      if (priority) filtered = filtered.filter((t) => t.priority === priority)
      if (assignedToUserId) filtered = filtered.filter((t) => t.assignedToUserId === assignedToUserId)
      if (unassigned) filtered = filtered.filter((t) => t.assignedToUserId === null)
      if (dueDateOnOrBefore) filtered = filtered.filter((t) => t.dueDate && t.dueDate <= dueDateOnOrBefore)
      if (q) filtered = filtered.filter((t) => t.title.toLowerCase().includes(q))

      const page = Number(url.searchParams.get('page') ?? '0')
      const size = Number(url.searchParams.get('size') ?? '20')
      return json(route, 200, pageOf(filtered, page, size))
    }
    if (request.method() === 'POST') {
      const body = (request.postDataJSON() ?? {}) as Record<string, string | undefined>
      if (!body.title || body.title.trim() === '') {
        return json(route, 400, apiError(400, 'INVALID_TASK_DATA', 'title cannot be blank', url.pathname))
      }
      const now = new Date().toISOString()
      const created = {
        id: `e2e-task-${nextId++}`,
        title: body.title,
        description: body.description || null,
        status: 'TODO',
        priority: body.priority || 'MEDIUM',
        assignedToUserId: null,
        customerId: body.customerId || null,
        leadId: body.leadId || null,
        dueDate: body.dueDate || null,
        notes: null,
        organizationId: TEST_USER.organizationId,
        createdAt: now,
        updatedAt: now,
      }
      tasks.push(created)
      return json(route, 201, created)
    }
    return route.fallback()
  })

  await page.route('**/api/v1/tasks/*/assign', async (route) => {
    const id = new URL(route.request().url()).pathname.split('/')[4] ?? ''
    const task = tasks.find((t) => t.id === id)
    if (!task) return json(route, 404, apiError(404, 'TASK_NOT_FOUND', 'Task not found', route.request().url()))
    const body = (route.request().postDataJSON() ?? {}) as { assigneeUserId: string | null }
    task.assignedToUserId = body.assigneeUserId
    task.updatedAt = new Date().toISOString()
    return json(route, 200, task)
  })

  await page.route('**/api/v1/tasks/*', async (route) => {
    const request = route.request()
    const pathname = new URL(request.url()).pathname
    const id = pathname.split('/').pop()
    const task = tasks.find((t) => t.id === id)
    if (!task) return json(route, 404, apiError(404, 'TASK_NOT_FOUND', 'Task not found', pathname))

    if (request.method() === 'GET') return json(route, 200, task)
    if (request.method() === 'PATCH') {
      const body = (request.postDataJSON() ?? {}) as Record<string, unknown>
      for (const key of ['title', 'description', 'priority', 'status', 'notes'] as const) {
        if (body[key] !== undefined) (task as Record<string, unknown>)[key] = body[key]
      }
      if (body.clearDueDate) task.dueDate = null
      else if (typeof body.dueDate === 'string') task.dueDate = body.dueDate
      if (body.clearCustomerId) task.customerId = null
      else if (typeof body.customerId === 'string') task.customerId = body.customerId
      if (body.clearLeadId) task.leadId = null
      else if (typeof body.leadId === 'string') task.leadId = body.leadId
      task.updatedAt = new Date().toISOString()
      return json(route, 200, task)
    }
    if (request.method() === 'DELETE') {
      task.status = 'CANCELLED'
      return route.fulfill({ status: 204 })
    }
    return route.fallback()
  })

  return { tasks }
}

// ---------------------------------------------------------------------------
// Phase 24 — Documents fixtures
// ---------------------------------------------------------------------------

const ALLOWED_DOCUMENT_CONTENT_TYPES = [
  'application/pdf',
  'text/plain',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
]

export const TEST_DOCUMENT = {
  id: 'e2e-document-1',
  originalFilename: 'sample.pdf',
  contentType: 'application/pdf',
  fileSize: 267,
  status: 'COMPLETED' as string,
  uploadedByUserId: TEST_USER.id as string | null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

/**
 * Same real, in-memory CRUD simulation as {@link mockTasksResource}. Upload
 * is multipart, not JSON, so the POST handler reads the raw request body
 * and regex-extracts the filename/Content-Type from the multipart headers
 * (Playwright's `postDataJSON()` only works for JSON bodies) — mirroring
 * just enough of what `DocumentValidator` checks (content-type must be one
 * of the three supported types) to exercise the real error path, without
 * reimplementing the backend's magic-byte signature check.
 */
export async function mockDocumentsResource(page: Page, initial: (typeof TEST_DOCUMENT)[] = [TEST_DOCUMENT]) {
  const documents = initial.map((d) => ({ ...d }))
  let nextId = documents.length + 1

  await page.route('**/api/v1/documents*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'GET') {
      const status = url.searchParams.get('status')
      const contentType = url.searchParams.get('contentType')
      const q = url.searchParams.get('q')?.toLowerCase()

      let filtered = [...documents]
      if (status) filtered = filtered.filter((d) => d.status === status)
      if (contentType) filtered = filtered.filter((d) => d.contentType === contentType)
      if (q) filtered = filtered.filter((d) => d.originalFilename.toLowerCase().includes(q))

      const page = Number(url.searchParams.get('page') ?? '0')
      const size = Number(url.searchParams.get('size') ?? '20')
      return json(route, 200, pageOf(filtered, page, size))
    }
    if (request.method() === 'POST') {
      const buffer = request.postDataBuffer()
      const raw = buffer ? buffer.toString('latin1') : ''
      const filename = raw.match(/filename="([^"]*)"/)?.[1] ?? 'unnamed'
      const contentType = (raw.match(/Content-Type:\s*([^\r\n]+)/i)?.[1] ?? '').trim()

      if (!ALLOWED_DOCUMENT_CONTENT_TYPES.includes(contentType)) {
        return json(
          route,
          400,
          apiError(400, 'INVALID_DOCUMENT', `Unsupported content type: ${contentType || 'unknown'}`, url.pathname),
        )
      }
      const now = new Date().toISOString()
      const created = {
        id: `e2e-document-${nextId++}`,
        originalFilename: filename,
        contentType,
        fileSize: buffer ? buffer.length : 0,
        status: 'UPLOADED',
        uploadedByUserId: TEST_USER.id,
        createdAt: now,
        updatedAt: now,
      }
      documents.push(created)
      return json(route, 201, created)
    }
    return route.fallback()
  })

  await page.route('**/api/v1/documents/*/download', async (route) => {
    const id = new URL(route.request().url()).pathname.split('/')[4] ?? ''
    const doc = documents.find((d) => d.id === id)
    if (!doc) return json(route, 404, apiError(404, 'DOCUMENT_NOT_FOUND', 'Document not found', route.request().url()))
    return route.fulfill({
      status: 200,
      contentType: doc.contentType,
      headers: { 'Content-Disposition': `attachment; filename="${doc.originalFilename}"` },
      body: `mock file contents for ${doc.originalFilename}`,
    })
  })

  await page.route('**/api/v1/documents/*', async (route) => {
    const request = route.request()
    const pathname = new URL(request.url()).pathname
    const id = pathname.split('/').pop()
    const doc = documents.find((d) => d.id === id)
    if (!doc) return json(route, 404, apiError(404, 'DOCUMENT_NOT_FOUND', 'Document not found', pathname))

    if (request.method() === 'GET') return json(route, 200, doc)
    if (request.method() === 'DELETE') {
      const index = documents.findIndex((d) => d.id === id)
      if (index >= 0) documents.splice(index, 1)
      return route.fulfill({ status: 204 })
    }
    return route.fallback()
  })

  return { documents }
}

// ---------------------------------------------------------------------------
// Phase 25 — AI Assistant fixtures
// ---------------------------------------------------------------------------

export async function mockAiChatSuccess(page: Page, response: { answer: string; sources?: unknown[] }) {
  await page.route('**/api/v1/ai/chat', (route) =>
    json(route, 200, { answer: response.answer, sources: response.sources ?? [] }),
  )
}

export async function mockAiChatDisabled(page: Page) {
  await page.route('**/api/v1/ai/chat', (route) =>
    json(route, 503, apiError(503, 'AI_DISABLED', 'The AI assistant is currently unavailable', '/api/v1/ai/chat')),
  )
}

export async function mockAiChatProviderError(page: Page) {
  await page.route('**/api/v1/ai/chat', (route) =>
    json(route, 502, apiError(502, 'AI_PROVIDER_ERROR', 'The AI assistant is temporarily unavailable', '/api/v1/ai/chat')),
  )
}
