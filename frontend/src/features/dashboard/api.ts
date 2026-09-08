import { apiFetch } from '@/lib/api-client'
import type { AnalyticsSummaryResponse } from '@/types/api'

export function getAnalyticsSummary(): Promise<AnalyticsSummaryResponse> {
  return apiFetch<AnalyticsSummaryResponse>('/api/v1/analytics/summary')
}
