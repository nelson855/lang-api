import { useQuery, type QueryClient } from '@tanstack/react-query';
import { PortalApiError } from '../../api/envelope';
import { fetchDashboardStats } from '../../api/dashboard';
import { clearAuthenticatedScope } from '../auth/authCache';
import { dashboardQueryKey, dashboardRequestsEnabled, type DashboardQueryParams } from './dashboardCache';
import { toStatsParams, type DashboardRangeValue } from './dashboardRange';

export function handleDashboardQueryError(client: QueryClient, error: unknown) {
  if (error instanceof PortalApiError && error.code === 'UNAUTHENTICATED') {
    clearAuthenticatedScope(client);
  }
}

export function useDashboardStatsQuery(userId: number, range: DashboardRangeValue) {
  const params: DashboardQueryParams = toStatsParams(range);
  return useQuery({
    queryKey: dashboardQueryKey(userId, params),
    queryFn: ({ signal }) => fetchDashboardStats(params, signal),
    retry: false,
    enabled: dashboardRequestsEnabled(range),
  });
}