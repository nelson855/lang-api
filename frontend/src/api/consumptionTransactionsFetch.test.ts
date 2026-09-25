import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  CONSUMPTION_SUMMARY_PATH,
  TRANSACTIONS_PATH,
  WALLET_TRANSACTIONS_PAGE_SIZE,
  buildConsumptionSummaryUrl,
  buildTransactionsUrl,
  fetchConsumptionSummary,
  fetchTransactions,
} from './consumptionTransactionsFetch';
import { InvalidPortalResponseError } from './envelope';
import type { AggregationRange } from './consumptionTransactions';

function response(data: unknown, status = 200) {
  return new Response(JSON.stringify({ requestId: 'req-test', data }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const RANGE: AggregationRange = {
  start: '2026-09-01T00:00:00Z',
  end: '2026-09-08T00:00:00Z',
  timezone: 'UTC',
  granularity: 'HOUR',
};

const SUMMARY_PAYLOAD = {
  baselineVersion: 'p2-2026-09-22-a',
  range: RANGE,
  recordCount: { value: '3', unit: 'records', availability: 'AVAILABLE', reasonCode: null },
  quotaTotal: { value: '60', unit: 'quota', availability: 'AVAILABLE', reasonCode: null },
  moneyTotal: {
    value: null,
    currency: null,
    availability: 'UNAVAILABLE',
    reasonCode: 'CURRENCY_CONVERSION_NOT_VERIFIED',
  },
  coverage: { type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null },
};

const TX_ITEM = {
  transactionId: 'CONSUMPTION_abc',
  occurredAt: '2026-09-01T12:00:00Z',
  type: 'CONSUMPTION',
  direction: 'DEBIT',
  amount: '60',
  unit: 'QUOTA',
  currency: null,
  status: 'SUCCEEDED',
  remark: null,
  referenceId: null,
};

const TX_PAYLOAD = {
  baselineVersion: 'p2-2026-09-22-a',
  range: RANGE,
  type: null,
  page: 1,
  pageSize: 20,
  total: 1,
  availability: 'PARTIAL',
  reasonCode: null,
  coverage: [
    { type: 'TOPUP', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' },
    { type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null },
    { type: 'REFUND', availability: 'UNAVAILABLE', reasonCode: 'SOURCE_NOT_AVAILABLE' },
  ],
  items: [TX_ITEM],
};

describe('P2-05 消费汇总 URL 构造', () => {
  it('以合法相对路径 + 四个范围参数生成查询串', () => {
    const url = buildConsumptionSummaryUrl(RANGE);
    expect(url.startsWith('/portal/api/account/consumption-summary?')).toBe(true);
    expect(url).toContain('startTime=2026-09-01T00%3A00%3A00Z');
    expect(url).toContain('endTime=2026-09-08T00%3A00%3A00Z');
    expect(url).toContain('granularity=HOUR');
    expect(url).toContain('timezone=UTC');
  });

  it('IANA 时区含斜杠也能正确编码', () => {
    const url = buildConsumptionSummaryUrl({ ...RANGE, timezone: 'Asia/Shanghai' });
    expect(url).toContain('timezone=Asia%2FShanghai');
  });

  it('拒绝空白或缺失字段', () => {
    expect(() =>
      buildConsumptionSummaryUrl({ ...RANGE, start: '' }),
    ).toThrow();
    expect(() =>
      buildConsumptionSummaryUrl({ ...RANGE, end: '   ' }),
    ).toThrow();
    expect(() =>
      buildConsumptionSummaryUrl({ start: '', end: '', timezone: '', granularity: '' } as never),
    ).toThrow();
  });
});

describe('P2-05 统一流水 URL 构造', () => {
  it('默认 page=1 且 pageSize 固定 20,type 缺省即查询全部', () => {
    const url = buildTransactionsUrl(RANGE, 1);
    expect(url.startsWith('/portal/api/account/transactions?')).toBe(true);
    expect(url).toContain('page=1');
    expect(url).toContain(`pageSize=${WALLET_TRANSACTIONS_PAGE_SIZE}`);
    expect(url).not.toContain('type=');
    expect(WALLET_TRANSACTIONS_PAGE_SIZE).toBe(20);
  });

  it('显式 type 加入查询串,只允许 TOPUP/CONSUMPTION/REFUND', () => {
    expect(buildTransactionsUrl(RANGE, 2, 'CONSUMPTION')).toContain('type=CONSUMPTION');
    expect(buildTransactionsUrl(RANGE, 1, 'TOPUP')).toContain('type=TOPUP');
    expect(buildTransactionsUrl(RANGE, 1, 'REFUND')).toContain('type=REFUND');
    expect(() => buildTransactionsUrl(RANGE, 1, 'ALL' as never)).toThrow();
    expect(() => buildTransactionsUrl(RANGE, 1, ['TOPUP', 'REFUND'] as never)).toThrow();
  });

  it('拒绝非正整数或超过深分页上限的页码(page*pageSize ≤ 200)', () => {
    expect(() => buildTransactionsUrl(RANGE, 0)).toThrow();
    expect(() => buildTransactionsUrl(RANGE, -1)).toThrow();
    expect(() => buildTransactionsUrl(RANGE, 1.5)).toThrow();
    expect(() => buildTransactionsUrl(RANGE, 11)).toThrow(); // 11*20=220 > 200
    expect(() => buildTransactionsUrl(RANGE, 10)).not.toThrow(); // 10*20=200 边界内
  });

  it('拒绝调用方覆盖 pageSize 或附加未知参数', () => {
    expect(() =>
      buildTransactionsUrl(RANGE, 1, undefined, { pageSize: 100 } as never),
    ).toThrow();
    expect(() =>
      buildTransactionsUrl(RANGE, 1, undefined, { baselineVersion: 'x' } as never),
    ).toThrow();
    expect(() =>
      buildTransactionsUrl(RANGE, 1, undefined, { userId: 42 } as never),
    ).toThrow();
  });
});

describe('P2-05 fetchConsumptionSummary', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn()));
  afterEach(() => vi.unstubAllGlobals());

  it('请求相对路径并返回 strict schema 通过的数据', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(response(SUMMARY_PAYLOAD));
    const result = await fetchConsumptionSummary(RANGE);
    expect(vi.mocked(fetch).mock.calls[0]?.[0]).toBe(buildConsumptionSummaryUrl(RANGE));
    expect(result.data.quotaTotal.value).toBe('60');
    expect(result.data.moneyTotal.availability).toBe('UNAVAILABLE');
  });

  it('透传 AbortSignal 给底层 fetch', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(response(SUMMARY_PAYLOAD));
    const ac = new AbortController();
    await fetchConsumptionSummary(RANGE, ac.signal);
    const init = vi.mocked(fetch).mock.calls[0]?.[1] as RequestInit;
    expect(init.signal).toBe(ac.signal);
  });

  it('拒绝带有多余字段的上游响应', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      response({ ...SUMMARY_PAYLOAD, quotaPerUsd: '500000' }),
    );
    await expect(fetchConsumptionSummary(RANGE)).rejects.toBeInstanceOf(InvalidPortalResponseError);
  });

  it('斜线时区的汇总请求可通过路径校验并发出', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(response(SUMMARY_PAYLOAD));
    const result = await fetchConsumptionSummary({ ...RANGE, timezone: 'Asia/Shanghai' });
    expect(vi.mocked(fetch).mock.calls[0]?.[0]).toContain('timezone=Asia%2FShanghai');
    expect(result.data.quotaTotal.value).toBe('60');
  });
});

