import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';
import { DASHBOARD_QUERY_KEY } from '../../api/dashboard';
import {
  dashboardQueryKey,
  clearDashboardScope,
  dashboardRequestsEnabled,
  type DashboardQueryParams,
} from './dashboardCache';
import { buildDashboardRange } from './dashboardRange';

const params: DashboardQueryParams = {
  startTime: '2026-09-01T00:00:00.000Z',
  endTime: '2026-09-01T02:00:00.000Z',
  granularity: 'HOUR',
  timezone: 'UTC',
};

describe('dashboard 查询键', () => {
  it('键包含用户、起止时间、粒度与时区，且命名空间独立', () => {
    const key = dashboardQueryKey(42, params);
    expect(key).toEqual([
      ...DASHBOARD_QUERY_KEY,
      42,
      '2026-09-01T00:00:00.000Z',
      '2026-09-01T02:00:00.000Z',
      'HOUR',
      'UTC',
    ]);
  });

  it('不同用户不共享缓存', () => {
    const a = dashboardQueryKey(42, params);
    const b = dashboardQueryKey(7, params);
    expect(a).not.toEqual(b);
  });

  it('不同范围不共享缓存（起止、粒度或时区变化均不同）', () => {
    const base = dashboardQueryKey(42, params);
    const otherStart = dashboardQueryKey(42, { ...params, startTime: '2026-09-01T01:00:00.000Z' });
    const otherGranularity = dashboardQueryKey(42, { ...params, granularity: 'DAY' });
    const otherTz = dashboardQueryKey(42, { ...params, timezone: 'Asia/Shanghai' });
    expect(base).not.toEqual(otherStart);
    expect(base).not.toEqual(otherGranularity);
    expect(base).not.toEqual(otherTz);
  });

  it('同一范围的键稳定（无时钟噪声）', () => {
    expect(dashboardQueryKey(42, params)).toEqual(dashboardQueryKey(42, params));
  });
});

describe('dashboard 缓存清理', () => {
  it('清理仅命中指定用户的 dashboard 缓存，不影响其他用户', () => {
    const client = new QueryClient();
    client.setQueryData(dashboardQueryKey(42, params), { data: 'x' });
    client.setQueryData(dashboardQueryKey(7, params), { data: 'y' });

    clearDashboardScope(client, 42);

    expect(client.getQueryData(dashboardQueryKey(42, params))).toBeUndefined();
    expect(client.getQueryData(dashboardQueryKey(7, params))).toEqual({ data: 'y' });
  });
});

describe('dashboard 请求能力门控', () => {
  it('可请求范围返回 true', () => {
    const range = buildDashboardRange('24h', new Date('2026-09-01T12:00:00Z'), 'UTC');
    expect(dashboardRequestsEnabled(range)).toBe(true);
  });

  it('30D 在当前能力下不可请求', () => {
    const range = buildDashboardRange('30d', new Date('2026-09-01T12:00:00Z'), 'UTC');
    expect(dashboardRequestsEnabled(range)).toBe(false);
  });
});