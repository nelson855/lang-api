import { describe, expect, it } from 'vitest';
import type { ConsumptionSummary, TransactionsResponse } from '../../api/consumptionTransactions';
import { formatDecimalString } from './formatDecimal';
import { formatWalletTime } from './formatWalletTime';
import {
  toConsumptionSummaryDisplay,
  toTransactionItemDisplay,
  toTransactionsDisplay,
} from './walletDisplay';

const SUMMARY_BASE: ConsumptionSummary = {
  baselineVersion: 'p2-2026-09-22-a',
  range: {
    start: '2026-09-01T00:00:00Z',
    end: '2026-09-08T00:00:00Z',
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
};

const ITEM_BASE = {
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
} as const;

describe('消费汇总展示映射', () => {
  it('基线响应:recordCount/quota 显示真实值,money 显示不可用原因', () => {
    const display = toConsumptionSummaryDisplay(SUMMARY_BASE, 'zh-CN');
    expect(display.status).toBe('ok');
    if (display.status !== 'ok') return;
    expect(display.recordCount).toMatchObject({ status: 'value', text: '3' });
    expect(display.quotaTotal).toMatchObject({ status: 'value', text: '60' });
    expect(display.moneyTotal.status).toBe('unavailable');
    if (display.moneyTotal.status === 'unavailable') {
      expect(display.moneyTotal.reasonKey).toContain('CurrencyConversionNotVerified');
    }
  });

  it('真实零:AVAILABLE 的零值显示为零,不走 empty 分支', () => {
    const display = toConsumptionSummaryDisplay(
      {
        ...SUMMARY_BASE,
        recordCount: { value: '0', unit: 'records', availability: 'AVAILABLE', reasonCode: null },
        quotaTotal: { value: '0', unit: 'quota', availability: 'AVAILABLE', reasonCode: null },
      },
      'zh-CN',
    );
    expect(display.status).toBe('ok');
    if (display.status !== 'ok') return;
    expect(display.recordCount).toMatchObject({ status: 'value', text: '0' });
    expect(display.quotaTotal).toMatchObject({ status: 'value', text: '0' });
  });

  it('PARTIAL + 缺稳定引用:保留数值并给出持续可见的部分覆盖说明', () => {
    const display = toConsumptionSummaryDisplay(
      {
        ...SUMMARY_BASE,
        coverage: {
          type: 'CONSUMPTION',
          availability: 'PARTIAL',
          reasonCode: 'MISSING_STABLE_REFERENCE',
        },
      },
      'zh-CN',
    );
    expect(display.status).toBe('ok');
    if (display.status !== 'ok') return;
    expect(display.quotaTotal).toMatchObject({ status: 'value', text: '60' });
    expect(display.coverage.status).toBe('partial');
    if (display.coverage.status === 'partial') {
      expect(display.coverage.reasonKey).toContain('MissingStableReference');
    }
  });

  it('UNAVAILABLE 的值绝不显示为零:moneyTotal 不可用时无 text', () => {
    const display = toConsumptionSummaryDisplay(SUMMARY_BASE, 'en-US');
    if (display.status !== 'ok') throw new Error('expected ok');
    expect(display.moneyTotal.status).toBe('unavailable');
    expect(display.moneyTotal).not.toHaveProperty('text');
  });

  it('未知 availability 与矛盾组合安全失败,不抛异常', () => {
    expect(
      toConsumptionSummaryDisplay(
        { ...SUMMARY_BASE, recordCount: { ...SUMMARY_BASE.recordCount, availability: 'WEIRD' } },
        'zh-CN',
      ).status,
    ).toBe('invalid');
    expect(
      toConsumptionSummaryDisplay(
        {
          ...SUMMARY_BASE,
          moneyTotal: { value: '1.00', currency: 'USD', availability: 'UNAVAILABLE', reasonCode: 'X' },
        },
        'zh-CN',
      ).status,
    ).toBe('invalid');
    expect(toConsumptionSummaryDisplay(null, 'zh-CN').status).toBe('invalid');
  });

  it('quota 单位通过 unitKey 表达,不在 text 中硬编码', () => {
    const display = toConsumptionSummaryDisplay(SUMMARY_BASE, 'zh-CN');
    if (display.status !== 'ok') throw new Error('expected ok');
    if (display.quotaTotal.status !== 'value') throw new Error('expected value');
    expect(display.quotaTotal.unitKey).toContain('Quota');
    expect(display.quotaTotal.currency).toBeNull();
  });
});

describe('统一流水展示映射', () => {
  const RESPONSE_BASE: TransactionsResponse = {
    baselineVersion: 'p2-2026-09-22-a',
    range: SUMMARY_BASE.range,
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
    items: [{ ...ITEM_BASE }],
  };

  it('消费支出条目:类型/方向/金额单位/状态全部用本地化键表达', () => {
    const item = toTransactionItemDisplay({ ...ITEM_BASE }, 'zh-CN', 'UTC');
    expect(item.status).toBe('ok');
    if (item.status !== 'ok') return;
    expect(item.typeKey).toContain('Consumption');
    expect(item.directionKey).toContain('Debit');
    expect(item.isIncome).toBe(false);
    expect(item.amountText).toBe('60');
    expect(item.amountUnitKey).toContain('Quota');
    expect(item.amountCurrency).toBeNull();
    expect(item.statusKey).toContain('Succeeded');
  });

  it('DEBIT 金额保持非负,不改写成负数', () => {
    const item = toTransactionItemDisplay({ ...ITEM_BASE }, 'zh-CN', 'UTC');
    if (item.status !== 'ok') throw new Error('expected ok');
    expect(item.amountText.startsWith('-')).toBe(false);
  });

  it('CURRENCY 条目保留币种代码,不假定 USD', () => {
    const item = toTransactionItemDisplay(
      { ...ITEM_BASE, unit: 'CURRENCY', currency: 'EUR', direction: 'CREDIT' },
      'en-US',
      'UTC',
    );
    expect(item.status).toBe('ok');
    if (item.status !== 'ok') return;
    expect(item.amountCurrency).toBe('EUR');
    expect(item.isIncome).toBe(true);
    expect(item.directionKey).toContain('Credit');
  });

  it('五种状态各有独立文案键', () => {
    for (const status of ['PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED', 'UNKNOWN'] as const) {
      const item = toTransactionItemDisplay({ ...ITEM_BASE, status }, 'zh-CN', 'UTC');
      expect(item.status).toBe('ok');
      if (item.status === 'ok') {
        expect(item.statusKey).toContain(status.charAt(0) + status.slice(1).toLowerCase());
      }
    }
  });

  it('可空备注/引用映射为 none,保持其他字段可读', () => {
    const item = toTransactionItemDisplay(
      { ...ITEM_BASE, remark: null, referenceId: null },
      'zh-CN',
      'UTC',
    );
    expect(item.status).toBe('ok');
    if (item.status !== 'ok') return;
    expect(item.remark).toEqual({ status: 'none' });
    expect(item.reference).toEqual({ status: 'none' });
    expect(item.amountText).toBe('60');
  });

  it('时间保留原始 ISO,同时给出本地化文本', () => {
    const item = toTransactionItemDisplay({ ...ITEM_BASE }, 'zh-CN', 'UTC');
    if (item.status !== 'ok') throw new Error('expected ok');
    expect(item.iso).toBe('2026-09-01T12:00:00Z');
    expect(item.timeText).not.toBe('2026-09-01T12:00:00Z');
    expect(item.timeText.length).toBeGreaterThan(0);
  });

  it('全部类型 coverage:消费可用 + 充值/退款不可用,各自给出原因', () => {
    const display = toTransactionsDisplay(RESPONSE_BASE, 'zh-CN');
    expect(display.status).toBe('ok');
    if (display.status !== 'ok') return;
    expect(display.overall).toBe('partial');
    const byType = new Map(display.coverage.map((c) => [c.type, c]));
    expect(byType.get('CONSUMPTION')).toMatchObject({ availability: 'available' });
    expect(byType.get('TOPUP')?.reasonKey).toContain('BaselineNotVerified');
    expect(byType.get('REFUND')?.reasonKey).toContain('SourceNotAvailable');
  });

  it('TOPUP 单类型不可用:空 items 但 overall 为 unavailable,不是真实空', () => {
    const display = toTransactionsDisplay(
      {
        ...RESPONSE_BASE,
        type: 'TOPUP',
        items: [],
        total: 0,
        availability: 'UNAVAILABLE',
        reasonCode: 'BASELINE_NOT_VERIFIED',
        coverage: [{ type: 'TOPUP', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' }],
      },
      'zh-CN',
    );
    expect(display.status).toBe('ok');
    if (display.status !== 'ok') return;
    expect(display.overall).toBe('unavailable');
    expect(display.isRealEmpty).toBe(false);
  });

  it('AVAILABLE + total=0 才是真实空', () => {
    const display = toTransactionsDisplay(
      {
        ...RESPONSE_BASE,
        type: 'CONSUMPTION',
        items: [],
        total: 0,
        availability: 'AVAILABLE',
        reasonCode: null,
        coverage: [{ type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null }],
      },
      'zh-CN',
    );
    if (display.status !== 'ok') throw new Error('expected ok');
    expect(display.isRealEmpty).toBe(true);
  });

  it('未知枚举与矛盾响应安全失败', () => {
    expect(
      toTransactionsDisplay({ ...RESPONSE_BASE, availability: 'WEIRD' }, 'zh-CN').status,
    ).toBe('invalid');
    expect(toTransactionItemDisplay({ ...ITEM_BASE, unit: 'GOLD' }, 'zh-CN', 'UTC').status).toBe(
      'invalid',
    );
  });
});

describe('十进制字符串精度安全格式化', () => {
  it('超安全整数不截断(不经 number)', () => {
    expect(formatDecimalString('9007199254740993', 'en-US')).toBe('9,007,199,254,740,993');
    expect(formatDecimalString('9999999999999999999', 'en-US')).toBe('9,999,999,999,999,999,999');
  });

  it('长小数完整保留,不四舍五入', () => {
    expect(formatDecimalString('0.123456789012345678', 'en-US')).toBe('0.123456789012345678');
    expect(formatDecimalString('60.50', 'en-US')).toBe('60.50');
  });

  it('zh-CN 与 en-US 分组符号一致,de-DE 使用本地分隔符', () => {
    expect(formatDecimalString('1234567.89', 'zh-CN')).toBe('1,234,567.89');
    expect(formatDecimalString('1234567.89', 'en-US')).toBe('1,234,567.89');
    expect(formatDecimalString('1234567.89', 'de-DE')).toBe('1.234.567,89');
  });

  it('非法输入抛错,不补算 USD 或返回假零', () => {
    for (const bad of ['', 'abc', '-1', '1e3', '+5', '1.2.3', 'NaN']) {
      expect(() => formatDecimalString(bad, 'en-US')).toThrow();
    }
  });
});

describe('活动 locale 与响应 timezone 的时间格式化', () => {
  it('同一 ISO 在 zh-CN 与 en-US 下文本不同', () => {
    const zh = formatWalletTime('2026-09-01T12:00:00Z', 'UTC', 'zh-CN');
    const en = formatWalletTime('2026-09-01T12:00:00Z', 'UTC', 'en-US');
    expect(zh).not.toBe(en);
    expect(zh.length).toBeGreaterThan(0);
    expect(en).toContain('2026');
  });

  it('响应 timezone 决定墙钟显示', () => {
    const utc = formatWalletTime('2026-09-01T12:00:00Z', 'UTC', 'en-US');
    const shanghai = formatWalletTime('2026-09-01T12:00:00Z', 'Asia/Shanghai', 'en-US');
    expect(utc).not.toBe(shanghai);
    expect(shanghai).toContain('20:00');
  });

  it('非法 ISO 回退为原字符串,不抛异常、不输出错误对象', () => {
    expect(formatWalletTime('not-a-date', 'UTC', 'zh-CN')).toBe('not-a-date');
  });
});