describe('P2-05 fetchTransactions', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn()));
  afterEach(() => vi.unstubAllGlobals());

  it('默认请求全部类型,不包含 userId 或其他身份字段', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(response(TX_PAYLOAD));
    const result = await fetchTransactions(RANGE, 1);
    const calledUrl = vi.mocked(fetch).mock.calls[0]?.[0] as string;
    expect(calledUrl.startsWith(TRANSACTIONS_PATH + '?')).toBe(true);
    expect(calledUrl).not.toContain('userId=');
    expect(calledUrl).not.toContain('baseline=');
    expect(result.data.items[0]?.transactionId).toBe('CONSUMPTION_abc');
  });

  it('显式 type 加入查询串', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      response({
        ...TX_PAYLOAD,
        type: 'CONSUMPTION',
        coverage: [{ type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null }],
      }),
    );
    await fetchTransactions(RANGE, 2, 'CONSUMPTION');
    const calledUrl = vi.mocked(fetch).mock.calls[0]?.[0] as string;
    expect(calledUrl).toContain('type=CONSUMPTION');
    expect(calledUrl).toContain('page=2');
  });

  it('透传 AbortSignal', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(response(TX_PAYLOAD));
    const ac = new AbortController();
    await fetchTransactions(RANGE, 1, undefined, ac.signal);
    const init = vi.mocked(fetch).mock.calls[0]?.[1] as RequestInit;
    expect(init.signal).toBe(ac.signal);
  });

  it('非法响应拒绝:含上游原始 userId 字段', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      response({ ...TX_PAYLOAD, userId: 7 }),
    );
    await expect(fetchTransactions(RANGE, 1)).rejects.toBeInstanceOf(InvalidPortalResponseError);
  });
});

describe('P2-05 路径常量', () => {
  it('消费汇总与统一流水路径固定', () => {
    expect(CONSUMPTION_SUMMARY_PATH).toBe('/portal/api/account/consumption-summary');
    expect(TRANSACTIONS_PATH).toBe('/portal/api/account/transactions');
  });
});
