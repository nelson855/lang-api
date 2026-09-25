import { describe, expect, it } from 'vitest';
import {
  consumptionSummarySchema,
  transactionsResponseSchema,
  transactionItemSchema,
  availabilitySchema,
  transactionCoverageSchema,
  transactionTypeSchema,
  type ConsumptionSummary,
  type TransactionsResponse,
} from './consumptionTransactions';

const RANGE = {
  start: '2026-09-01T00:00:00Z',
  end: '2026-09-08T00:00:00Z',
  timezone: 'UTC',
  granularity: 'HOUR',
} as const;

const AVAILABLE_RECORDS = {
  value: '3',
  unit: 'records',
  availability: 'AVAILABLE',
  reasonCode: null,
} as const;

const AVAILABLE_QUOTA = {
  value: '60',
  unit: 'quota',
  availability: 'AVAILABLE',
  reasonCode: null,
} as const;

const UNAVAILABLE_MONEY = {
  value: null,
  currency: null,
  availability: 'UNAVAILABLE',
  reasonCode: 'CURRENCY_CONVERSION_NOT_VERIFIED',
} as const;

describe('P2-05 availability 枚举', () => {
  it('只允许 AVAILABLE / PARTIAL / UNAVAILABLE', () => {
    expect(availabilitySchema.parse('AVAILABLE')).toBe('AVAILABLE');
    expect(availabilitySchema.parse('PARTIAL')).toBe('PARTIAL');
    expect(availabilitySchema.parse('UNAVAILABLE')).toBe('UNAVAILABLE');
    expect(() => availabilitySchema.parse('UNKNOWN')).toThrow();
    expect(() => availabilitySchema.parse('available')).toThrow();
    expect(() => availabilitySchema.parse('')).toThrow();
  });
});

