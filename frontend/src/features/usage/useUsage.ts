import { useQuery, type QueryClient } from '@tanstack/react-query';
import { PortalApiError } from '../../api/envelope';
import {
  USAGE_QUERY_KEY,
  fetchBalance,
  fetchSummary,
  fetchTimeseries,
  type UsageRange,
} from '../../api/usage';
import { clearUsageScope, normalizeUsageRange, usageRangeKey } from './usageCache';

export function handleUsageQueryError(client: QueryClient, error: unknown) {
  // 会话失效时清掉认证与用量范围；局部失败保留最后一次可信展示。
  if (error instanceof PortalApiError && error.code === 'UNAUTHENTICATED') {
    clearUsageScope(client);
  }
}

export function useBalanceQuery(userId: number) {
  return useQuery({
    queryKey: [...USAGE_QUERY_KEY, userId, 'balance'],
    queryFn: ({ signal }) => fetchBalance(signal),
    retry: false,
  });
}

export function useSummaryQuery(userId: number, range: UsageRange) {
  const normalized = normalizeUsageRange(range.startTime, range.endTime);
  return useQuery({
    queryKey: [...usageRangeKey(userId, normalized), 'summary'],
    queryFn: ({ signal }) => fetchSummary(normalized, signal),
    retry: false,
  });
}

export function useTimeseriesQuery(userId: number, range: UsageRange) {
  const normalized = normalizeUsageRange(range.startTime, range.endTime);
  return useQuery({
    queryKey: [...usageRangeKey(userId, normalized), 'timeseries'],
    queryFn: ({ signal }) => fetchTimeseries(normalized, signal),
    retry: false,
  });
}
