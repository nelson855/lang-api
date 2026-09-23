import { describe, expect, it } from 'vitest';
import {
  createDisplayFormatters,
  toMetricDisplay,
  toCollectionDisplay,
  type MetricDisplayInput,
} from './displayModel';

const fmt = createDisplayFormatters('zh-CN');

describe('createDisplayFormatters', () => {
  it('按 locale 格式化数字、百分比、毫秒与货币', () => {
    expect(fmt.number(90000)).toContain('9');
    expect(fmt.percent(0.9834)).toContain('%');
    expect(fmt.ms(210.5)).toContain('ms');
    expect(fmt.money('0.0543', 'USD')).toContain('0.05');
  });
});

describe('toMetricDisplay', () => {
  it('真实零值映射为 value 而非 no-data', () => {
    const input: MetricDisplayInput = { kind: 'count', availability: 'AVAILABLE', reasonCode: null, value: 0 };
    const display = toMetricDisplay(input, fmt);
    expect(display.status).toBe('value');
    expect(display).toEqual({ status: 'value', text: fmt.number(0) });
  });

  it('真实计数与 token 使用 locale 数字格式', () => {
    const input: MetricDisplayInput = { kind: 'count', availability: 'AVAILABLE', reasonCode: null, value: 90000 };
    expect(toMetricDisplay(input, fmt)).toEqual({ status: 'value', text: fmt.number(90000) });
  });

  it('成功率 0-1 映射为百分比', () => {
    const input: MetricDisplayInput = { kind: 'ratio', availability: 'AVAILABLE', reasonCode: null, value: 0.9834 };
    expect(toMetricDisplay(input, fmt)).toEqual({ status: 'value', text: fmt.percent(0.9834) });
  });

  it('平均延迟映射为毫秒', () => {
    const input: MetricDisplayInput = { kind: 'average', availability: 'AVAILABLE', reasonCode: null, value: 210.5 };
    expect(toMetricDisplay(input, fmt)).toEqual({ status: 'value', text: fmt.ms(210.5) });
  });

  it('金额在有币种时进入货币格式化', () => {
    const input: MetricDisplayInput = { kind: 'money', availability: 'AVAILABLE', reasonCode: null, value: '0.0543', currency: 'USD' };
    expect(toMetricDisplay(input, fmt)).toEqual({ status: 'value', text: fmt.money('0.0543', 'USD') });
  });

  it('NO_DATA 映射为 no-data', () => {
    const input: MetricDisplayInput = { kind: 'average', availability: 'UNAVAILABLE', reasonCode: 'NO_DATA', value: null };
    expect(toMetricDisplay(input, fmt)).toEqual({ status: 'no-data' });
  });

  it('BASELINE_NOT_VERIFIED 映射为不可用并带原因', () => {
    const input: MetricDisplayInput = { kind: 'count', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED', value: null };
    expect(toMetricDisplay(input, fmt)).toEqual({ status: 'unavailable', reason: 'BASELINE_NOT_VERIFIED' });
  });

  it('SOURCE_FIELD_MISSING 映射为不可用并带原因', () => {
    const input: MetricDisplayInput = { kind: 'count', availability: 'UNAVAILABLE', reasonCode: 'SOURCE_FIELD_MISSING', value: null };
    expect(toMetricDisplay(input, fmt)).toEqual({ status: 'unavailable', reason: 'SOURCE_FIELD_MISSING' });
  });

  it('金额不可用时不进入货币格式化', () => {
    const input: MetricDisplayInput = { kind: 'money', availability: 'UNAVAILABLE', reasonCode: 'NO_DATA', value: null };
    expect(toMetricDisplay(input, fmt)).toEqual({ status: 'no-data' });
  });
});

describe('toCollectionDisplay', () => {
  it('AVAILABLE 集合显示为 value', () => {
    expect(toCollectionDisplay('AVAILABLE', null)).toEqual({ status: 'value' });
  });

  it('PARTIAL 集合映射为 partial（仅最近请求）', () => {
    expect(toCollectionDisplay('PARTIAL', 'PARTIAL_SOURCE_COVERAGE')).toEqual({
      status: 'partial',
      reason: 'PARTIAL_SOURCE_COVERAGE',
    });
  });

  it('NO_DATA 集合映射为 no-data', () => {
    expect(toCollectionDisplay('UNAVAILABLE', 'NO_DATA')).toEqual({ status: 'no-data' });
  });

  it('其余不可用原因映射为 unavailable 并带原因', () => {
    expect(toCollectionDisplay('UNAVAILABLE', 'BASELINE_NOT_VERIFIED')).toEqual({
      status: 'unavailable',
      reason: 'BASELINE_NOT_VERIFIED',
    });
  });
});