describe('P2-05 消费汇总 strict schema', () => {
  const base: ConsumptionSummary = {
    baselineVersion: 'p2-2026-09-22-a',
    range: RANGE,
    recordCount: AVAILABLE_RECORDS,
    quotaTotal: AVAILABLE_QUOTA,
    moneyTotal: UNAVAILABLE_MONEY,
    coverage: { type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null },
  };

  it('接受当前基线的合法响应（recordCount/quotaTotal 可用,moneyTotal 不可用)', () => {
    const parsed = consumptionSummarySchema.parse(base);
    expect(parsed.recordCount.value).toBe('3');
    expect(parsed.quotaTotal.value).toBe('60');
    expect(parsed.moneyTotal.value).toBeNull();
    expect(parsed.moneyTotal.currency).toBeNull();
    expect(parsed.moneyTotal.availability).toBe('UNAVAILABLE');
  });

  it('拒绝未知字段(如 userId、baseline、quotaPerUsd 等上游残留)', () => {
    expect(() =>
      consumptionSummarySchema.parse({ ...base, userId: 42 }),
    ).toThrow();
    expect(() =>
      consumptionSummarySchema.parse({ ...base, baseline: 'p1' }),
    ).toThrow();
    expect(() =>
      consumptionSummarySchema.parse({
        ...base,
        moneyTotal: { ...UNAVAILABLE_MONEY, quotaPerUsd: '500000' },
      }),
    ).toThrow();
  });

  it('拒绝缺字段或字段类型错误', () => {
    const noRecord: Record<string, unknown> = { ...base };
    delete noRecord.recordCount;
    expect(() => consumptionSummarySchema.parse(noRecord)).toThrow();
    expect(() =>
      consumptionSummarySchema.parse({ ...base, recordCount: null }),
    ).toThrow();
    expect(() =>
      consumptionSummarySchema.parse({ ...base, baselineVersion: 1 }),
    ).toThrow();
  });

  it('range 必须秒级 ISO + IANA 时区 + 合法粒度', () => {
    expect(() =>
      consumptionSummarySchema.parse({
        ...base,
        range: { ...RANGE, start: '2026-09-01 00:00:00' },
      }),
    ).toThrow();
    expect(() =>
      consumptionSummarySchema.parse({
        ...base,
        range: { ...RANGE, granularity: 'MINUTE' },
      }),
    ).toThrow();
    expect(() =>
      consumptionSummarySchema.parse({
        ...base,
        range: { ...RANGE, timezone: '' },
      }),
    ).toThrow();
  });

  it('recordCount/quotaTotal 的 AVAILABLE 必须带值且 reasonCode=null', () => {
    expect(() =>
      consumptionSummarySchema.parse({
        ...base,
        recordCount: { value: null, unit: 'records', availability: 'AVAILABLE', reasonCode: null },
      }),
    ).toThrow();
    expect(() =>
      consumptionSummarySchema.parse({
        ...base,
        quotaTotal: { value: '60', unit: 'quota', availability: 'AVAILABLE', reasonCode: 'X' },
      }),
    ).toThrow();
  });

  it('recordCount/quotaTotal 的 UNAVAILABLE 必须 value=null 且 reasonCode 非空', () => {
    expect(() =>
      consumptionSummarySchema.parse({
        ...base,
        recordCount: { value: '3', unit: 'records', availability: 'UNAVAILABLE', reasonCode: 'X' },
      }),
    ).toThrow();
    expect(() =>
      consumptionSummarySchema.parse({
        ...base,
        quotaTotal: { value: null, unit: 'quota', availability: 'UNAVAILABLE', reasonCode: null },
      }),
    ).toThrow();
  });

  it('moneyTotal UNAVAILABLE 必须 value/currency 均为 null 且 reasonCode 非空', () => {
    expect(() =>
      consumptionSummarySchema.parse({
        ...base,
        moneyTotal: { value: '0', currency: 'USD', availability: 'UNAVAILABLE', reasonCode: 'X' },
      }),
    ).toThrow();
    expect(() =>
      consumptionSummarySchema.parse({
        ...base,
        moneyTotal: { value: null, currency: null, availability: 'UNAVAILABLE', reasonCode: null },
      }),
    ).toThrow();
  });

  it('moneyTotal AVAILABLE 必须提供非空 value 与 currency', () => {
    expect(() =>
      consumptionSummarySchema.parse({
        ...base,
        moneyTotal: { value: null, currency: 'USD', availability: 'AVAILABLE', reasonCode: null },
      }),
    ).toThrow();
    expect(() =>
      consumptionSummarySchema.parse({
        ...base,
        moneyTotal: { value: '1.00', currency: null, availability: 'AVAILABLE', reasonCode: null },
      }),
    ).toThrow();
  });

  it('非负十进制:value 允许 0、整数、长小数,拒绝负数/非数字/科学计数法', () => {
    const ok = (v: string) => ({ value: v, unit: 'quota', availability: 'AVAILABLE', reasonCode: null });
    expect(() =>
      consumptionSummarySchema.parse({ ...base, quotaTotal: ok('0') }),
    ).not.toThrow();
    expect(() =>
      consumptionSummarySchema.parse({ ...base, quotaTotal: ok('9999999999999999999') }),
    ).not.toThrow();
    expect(() =>
      consumptionSummarySchema.parse({ ...base, quotaTotal: ok('0.123456789012345678') }),
    ).not.toThrow();
    expect(() =>
      consumptionSummarySchema.parse({ ...base, quotaTotal: ok('-1') }),
    ).toThrow();
    expect(() =>
      consumptionSummarySchema.parse({ ...base, quotaTotal: ok('1e3') }),
    ).toThrow();
    expect(() =>
      consumptionSummarySchema.parse({ ...base, quotaTotal: ok('abc') }),
    ).toThrow();
  });

  it('coverage 枚举: type 限定 CONSUMPTION,可用性与原因合理', () => {
    expect(() =>
      consumptionSummarySchema.parse({
        ...base,
        coverage: { type: 'TOPUP', availability: 'AVAILABLE', reasonCode: null },
      }),
    ).toThrow();
    expect(() =>
      consumptionSummarySchema.parse({
        ...base,
        coverage: { type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: 'X' },
      }),
    ).toThrow();
    expect(() =>
      consumptionSummarySchema.parse({
        ...base,
        coverage: { type: 'CONSUMPTION', availability: 'UNAVAILABLE', reasonCode: null },
      }),
    ).toThrow();
    // PARTIAL + MISSING_STABLE_REFERENCE 合法
    const partial = consumptionSummarySchema.parse({
      ...base,
      coverage: { type: 'CONSUMPTION', availability: 'PARTIAL', reasonCode: 'MISSING_STABLE_REFERENCE' },
    });
    expect(partial.coverage.availability).toBe('PARTIAL');
  });
});

