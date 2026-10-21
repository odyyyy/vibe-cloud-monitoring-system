import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../store/authStore'
import { apiRequest } from './client'
import type { Dashboard } from './dashboards'

const BASE_PATH = '/v1/admin/platform-health'

export const platformHealthQueryKey = ['platform-health'] as const

/**
 * Reads the platform self-monitoring dashboard. The endpoint requires the PLATFORM_ADMIN role;
 * other accounts receive HTTP 403 from the backend.
 */
export function getPlatformHealthDashboard(): Promise<Dashboard> {
  return apiRequest<Dashboard>(BASE_PATH, { token: useAuthStore.getState().token })
}

export function usePlatformHealthQuery() {
  return useQuery({
    queryKey: platformHealthQueryKey,
    queryFn: getPlatformHealthDashboard,
    retry: false,
  })
}
