import { apiFetch } from '@/lib/api-client'
import type { OrganizationResponse } from '@/types/api'

export function getCurrentOrganization(): Promise<OrganizationResponse> {
  return apiFetch<OrganizationResponse>('/api/v1/organizations/current')
}
