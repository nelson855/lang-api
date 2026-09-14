import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { AUTH_PROFILE_QUERY_KEY } from '../../api/auth';
import { PortalApiError } from '../../api/envelope';
import { USAGE_QUERY_KEY } from '../../api/usage';
import { handleUsageQueryError, useBalanceQuery, useSummaryQuery } from './useUsage';

vi.mock('../../api/usage', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/usage')>();
  return {
    ...actual,
    fetchBalance: vi.fn(),
    fetchSummary: vi.fn(),
    fetchTimeseries: vi.fn(),
  };
});

const { fetchBalance, fetchSummary } = await import('../../api/usage');
const balanceMock = vi.mocked(fetchBalance);
const summaryMock = vi.mocked(fetchSummary);

function wrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

function failure(code: string) {
  return new PortalApiError(502, code, code, 'req-1');
}

describe('用量查询钩子', () => {
  it('三个查询只请求一次且透出错误', async () => {
    balanceMock.mockRejectedValueOnce(failure('UPSTREAM_ERROR'));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    const { result } = renderHook(() => useBalanceQuery(42), { wrapper: wrapper(client) });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(balanceMock).toHaveBeenCalledTimes(1);
  });

  it('摘要与趋势共享同一范围键', async () => {
    summaryMock.mockResolvedValueOnce({
      data: { quota: '0', amount: '0.0', currency: 'USD', rpm: 0, tpm: 0, rateWindowSeconds: 60 },
      requestId: 'req-1',
    });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const range = { startTime: '2026-09-09T12:00:00.000Z', endTime: '2026-09-10T12:00:00.000Z' };

    const { result } = renderHook(() => useSummaryQuery(42, range), { wrapper: wrapper(client) });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(summaryMock).toHaveBeenCalledTimes(1);
  });

  it('会话失效清理认证与用量范围，局部失败保留数据', () => {
    const client = new QueryClient();
    client.setQueryData(AUTH_PROFILE_QUERY_KEY, { id: 42 });
    client.setQueryData([...USAGE_QUERY_KEY, 42, 'a', 'b'], { quota: '1' });

    handleUsageQueryError(client, failure('UNAUTHENTICATED'));
    expect(client.getQueryData(AUTH_PROFILE_QUERY_KEY)).toBeUndefined();
    expect(client.getQueryData([...USAGE_QUERY_KEY, 42, 'a', 'b'])).toBeUndefined();

    client.setQueryData([...USAGE_QUERY_KEY, 42, 'a', 'b'], { quota: '1' });
    handleUsageQueryError(client, failure('UPSTREAM_ERROR'));
    expect(client.getQueryData([...USAGE_QUERY_KEY, 42, 'a', 'b'])).toEqual({ quota: '1' });
  });
});
