import { describe, expect, it } from 'vitest';
import { formatDate, formatMoney, formatNumber, formatQuotaAmount } from './format';

describe('集中格式化', () => {
  it('按语言格式化日期与数字', () => {
    expect(formatDate('en-US', new Date(2026, 0, 2))).toContain('2026');
    expect(formatDate('zh-CN', new Date(2026, 0, 2))).toContain('2026');
    expect(formatNumber('en-US', 1234567.89)).toBe('1,234,567.89');
  });

  it('有币种时格式化金额', () => {
    expect(formatMoney('en-US', 1234.5, 'USD')).toBe('$1,234.50');
    expect(formatMoney('zh-CN', 1234.5, 'CNY')).toContain('1,234.50');
  });

  it('缺币种或额度单位时不猜测金额', () => {
    expect(formatMoney('zh-CN', 100)).toBeNull();
    expect(formatMoney('en-US', 100, '')).toBeNull();
    expect(formatQuotaAmount('zh-CN', 100)).toBeNull();
    expect(formatQuotaAmount('zh-CN', 100, '次')).toContain('100');
  });
});
