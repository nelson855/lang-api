import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { AUTH_PROFILE_QUERY_KEY } from '../../api/auth';
import { PortalApiError } from '../../api/envelope';
import { USAGE_QUERY_KEY } from '../../api/usage';
import { WALLET_QUERY_KEY } from '../../api/wallet';
import {
  handleWalletQueryError,
  topupRecordsKey,
  useTopupOptionsQuery,
  useTopupRecordsQuery,
} from './useWallet';
import { buildWalletRange } from './walletRange';
import { consumptionSummaryKey, transactionsKey } from './walletAnalyticsCache';

vi.mock('../../api/wallet', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/wallet')>();
  return {
    ...actual,
    fetchTopupOptions: vi.fn(),
    fetchTopupPage: vi.fn(),
  };
});

const { fetchTopupOptions, fetchTopupPage } = await import('../../api/wallet');
const optionsMock = vi.mocked(fetchTopupOptions);
const recordsMock = vi.mocked(fetchTopupPage);

function wrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

function failure(code: string) {
  return new PortalApiError(502, code, code, 'req-1');
}

const optionsData: {
  data: { enabled: boolean; methods: []; currency: 'USD'; reason: 'NOT_CONFIGURED' };
  requestId: string;
} = {
  data: { enabled: false, methods: [], currency: 'USD', reason: 'NOT_CONFIGURED' },
  requestId: 'req-1',
};

const pageData = (page: number) => ({
  data: { items: [], page, pageSize: 20, total: 0 },
  requestId: 'req-1',
});

describe('钱包查询钩子', () => {
  it('记录键包含用户与分页，不同页独立请求', async () => {
    recordsMock.mockResolvedValueOnce(pageData(1)).mockResolvedValueOnce(pageData(2));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    expect(topupRecordsKey(42, 1, 20)).toEqual([...WALLET_QUERY_KEY, 42, 'topups', 1, 20]);

    const first = renderHook(() => useTopupRecordsQuery(42, 1, 20), { wrapper: wrapper(client) });
    const second = renderHook(() => useTopupRecordsQuery(42, 2, 20), { wrapper: wrapper(client) });

    await waitFor(() => expect(first.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(second.result.current.isSuccess).toBe(true));
    expect(recordsMock).toHaveBeenCalledTimes(2);
  });

  it('记录失败不影响能力数据', async () => {
    optionsMock.mockResolvedValueOnce(optionsData);
    recordsMock.mockRejectedValueOnce(failure('UPSTREAM_ERROR'));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrap = wrapper(client);

    const options = renderHook(() => useTopupOptionsQuery(42), { wrapper: wrap });
    const records = renderHook(() => useTopupRecordsQuery(42, 1, 20), { wrapper: wrap });

    await waitFor(() => expect(options.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(records.result.current.isError).toBe(true));
    expect(options.result.current.data).toBeDefined();
  });

  it('会话失效清理认证与钱包范围，局部失败保留数据', () => {
    const client = new QueryClient();
    client.setQueryData(AUTH_PROFILE_QUERY_KEY, { id: 42 });
    client.setQueryData([...WALLET_QUERY_KEY, 42, 'options'], { enabled: false });
    client.setQueryData(topupRecordsKey(42, 1, 20), { total: 0 });

    handleWalletQueryError(client, failure('UNAUTHENTICATED'));
    expect(client.getQueryData(AUTH_PROFILE_QUERY_KEY)).toBeUndefined();
    expect(client.getQueryData([...WALLET_QUERY_KEY, 42, 'options'])).toBeUndefined();
    expect(client.getQueryData(topupRecordsKey(42, 1, 20))).toBeUndefined();

    client.setQueryData([...WALLET_QUERY_KEY, 42, 'options'], { enabled: false });
    handleWalletQueryError(client, failure('UPSTREAM_ERROR'));
    expect(client.getQueryData([...WALLET_QUERY_KEY, 42, 'options'])).toEqual({ enabled: false });
  });

  it('任一只读请求 401 清除认证、余额、钱包、汇总与流水用户作用域缓存', () => {
    const client = new QueryClient();
    const range = buildWalletRange('24h', new Date('2026-09-10T08:00:00.000Z'), 'UTC');
    const summary = consumptionSummaryKey(42, range);
    const tx = transactionsKey(42, range, 'ALL', 1);

    client.setQueryData(AUTH_PROFILE_QUERY_KEY, { id: 42 });
    client.setQueryData([...USAGE_QUERY_KEY, 42, 'balance'], { amount: '1' });
    client.setQueryData([...WALLET_QUERY_KEY, 42, 'options'], { enabled: false });
    client.setQueryData(topupRecordsKey(42, 1, 20), { total: 0 });
    client.setQueryData(summary, { total: '60' });
    client.setQueryData(tx, { total: 1 });

    handleWalletQueryError(client, failure('UNAUTHENTICATED'));

    expect(client.getQueryData(AUTH_PROFILE_QUERY_KEY)).toBeUndefined();
    expect(client.getQueryData([...USAGE_QUERY_KEY, 42, 'balance'])).toBeUndefined();
    expect(client.getQueryData([...WALLET_QUERY_KEY, 42, 'options'])).toBeUndefined();
    expect(client.getQueryData(topupRecordsKey(42, 1, 20))).toBeUndefined();
    expect(client.getQueryData(summary)).toBeUndefined();
    expect(client.getQueryData(tx)).toBeUndefined();
  });

  it('普通区域失败不清除其他成功数据', () => {
    const client = new QueryClient();
    const range = buildWalletRange('24h', new Date('2026-09-10T08:00:00.000Z'), 'UTC');
    const summary = consumptionSummaryKey(42, range);
    const tx = transactionsKey(42, range, 'ALL', 1);

    client.setQueryData([...USAGE_QUERY_KEY, 42, 'balance'], { amount: '1' });
    client.setQueryData(summary, { total: '60' });
    client.setQueryData(tx, { total: 1 });

    handleWalletQueryError(client, failure('UPSTREAM_ERROR'));

    expect(client.getQueryData([...USAGE_QUERY_KEY, 42, 'balance'])).toEqual({ amount: '1' });
    expect(client.getQueryData(summary)).toEqual({ total: '60' });
    expect(client.getQueryData(tx)).toEqual({ total: 1 });
  });
});
