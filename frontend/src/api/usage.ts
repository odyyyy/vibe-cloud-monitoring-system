import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'

export interface UsageMeter {
  used: number
  limit: number
  /** Percentage of the limit consumed, already rounded by the backend. */
  percentage: number
}

export interface CurrentMonthUsage {
  organizationId: number
  /** Inclusive first day of the period (UTC, ISO date). */
  periodStart: string
  /** Inclusive last day of the period (UTC, ISO date). */
  periodEnd: string
  metrics: UsageMeter
  logs: UsageMeter
  traces: UsageMeter
}

export interface DailyUsagePoint {
  date: string
  metrics: number
  logs: number
  traces: number
}

export const usageQueryKeys = {
  currentMonth: ['usage', 'current-month'] as const,
  daily: (days: number) => ['usage', 'daily', days] as const,
}

export function getCurrentMonthUsage(): Promise<CurrentMonthUsage> {
  return apiRequest<CurrentMonthUsage>('/v1/usage/current-month', {
    token: useAuthStore.getState().token,
  })
}

export function getDailyUsage(days: number): Promise<DailyUsagePoint[]> {
  return apiRequest<DailyUsagePoint[]>(`/v1/usage/daily?days=${days}`, {
    token: useAuthStore.getState().token,
  })
}

export function useCurrentMonthUsageQuery() {
  return useQuery({
    queryKey: usageQueryKeys.currentMonth,
    queryFn: getCurrentMonthUsage,
  })
}

export function useDailyUsageQuery(days: number) {
  return useQuery({
    queryKey: usageQueryKeys.daily(days),
    queryFn: () => getDailyUsage(days),
  })
}
