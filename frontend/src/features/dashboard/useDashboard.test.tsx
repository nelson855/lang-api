import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { PortalApiError } from '../../api/envelope';
import { useDashboardStatsQuery, handleDashboardQueryError } from './useDashboard';
import { buildDashboardRange } from './dashboardRange';
import { DASHBOARD_QUERY_KEY, type DashboardStats } from '../../api/dashboard';

vi.mock('../../api/dashboard', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/dashboard')>();
  return {
    ...actual,
    fetchDashboardStats: vi.fn(),
  };
});

const { fetchDashboardStats } = await import('../../api/dashboard');
const statsMock = vi.mocked(fetchDashboardStats);

function wrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

function success(): { data: DashboardStats; requestId: string } {
  return {
    data: {
      baselineVersion: 'p2-2026-09-22-a',
      range: { startTime: '2026-09-01T00:00:00.000Z', endTime: '2026-09-01T01:00:00.000Z', timezone: 'UTC', granularity: 'HOUR' },
      metrics: {
        requestTotal: { value: null, unit: 'requests', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' },
        tokenUsage: { value: 90, unit: 'tokens', availability: 'AVAILABLE', reasonCode: null },
        spend: { value: null, currency: null, availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' },
        activeKeys: { value: 2, unit: 'keys', availability: 'AVAILABLE', reasonCode: null },
        successRate: { value: null, unit: 'ratio', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' },
        averageLatency: { value: null, unit: 'ms', availability: 'UNAVAILABLE', reasonCode: 'NO_DATA' },
      },
      requestTrend: { availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED', unit: 'requests', points: [] },
      spendTrend: { availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED', currency: null, points: [] },
      recentRequests: { availability: 'PARTIAL', reasonCode: 'PARTIAL_SOURCE_COVERAGE', items: [] },
    },
    requestId: 'req-1',
  };
}

describe('useDashboardStatsQuery', () => {
  beforeEach(() => {
    statsMock.mockReset();
  });

  it('可请求范围调用一次并透出数据', async () => {
    statsMock.mockResolvedValueOnce(success());
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const range = buildDashboardRange('24h', new Date('2026-09-01T12:00:00Z'), 'UTC');

    const { result } = renderHook(() => useDashboardStatsQuery(42, range), { wrapper: wrapper(client) });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(statsMock).toHaveBeenCalledTimes(1);
  });

  it('30D disabled 状态不触发 HTTP 请求', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const range = buildDashboardRange('30d', new Date('2026-09-01T12:00:00Z'), 'UTC');

    renderHook(() => useDashboardStatsQuery(42, range), { wrapper: wrapper(client) });

    await new Promise((r) => setTimeout(r, 10));
    expect(statsMock).not.toHaveBeenCalled();
  });

  it('禁自动重试：失败只请求一次', async () => {
    statsMock.mockRejectedValueOnce(new PortalApiError(502, 'UPSTREAM_ERROR', 'up', 'req-1'));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const range = buildDashboardRange('7d', new Date('2026-09-01T12:00:00Z'), 'UTC');

    const { result } = renderHook(() => useDashboardStatsQuery(42, range), { wrapper: wrapper(client) });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(statsMock).toHaveBeenCalledTimes(1);
  });

  it('透传 AbortSignal 给 fetch', async () => {
    statsMock.mockResolvedValueOnce(success());
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const range = buildDashboardRange('24h', new Date('2026-09-01T12:00:00Z'), 'Asia/Shanghai');

    const { result } = renderHook(() => useDashboardStatsQuery(42, range), { wrapper: wrapper(client) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(statsMock).toHaveBeenCalledTimes(1);
    const secondArg = statsMock.mock.calls[0][1];
    expect(secondArg).toBeInstanceOf(AbortSignal);
  });
});

describe('handleDashboardQueryError', () => {
  it('UNAUTHENTICATED 清空 dashboard 缓存快照', () => {
    const client = new QueryClient();
    client.setQueryData([...DASHBOARD_QUERY_KEY, 42, 's'], { data: 'stale-dash' });
    client.setQueryData([...DASHBOARD_QUERY_KEY, 7, 's'], { data: 'other-user-dash' });

    handleDashboardQueryError(client, new PortalApiError(401, 'UNAUTHENTICATED', 'expired', 'req-1'));

    expect(client.getQueryData([...DASHBOARD_QUERY_KEY, 42, 's'])).toBeUndefined();
    expect(client.getQueryData([...DASHBOARD_QUERY_KEY, 7, 's'])).toBeUndefined();
  });

  it('非认证错误不清理 dashboard', () => {
    const client = new QueryClient();
    client.setQueryData([...DASHBOARD_QUERY_KEY, 42, 's'], { data: 'dash' });

    handleDashboardQueryError(client, new PortalApiError(502, 'UPSTREAM_ERROR', 'up', 'req-1'));

    expect(client.getQueryData([...DASHBOARD_QUERY_KEY, 42, 's'])).toEqual({ data: 'dash' });
  });
});

describe('范围快速切换', () => {
  it('切换范围后采用新范围数据，旧范围迟到响应不覆盖', async () => {
    const rangeA = buildDashboardRange('24h', new Date('2026-09-01T12:00:00Z'), 'UTC');
    const rangeB = buildDashboardRange('24h', new Date('2026-09-01T13:00:00Z'), 'UTC'); // 不同 now → 不同 key

    let resolveA!: (value: unknown) => void;
    const pendingA: Promise<unknown> = new Promise((r) => (resolveA = r));
    const b = success();
    b.data.metrics.tokenUsage = { value: 999, unit: 'tokens', availability: 'AVAILABLE', reasonCode: null };
    statsMock
      .mockImplementationOnce(() => pendingA as never) // 旧 A 挂起
      .mockImplementation(() => Promise.resolve(b) as never); // B 及后续均返回 B

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result, rerender } = renderHook(
      ({ range }: { range: ReturnType<typeof buildDashboardRange> }) => useDashboardStatsQuery(42, range),
      { initialProps: { range: rangeA }, wrapper: wrapper(client) },
    );

    rerender({ range: rangeB }); // 立即切到 B
    await waitFor(() => expect(result.current.data?.data.metrics.tokenUsage.value).toBe(999));

    // 让旧 A 迟到完成，断言不会覆盖新范围
    resolveA(success());
    await new Promise((r) => setTimeout(r, 0));
    expect(result.current.data?.data.metrics.tokenUsage.value).toBe(999);
    expect(statsMock.mock.calls.length).toBeGreaterThanOrEqual(2);
  });
});