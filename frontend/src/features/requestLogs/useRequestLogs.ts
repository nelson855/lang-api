import { useQuery, type QueryClient } from '@tanstack/react-query';
import { PortalApiError } from '../../api/envelope';
import { listRequestLogs } from '../../api/requestLogs';
import { clearAuthenticatedScope } from '../auth/authCache';
import {
  invalidateRequestLogsScope,
  normalizeRequestLogsParams,
  requestLogsListKey,
  type RequestLogsQueryParams,
} from './requestLogsCache';

export function handleRequestLogsQueryError(client: QueryClient, userId: number, error: unknown) {
  // 会话失效时清掉认证与日志范围；普通失败保留筛选项与最后一次可信展示。
  if (error instanceof PortalApiError && error.code === 'UNAUTHENTICATED') {
    clearAuthenticatedScope(client);
    invalidateRequestLogsScope(client, userId);
  }
}

export function useRequestLogsQuery(userId: number, params: RequestLogsQueryParams) {
  const normalized = normalizeRequestLogsParams(params);
  return useQuery({
    queryKey: requestLogsListKey(userId, normalized),
    queryFn: ({ signal }) =>
      listRequestLogs(
        {
          page: normalized.page,
          pageSize: normalized.pageSize,
          result: normalized.result,
          keyName: normalized.keyName === '' ? undefined : normalized.keyName,
          model: normalized.model === '' ? undefined : normalized.model,
          startTime: normalized.startTime === '' ? undefined : normalized.startTime,
          endTime: normalized.endTime === '' ? undefined : normalized.endTime,
        },
        signal,
      ),
    retry: false,
  });
}
