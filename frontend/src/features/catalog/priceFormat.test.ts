import { describe, expect, it } from 'vitest';
import { formatBasePricing, formatEnhancedPricing, normalizeDecimal } from './priceFormat';

const LABELS_ZH = {
  perMillionTokens: '/ 百万 Token',
  perRequest: '/ 次请求',
  unknownUnit: (unit: string) => `未知单位：${unit}`,
  input: '输入',
  output: '输出',
  request: '每次请求',
};

const LABELS_EN = {
  perMillionTokens: '/ 1M tokens',
  perRequest: '/ request',
  unknownUnit: (unit: string) => `Unknown unit: ${unit}`,
  input: 'Input',
  output: 'Output',
  request: 'Per request',
};

const ENHANCED_ZH = {
  ...LABELS_ZH,
  CACHE_INPUT: '缓存输入',
  CACHE_OUTPUT: '缓存输出',
  IMAGE_INPUT: '图片输入',
  IMAGE_OUTPUT: '图片输出',
  AUDIO_INPUT: '音频输入',
  AUDIO_OUTPUT: '音频输出',
  VIDEO: '视频',
  SEARCH: '搜索',
};

describe('十进制规范化', () => {
  it('保持整数值原文', () => {
    expect(normalizeDecimal('75')).toBe('75');
    expect(normalizeDecimal('0')).toBe('0');
    expect(normalizeDecimal('75.0')).toBe('75.0');
  });

  it('保留完整小数精度不四舍五入', () => {
    expect(normalizeDecimal('0.0000001')).toBe('0.0000001');
    expect(normalizeDecimal('123456789012345678.123456789012345678')).toBe(
      '123456789012345678.123456789012345678',
    );
  });

  it('科学计数法展开为完整十进制', () => {
    expect(normalizeDecimal('1e3')).toBe('1000');
    expect(normalizeDecimal('1.5e-7')).toBe('0.00000015');
    expect(normalizeDecimal('2E+2')).toBe('200');
    expect(normalizeDecimal('1e0')).toBe('1');
  });

  it('非法十进制返回 null', () => {
    expect(normalizeDecimal('abc')).toBeNull();
    expect(normalizeDecimal('-1')).toBeNull();
    expect(normalizeDecimal('1.')).toBeNull();
    expect(normalizeDecimal('')).toBeNull();
    expect(normalizeDecimal('Infinity')).toBeNull();
  });
});

describe('基础价格展示', () => {
  const tokenPricing = {
    mode: 'TOKEN' as const,
    currency: 'USD' as const,
    unit: 'PER_MILLION_TOKENS' as const,
    input: '75.0',
    output: '150.0',
    request: null,
  };

  it('TOKEN 模式展示输入与输出价格及单位', () => {
    expect(formatBasePricing(tokenPricing, LABELS_ZH)).toEqual({
      rows: [
        { key: 'input', label: '输入', value: '75.0' },
        { key: 'output', label: '输出', value: '150.0' },
      ],
      unitLabel: 'USD / 百万 Token',
    });
  });

  it('REQUEST 模式展示单次请求价格', () => {
    const requestPricing = {
      mode: 'REQUEST' as const,
      currency: 'USD' as const,
      unit: 'PER_REQUEST' as const,
      input: null,
      output: null,
      request: '0.003',
    };
    expect(formatBasePricing(requestPricing, LABELS_ZH)).toEqual({
      rows: [{ key: 'request', label: '每次请求', value: '0.003' }],
      unitLabel: 'USD / 次请求',
    });
  });

  it('真实零价展示为零而不是缺失', () => {
    const zero = { ...tokenPricing, input: '0', output: '0.0' };
    expect(formatBasePricing(zero, LABELS_ZH)!.rows.map((r) => r.value)).toEqual(['0', '0.0']);
  });

  it('极小值与大十进制数保持精度', () => {
    const extreme = { ...tokenPricing, input: '0.0000001', output: '99999999999999999999' };
    expect(formatBasePricing(extreme, LABELS_ZH)!.rows.map((r) => r.value)).toEqual([
      '0.0000001',
      '99999999999999999999',
    ]);
  });

  it('英文单位文案不混用中文', () => {
    expect(formatBasePricing(tokenPricing, LABELS_EN)!.unitLabel).toBe('USD / 1M tokens');
  });

  it('price 为 null 时无可展示行', () => {
    expect(formatBasePricing(null, LABELS_ZH)).toBeNull();
  });
});

describe('增强价格展示', () => {
  it('null 显示暂无数据，不等于无额外收费', () => {
    expect(formatEnhancedPricing(null, ENHANCED_ZH)).toEqual({ state: 'unknown', rows: [] });
  });

  it('空数组表示已验证无增强计费项目', () => {
    expect(formatEnhancedPricing([], ENHANCED_ZH)).toEqual({ state: 'verified-empty', rows: [] });
  });

  it('展示项目、币种、单位与价格', () => {
    const result = formatEnhancedPricing(
      [{ type: 'CACHE_INPUT', currency: 'USD', unit: 'PER_MILLION_TOKENS', price: '0.5' }],
      ENHANCED_ZH,
    );
    expect(result.state).toBe('items');
    expect(result.rows).toEqual([
      {
        key: 'CACHE_INPUT|USD|PER_MILLION_TOKENS',
        label: '缓存输入',
        value: '0.5',
        unitLabel: 'USD / 百万 Token',
      },
    ]);
  });

  it('未知单位保留接口原文，不猜测换算', () => {
    const result = formatEnhancedPricing(
      [{ type: 'SEARCH', currency: 'USD', unit: 'PER_1K_CALLS', price: '0.01' }],
      ENHANCED_ZH,
    );
    expect(result.rows[0].unitLabel).toBe('USD 未知单位：PER_1K_CALLS');
  });

  it('任意精度与科学计数法规范展示', () => {
    const result = formatEnhancedPricing(
      [
        { type: 'VIDEO', currency: 'USD', unit: 'PER_REQUEST', price: '1.23456789012345678e-3' },
        { type: 'AUDIO_INPUT', currency: 'USD', unit: 'PER_REQUEST', price: '0' },
      ],
      ENHANCED_ZH,
    );
    expect(result.rows.map((r) => r.value)).toEqual(['0.00123456789012345678', '0']);
  });
  it('增强价格使用响应币种而非基础价 USD', () => {
    const result = formatEnhancedPricing([
      { type: 'SEARCH', currency: 'CNY', unit: 'PER_REQUEST', price: '1.5' },
      { type: 'VIDEO', currency: 'EUR', unit: 'PER_FRAME', price: '0.01' },
    ], ENHANCED_ZH);
    expect(result.rows[0].unitLabel).toBe('CNY / 次请求');
    expect(result.rows[1].unitLabel).toBe('EUR 未知单位：PER_FRAME');
  });

});
