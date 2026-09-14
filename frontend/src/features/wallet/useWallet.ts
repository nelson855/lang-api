import { useQuery, type QueryClient } from '@tanstack/react-query';
import { PortalApiError } from '../../api/envelope';
import { WALLET_QUERY_KEY, fetchTopupOptions, fetchTopupPage } from '../../api/wallet';
import { clearUsageScope } from '../usage/usageCache';

export function handleWalletQueryError(client: QueryClient, error: unknown) {
  // 任一钱包请求 401 复用统一会话失效；局部失败保留最后一次可信展示。
  if (error instanceof PortalApiError && error.code === 'UNAUTHENTICATED') {
    clearUsageScope(client);
  }
}

export function topupRecordsKey(userId: number, page: number, pageSize: number) {
  return [...WALLET_QUERY_KEY, userId, 'topups', page, pageSize] as const;
}

export function useTopupOptionsQuery(userId: number) {
  return useQuery({
    queryKey: [...WALLET_QUERY_KEY, userId, 'options'],
    queryFn: ({ signal }) => fetchTopupOptions(signal),
    retry: false,
  });
}

export function useTopupRecordsQuery(userId: number, page: number, pageSize: number) {
  return useQuery({
    queryKey: topupRecordsKey(userId, page, pageSize),
    queryFn: ({ signal }) => fetchTopupPage(page, pageSize, signal),
    retry: false,
  });
}
