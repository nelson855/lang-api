import { describe, expect, it } from 'vitest';
import { WALLET_QUERY_KEY } from '../../api/wallet';
import { buildWalletRange } from './walletRange';
import {
  consumptionSummaryKey,
  transactionsKey,
} from './walletAnalyticsCache';

const NOW = new Date('2026-09-10T08:00:00.000Z');

describe('消费汇总查询键', () => {
  it('包含命名空间、用户与完整范围', () => {
    const range = buildWalletRange('24h', NOW, 'UTC');
    const key = consumptionSummaryKey(42, range);
    expect(key[0]).toBe(WALLET_QUERY_KEY[0]);
    expect(key[1]).toBe(WALLET_QUERY_KEY[1]);
    expect(key).toContain(42);
    expect(key).toContain(range.startTime);
    expect(key).toContain(range.endTime);
    expect(key).toContain(range.granularity);
    expect(key).toContain(range.timezone);
  });

  it('同一语义输入产生稳定的键', () => {
    const a = consumptionSummaryKey(42, buildWalletRange('7d', NOW, 'Asia/Shanghai'));
    const b = consumptionSummaryKey(42, buildWalletRange('7d', NOW, 'Asia/Shanghai'));
    expect(a).toEqual(b);
  });

  it('不同用户、不同范围不共享缓存', () => {
    const range = buildWalletRange('24h', NOW, 'UTC');
    const otherRange = buildWalletRange('7d', NOW, 'UTC');
    expect(consumptionSummaryKey(42, range)).not.toEqual(consumptionSummaryKey(7, range));
    expect(consumptionSummaryKey(42, range)).not.toEqual(consumptionSummaryKey(42, otherRange));
  });
});

describe('统一流水查询键', () => {
  it('包含命名空间、用户、完整范围、类型与分页', () => {
    const range = buildWalletRange('24h', NOW, 'UTC');
    const key = transactionsKey(42, range, 'CONSUMPTION', 2);
    expect(key[0]).toBe(WALLET_QUERY_KEY[0]);
    expect(key).toContain(42);
    expect(key).toContain(range.startTime);
    expect(key).toContain(range.endTime);
    expect(key).toContain('CONSUMPTION');
    expect(key).toContain(2);
    expect(key).toContain(20);
  });

  it('同一语义输入产生稳定的键', () => {
    const a = transactionsKey(42, buildWalletRange('24h', NOW, 'UTC'), 'ALL', 1);
    const b = transactionsKey(42, buildWalletRange('24h', NOW, 'UTC'), 'ALL', 1);
    expect(a).toEqual(b);
  });

  it('不同用户、范围、类型、页码不共享缓存', () => {
    const range = buildWalletRange('24h', NOW, 'UTC');
    const base = transactionsKey(42, range, 'ALL', 1);
    expect(transactionsKey(7, range, 'ALL', 1)).not.toEqual(base);
    expect(transactionsKey(42, buildWalletRange('7d', NOW, 'UTC'), 'ALL', 1)).not.toEqual(base);
    expect(transactionsKey(42, range, 'CONSUMPTION', 1)).not.toEqual(base);
    expect(transactionsKey(42, range, 'ALL', 2)).not.toEqual(base);
  });

  it('汇总键与流水键互不相同', () => {
    const range = buildWalletRange('24h', NOW, 'UTC');
    expect(consumptionSummaryKey(42, range)).not.toEqual(
      transactionsKey(42, range, 'ALL', 1),
    );
  });

  it('汇总键与流水键落在钱包命名空间下,随钱包范围清理', () => {
    const range = buildWalletRange('24h', NOW, 'UTC');
    for (const key of [consumptionSummaryKey(42, range), transactionsKey(42, range, 'ALL', 1)]) {
      expect(key.slice(0, WALLET_QUERY_KEY.length)).toEqual([...WALLET_QUERY_KEY]);
    }
  });
});
