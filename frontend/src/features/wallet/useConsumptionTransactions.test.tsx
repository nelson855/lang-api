import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ConsumptionSummary, TransactionsResponse } from '../../api/consumptionTransactions';
import { PortalApiError } from '../../api/envelope';
import { buildWalletRange } from './walletRange';
import {
  useConsumptionSummaryQuery,
  useTransactionsQuery,
} from './useConsumptionTransactions';

vi.mock('../../api/consumptionTransactionsFetch', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/consumptionTransactionsFetch')>();
  return {
    ...actual,
    fetchConsumptionSummary: vi.fn(),
    fetchTransactions: vi.fn(),
  };
});

const { fetchConsumptionSummary, fetchTransactions } = await import(
  '../../api/consumptionTransactionsFetch'
);
const summaryMock = vi.mocked(fetchConsumptionSummary);
const transactionsMock = vi.mocked(fetchTransactions);

function wrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

function summaryData(): { data: ConsumptionSummary; requestId: string } {
  return {
    data: {
      baselineVersion: 'p2-2026-09-22-a',
      range: {
        start: '2026-09-09T08:00:00.000Z',
        end: '2026-09-10T08:00:00.000Z',
        timezone: 'UTC',
        granularity: 'HOUR',
      },
      recordCount: { value: '3', unit: 'records', availability: 'AVAILABLE', reasonCode: null },
      quotaTotal: { value: '60', unit: 'quota', availability: 'AVAILABLE', reasonCode: null },
      moneyTotal: {
        value: null,
        currency: null,
        availability: 'UNAVAILABLE',
        reasonCode: 'CURRENCY_CONVERSION_NOT_VERIFIED',
      },
      coverage: { type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null },
    },
    requestId: 'req-summary',
  };
}

