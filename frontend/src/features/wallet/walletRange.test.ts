import { describe, expect, it } from 'vitest';
import {
  WALLET_RANGE_PRESETS,
  buildWalletRange,
} from './walletRange';

describe('钱包范围 preset 清单', () => {
  it('声明 24H / 今天 / 昨天 / 7D / 30D 五种 preset', () => {
    expect(WALLET_RANGE_PRESETS.map((p) => p.value)).toEqual([
      '24h',
      'today',
      'yesterday',
      '7d',
      '30d',
    ]);
  });

  it('24H/今天/昨天/7D 均可请求;30D 可发现但 disabled', () => {
    const map = new Map(WALLET_RANGE_PRESETS.map((p) => [p.value, p.enabled]));
    expect(map.get('24h')).toBe(true);
    expect(map.get('today')).toBe(true);
    expect(map.get('yesterday')).toBe(true);
    expect(map.get('7d')).toBe(true);
    expect(map.get('30d')).toBe(false);
  });

  it('30D 粒度为 DAY,其余均为 HOUR', () => {
    const map = new Map(WALLET_RANGE_PRESETS.map((p) => [p.value, p.granularity]));
    expect(map.get('30d')).toBe('DAY');
    expect(map.get('24h')).toBe('HOUR');
    expect(map.get('7d')).toBe('HOUR');
  });

  it('buildWalletRange 返回冻结对象且保留 preset 标识', () => {
    const now = new Date('2026-09-10T08:00:00Z');
    const range = buildWalletRange('7d', now, 'Asia/Shanghai');
    expect(range.preset).toBe('7d');
    expect(range.timezone).toBe('Asia/Shanghai');
    expect(range.enabled).toBe(true);
    expect(Object.isFrozen(range)).toBe(true);
  });

  it('30D 会生成正确边界但 enabled=false', () => {
    const now = new Date('2026-09-10T08:00:00Z');
    const range = buildWalletRange('30d', now, 'UTC');
    expect(range.enabled).toBe(false);
    expect((Date.parse(range.endTime) - Date.parse(range.startTime)) / 1000).toBe(30 * 24 * 3600);
  });
});