describe('P2-05 统一流水 item strict schema', () => {
  const baseItem = {
    transactionId: 'CONSUMPTION_abc123',
    occurredAt: '2026-09-01T12:00:00Z',
    type: 'CONSUMPTION',
    direction: 'DEBIT',
    amount: '60',
    unit: 'QUOTA',
    currency: null,
    status: 'SUCCEEDED',
    remark: 'gpt-4o',
    referenceId: 'req-abc',
  };

  it('接受合法消费条目', () => {
    const parsed = transactionItemSchema.parse(baseItem);
    expect(parsed.type).toBe('CONSUMPTION');
    expect(parsed.direction).toBe('DEBIT');
  });

  it('type 仅允许 TOPUP / CONSUMPTION / REFUND', () => {
    expect(transactionTypeSchema.parse('TOPUP')).toBe('TOPUP');
    expect(transactionTypeSchema.parse('CONSUMPTION')).toBe('CONSUMPTION');
    expect(transactionTypeSchema.parse('REFUND')).toBe('REFUND');
    expect(() => transactionTypeSchema.parse('ALL')).toThrow();
    expect(() => transactionTypeSchema.parse('TRANSFER')).toThrow();
  });

  it('direction 仅允许 CREDIT / DEBIT', () => {
    expect(() => transactionItemSchema.parse({ ...baseItem, direction: 'IN' })).toThrow();
    expect(() => transactionItemSchema.parse({ ...baseItem, direction: 'CREDIT' })).not.toThrow();
  });

  it('status 允许五种枚举,拒绝未知状态', () => {
    for (const s of ['PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED', 'UNKNOWN'] as const) {
      expect(() => transactionItemSchema.parse({ ...baseItem, status: s })).not.toThrow();
    }
    expect(() => transactionItemSchema.parse({ ...baseItem, status: 'CANCELLED' })).toThrow();
  });

  it('amount 必须是非负十进制字符串,拒绝负号和科学计数法', () => {
    expect(() => transactionItemSchema.parse({ ...baseItem, amount: '0' })).not.toThrow();
    expect(() =>
      transactionItemSchema.parse({ ...baseItem, amount: '12345678901234567890123456789.123456' }),
    ).not.toThrow();
    expect(() => transactionItemSchema.parse({ ...baseItem, amount: '-1' })).toThrow();
    expect(() => transactionItemSchema.parse({ ...baseItem, amount: '1.5e2' })).toThrow();
    expect(() => transactionItemSchema.parse({ ...baseItem, amount: '' })).toThrow();
  });

  it('unit=QUOTA 时 currency 必须为 null;unit=CURRENCY 时 currency 必须非空', () => {
    expect(() =>
      transactionItemSchema.parse({ ...baseItem, unit: 'QUOTA', currency: 'USD' }),
    ).toThrow();
    expect(() =>
      transactionItemSchema.parse({ ...baseItem, unit: 'CURRENCY', currency: null }),
    ).toThrow();
    expect(() =>
      transactionItemSchema.parse({ ...baseItem, unit: 'CURRENCY', currency: '' }),
    ).toThrow();
    expect(() =>
      transactionItemSchema.parse({ ...baseItem, unit: 'CURRENCY', currency: 'USD' }),
    ).not.toThrow();
  });

  it('remark 与 referenceId 可空;为 null 时通过,缺失字段时报错', () => {
    expect(() =>
      transactionItemSchema.parse({ ...baseItem, remark: null, referenceId: null }),
    ).not.toThrow();
    const noRemark: Record<string, unknown> = { ...baseItem };
    delete noRemark.remark;
    expect(() => transactionItemSchema.parse(noRemark)).toThrow();
  });

  it('拒绝未声明字段(如 userId、tokenId、上游 channel)', () => {
    expect(() =>
      transactionItemSchema.parse({ ...baseItem, userId: 1 }),
    ).toThrow();
    expect(() =>
      transactionItemSchema.parse({ ...baseItem, tokenId: 7 }),
    ).toThrow();
    expect(() =>
      transactionItemSchema.parse({ ...baseItem, channel: 'openai' }),
    ).toThrow();
  });
});

