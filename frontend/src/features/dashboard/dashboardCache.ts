import type { QueryClient } from '@tanstack/react-query';
import { DASHBOARD_QUERY_KEY } from '../../api/dashboard';
import type { DashboardRangeValue } from './dashboardRange';

/**
 * Dashboard 查询键命名空间与能力门控。
 * 键包含用户、起止时间、粒度与时区，保证不同用户或范围不共享快照，
 * 同一范围的键稳定无时钟噪声。
 */

export interface DashboardQueryParams {
  startTime: string;
  endTime: string;
  granularity: string;
  timezone: string;
}

export function dashboardQueryKey(
  userId: number,
  params: DashboardQueryParams,
): readonly unknown[] {
  return [
    ...DASHBOARD_QUERY_KEY,
    userId,
    params.startTime,
    params.endTime,
    params.granularity,
    params.timezone,
  ];
}

/** 移除指定用户的 dashboard 缓存（断言时清空快照，不影响其他用户）。 */
export function clearDashboardScope(client: QueryClient, userId: number) {
  client.removeQueries({ queryKey: [...DASHBOARD_QUERY_KEY, userId] });
}

/** 30D 在当前 P2-03 保护边界下不可请求；不发送已知必然失败的请求。 */
export function dashboardRequestsEnabled(range: DashboardRangeValue): boolean {
  return range.enabled;
}