function transactionsData(total = 1): { data: TransactionsResponse; requestId: string } {
  return {
    data: {
      baselineVersion: 'p2-2026-09-22-a',
      range: {
        start: '2026-09-09T08:00:00.000Z',
        end: '2026-09-10T08:00:00.000Z',
        timezone: 'UTC',
        granularity: 'HOUR',
      },
      type: null,
      page: 1,
      pageSize: 20,
      total,
      availability: 'PARTIAL',
      reasonCode: null,
      coverage: [
        { type: 'TOPUP', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' },
        { type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null },
        { type: 'REFUND', availability: 'UNAVAILABLE', reasonCode: 'SOURCE_NOT_AVAILABLE' },
      ],
      items: [],
    },
    requestId: 'req-tx',
  };
}

const NOW = new Date('2026-09-10T08:00:00.000Z');

describe('消费汇总与统一流水查询钩子', () => {
  beforeEach(() => {
    summaryMock.mockReset();
    transactionsMock.mockReset();
  });

  it('可请求范围并行独立请求,互不以对方成功为前提', async () => {
    summaryMock.mockResolvedValueOnce(summaryData());
    transactionsMock.mockResolvedValueOnce(transactionsData());
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const range = buildWalletRange('24h', NOW, 'UTC');
    const wrap = wrapper(client);

    const summary = renderHook(() => useConsumptionSummaryQuery(42, range), { wrapper: wrap });
    const tx = renderHook(() => useTransactionsQuery(42, range, 'ALL', 1), { wrapper: wrap });

    await waitFor(() => expect(summary.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(tx.result.current.isSuccess).toBe(true));
    expect(summaryMock).toHaveBeenCalledTimes(1);
    expect(transactionsMock).toHaveBeenCalledTimes(1);
  });

  it('透传 AbortSignal 给两个 fetch', async () => {
    summaryMock.mockResolvedValueOnce(summaryData());
    transactionsMock.mockResolvedValueOnce(transactionsData());
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const range = buildWalletRange('24h', NOW, 'UTC');
    const wrap = wrapper(client);

    const summary = renderHook(() => useConsumptionSummaryQuery(42, range), { wrapper: wrap });
    const tx = renderHook(() => useTransactionsQuery(42, range, 'CONSUMPTION', 1), {
      wrapper: wrap,
    });

    await waitFor(() => expect(summary.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(tx.result.current.isSuccess).toBe(true));
    expect(summaryMock.mock.calls[0][1]).toBeInstanceOf(AbortSignal);
    expect(transactionsMock.mock.calls[0][3]).toBeInstanceOf(AbortSignal);
  });

  it('禁自动重试:失败只请求一次', async () => {
    summaryMock.mockRejectedValueOnce(new PortalApiError(502, 'UPSTREAM_ERROR', 'up', 'req-1'));
    transactionsMock.mockRejectedValueOnce(
      new PortalApiError(502, 'UPSTREAM_ERROR', 'up', 'req-1'),
    );
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const range = buildWalletRange('24h', NOW, 'UTC');
    const wrap = wrapper(client);

    const summary = renderHook(() => useConsumptionSummaryQuery(42, range), { wrapper: wrap });
    const tx = renderHook(() => useTransactionsQuery(42, range, 'ALL', 1), { wrapper: wrap });

    await waitFor(() => expect(summary.result.current.isError).toBe(true));
    await waitFor(() => expect(tx.result.current.isError).toBe(true));
    expect(summaryMock).toHaveBeenCalledTimes(1);
    expect(transactionsMock).toHaveBeenCalledTimes(1);
  });

  it('30D 时两个 hook 均不发送请求', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const range = buildWalletRange('30d', NOW, 'UTC');
    const wrap = wrapper(client);

    renderHook(() => useConsumptionSummaryQuery(42, range), { wrapper: wrap });
    renderHook(() => useTransactionsQuery(42, range, 'ALL', 1), { wrapper: wrap });

    await new Promise((r) => setTimeout(r, 20));
    expect(summaryMock).not.toHaveBeenCalled();
    expect(transactionsMock).not.toHaveBeenCalled();
  });

  it('URL 尚未规范化时保持 disabled,不发送请求', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const range = buildWalletRange('24h', NOW, 'UTC');
    const wrap = wrapper(client);

    renderHook(() => useConsumptionSummaryQuery(42, range, { enabled: false }), {
      wrapper: wrap,
    });
    renderHook(() => useTransactionsQuery(42, range, 'ALL', 1, { enabled: false }), {
      wrapper: wrap,
    });

    await new Promise((r) => setTimeout(r, 20));
    expect(summaryMock).not.toHaveBeenCalled();
    expect(transactionsMock).not.toHaveBeenCalled();
  });

  it('单值类型透传给 fetch,ALL 表示查询全部', async () => {
    transactionsMock.mockResolvedValueOnce(transactionsData());
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const range = buildWalletRange('24h', NOW, 'UTC');

    const { result } = renderHook(() => useTransactionsQuery(42, range, 'CONSUMPTION', 2), {
      wrapper: wrapper(client),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(transactionsMock.mock.calls[0][2]).toBe('CONSUMPTION');
  });
});

describe('快速切换范围与分页', () => {
  beforeEach(() => {
    summaryMock.mockReset();
    transactionsMock.mockReset();
  });

  it('切换范围后旧请求被取消,迟到响应不覆盖当前界面', async () => {
    let resolveFirst!: (v: { data: TransactionsResponse; requestId: string }) => void;
    const firstSignals: AbortSignal[] = [];
    transactionsMock.mockImplementationOnce(
      (_range, _page, _type, signal) =>
        new Promise((resolve, reject) => {
          firstSignals.push(signal!);
          signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
          resolveFirst = resolve;
        }),
    );
    transactionsMock.mockResolvedValueOnce(transactionsData(5));

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const rangeA = buildWalletRange('24h', NOW, 'UTC');
    const rangeB = buildWalletRange('7d', NOW, 'UTC');
    const wrap = wrapper(client);

    const hook = renderHook(({ range }) => useTransactionsQuery(42, range, 'ALL', 1), {
      wrapper: wrap,
      initialProps: { range: rangeA },
    });

    await waitFor(() => expect(transactionsMock).toHaveBeenCalledTimes(1));
    hook.rerender({ range: rangeB });
    await waitFor(() => expect(transactionsMock).toHaveBeenCalledTimes(2));

    // 旧查询失去观察者后 signal 中止。
    expect(firstSignals[0]?.aborted).toBe(true);
    // 旧请求的迟到 resolve 不得写入当前查询。
    resolveFirst(transactionsData(99));
    await waitFor(() => expect(hook.result.current.isSuccess).toBe(true));
    expect(hook.result.current.data?.data.total).toBe(5);
  });

  it('切换类型与页码产生独立请求,旧页数据不复用', async () => {
    transactionsMock.mockResolvedValueOnce(transactionsData(1));
    transactionsMock.mockResolvedValueOnce(transactionsData(2));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const range = buildWalletRange('24h', NOW, 'UTC');
    const wrap = wrapper(client);

    const hook = renderHook(
      ({ type, page }: { type: 'ALL' | 'CONSUMPTION'; page: number }) =>
        useTransactionsQuery(42, range, type, page),
      { wrapper: wrap, initialProps: { type: 'ALL' as 'ALL' | 'CONSUMPTION', page: 1 } },
    );
    await waitFor(() => expect(hook.result.current.isSuccess).toBe(true));
    expect(hook.result.current.data?.data.total).toBe(1);

    hook.rerender({ type: 'CONSUMPTION', page: 1 });
    await waitFor(() => expect(transactionsMock).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(hook.result.current.data?.data.total).toBe(2));
    expect(transactionsMock.mock.calls[1][2]).toBe('CONSUMPTION');
  });
});