describe('P2-05 统一流水 response strict schema', () => {
  const baseItem = {
    transactionId: 'CONSUMPTION_abc123',
    occurredAt: '2026-09-01T12:00:00Z',
    type: 'CONSUMPTION',
    direction: 'DEBIT',
    amount: '60',
    unit: 'QUOTA',
    currency: null,
    status: 'SUCCEEDED',
    remark: 'gpt-4o',
    referenceId: 'req-abc',
  };

  const fullCoverage = [
    { type: 'TOPUP', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' },
    { type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null },
    { type: 'REFUND', availability: 'UNAVAILABLE', reasonCode: 'SOURCE_NOT_AVAILABLE' },
  ] as const;

  const baseResp = {
    baselineVersion: 'p2-2026-09-22-a',
    range: RANGE,
    type: null,
    page: 1,
    pageSize: 20,
    total: 1,
    availability: 'PARTIAL',
    reasonCode: null,
    coverage: fullCoverage,
    items: [baseItem],
  };

  it('接受完整响应(全部类型 + PARTIAL + coverage)', () => {
    const parsed = transactionsResponseSchema.parse(baseResp);
    expect(parsed.total).toBe(1);
    expect(parsed.coverage).toHaveLength(3);
    expect(parsed.items[0]?.transactionId).toBe('CONSUMPTION_abc123');
  });

  it('type=null 表示全部类型;type=CONSUMPTION 表示单类型', () => {
    expect(transactionsResponseSchema.parse(baseResp).type).toBeNull();
    const single: TransactionsResponse = transactionsResponseSchema.parse({
      ...baseResp,
      type: 'CONSUMPTION',
      coverage: [{ type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null }],
    });
    expect(single.type).toBe('CONSUMPTION');
    expect(single.coverage).toHaveLength(1);
  });

  it('type=TOPUP 的 UNAVAILABLE 响应:items=[]、total=0、reasonCode=BASELINE_NOT_VERIFIED', () => {
    const parsed = transactionsResponseSchema.parse({
      ...baseResp,
      type: 'TOPUP',
      items: [],
      total: 0,
      availability: 'UNAVAILABLE',
      reasonCode: 'BASELINE_NOT_VERIFIED',
      coverage: [{ type: 'TOPUP', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' }],
    });
    expect(parsed.items).toHaveLength(0);
    expect(parsed.reasonCode).toBe('BASELINE_NOT_VERIFIED');
  });

  it('拒绝 page<1、pageSize<1 或 total<0', () => {
    expect(() => transactionsResponseSchema.parse({ ...baseResp, page: 0 })).toThrow();
    expect(() => transactionsResponseSchema.parse({ ...baseResp, pageSize: 0 })).toThrow();
    expect(() => transactionsResponseSchema.parse({ ...baseResp, total: -1 })).toThrow();
  });

  it('拒绝未知 type、未知 coverage 类型或数组含重复类型', () => {
    expect(() =>
      transactionsResponseSchema.parse({ ...baseResp, type: 'TRANSFER' }),
    ).toThrow();
    expect(() =>
      transactionsResponseSchema.parse({
        ...baseResp,
        coverage: [{ type: 'TRANSFER', availability: 'AVAILABLE', reasonCode: null }],
      }),
    ).toThrow();
    expect(() =>
      transactionsResponseSchema.parse({
        ...baseResp,
        coverage: [
          { type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null },
          { type: 'CONSUMPTION', availability: 'PARTIAL', reasonCode: 'MISSING_STABLE_REFERENCE' },
        ],
      }),
    ).toThrow();
  });

  it('coverage 数组至少 1 项,可空 reasonCode 仅当 availability=AVAILABLE 时为 null', () => {
    expect(() =>
      transactionsResponseSchema.parse({ ...baseResp, coverage: [] }),
    ).toThrow();
    expect(() =>
      transactionsResponseSchema.parse({
        ...baseResp,
        coverage: [{ type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: 'X' }],
      }),
    ).toThrow();
    expect(() =>
      transactionsResponseSchema.parse({
        ...baseResp,
        coverage: [{ type: 'CONSUMPTION', availability: 'UNAVAILABLE', reasonCode: null }],
      }),
    ).toThrow();
  });

  it('拒绝携带上游原始字段或 userId 的响应', () => {
    expect(() =>
      transactionsResponseSchema.parse({ ...baseResp, userId: 7 }),
    ).toThrow();
    expect(() =>
      transactionsResponseSchema.parse({
        ...baseResp,
        items: [{ ...baseItem, token_id: 1 }],
      }),
    ).toThrow();
  });
});

describe('P2-05 transactionCoverage schema', () => {
  it('三种来源类型全部允许', () => {
    for (const t of ['TOPUP', 'CONSUMPTION', 'REFUND'] as const) {
      const parsed = transactionCoverageSchema.parse({
        type: t,
        availability: 'AVAILABLE',
        reasonCode: null,
      });
      expect(parsed.type).toBe(t);
    }
  });

  it('AVAILABLE 必须 reasonCode=null,UNAVAILABLE/PARTIAL 必须非空', () => {
    expect(() =>
      transactionCoverageSchema.parse({ type: 'TOPUP', availability: 'AVAILABLE', reasonCode: 'X' }),
    ).toThrow();
    expect(() =>
      transactionCoverageSchema.parse({ type: 'TOPUP', availability: 'UNAVAILABLE', reasonCode: null }),
    ).toThrow();
    expect(() =>
      transactionCoverageSchema.parse({ type: 'TOPUP', availability: 'PARTIAL', reasonCode: null }),
    ).toThrow();
  });
});
