import { apiFetch } from '@/lib/api-client'
import type {
  AnalyticsSummaryResponse,
  LeadFunnelStageResponse,
  LeadSourceBreakdownResponse,
  RevenueTrendPointResponse,
  SalesPipelineStageResponse,
  TopCustomerResponse,
} from '@/types/api'

export function getAnalyticsSummary(): Promise<AnalyticsSummaryResponse> {
  return apiFetch<AnalyticsSummaryResponse>('/api/v1/analytics/summary')
}

export function getRevenueTrend(): Promise<RevenueTrendPointResponse[]> {
  return apiFetch<RevenueTrendPointResponse[]>('/api/v1/analytics/revenue-trend')
}

export function getLeadFunnel(): Promise<LeadFunnelStageResponse[]> {
  return apiFetch<LeadFunnelStageResponse[]>('/api/v1/analytics/lead-funnel')
}

export function getLeadSources(): Promise<LeadSourceBreakdownResponse[]> {
  return apiFetch<LeadSourceBreakdownResponse[]>('/api/v1/analytics/lead-sources')
}

export function getSalesPipeline(): Promise<SalesPipelineStageResponse[]> {
  return apiFetch<SalesPipelineStageResponse[]>('/api/v1/analytics/sales-pipeline')
}

export function getTopCustomers(): Promise<TopCustomerResponse[]> {
  return apiFetch<TopCustomerResponse[]>('/api/v1/analytics/top-customers')
}
