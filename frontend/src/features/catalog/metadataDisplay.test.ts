import { describe, expect, it } from 'vitest';
import {
  describeCapability,
  describeModalities,
  describeOptionalNumber,
  describeOptionalText,
  describeReleaseDate,
  describeTags,
  truncateForDisplay,
} from './metadataDisplay';

describe('能力三态展示', () => {
  it('true 显示支持', () => {
    expect(describeCapability(true, { supported: '支持', unsupported: '不支持', unknown: '暂无数据' })).toBe(
      '支持',
    );
  });

  it('false 显示不支持，不与 null 合并', () => {
    expect(describeCapability(false, { supported: '支持', unsupported: '不支持', unknown: '暂无数据' })).toBe(
      '不支持',
    );
  });

  it('null 显示暂无数据', () => {
    expect(describeCapability(null, { supported: '支持', unsupported: '不支持', unknown: '暂无数据' })).toBe(
      '暂无数据',
    );
  });
});

describe('模态集合展示', () => {
  const labels = { TEXT: '文本', IMAGE: '图片', AUDIO: '音频', VIDEO: '视频', FILE: '文件' };

  it('null 显示暂无数据', () => {
    expect(describeModalities(null, labels)).toEqual({ kind: 'unknown', text: '暂无数据' });
  });

  it('已验证空数组显示无', () => {
    expect(describeModalities([], labels)).toEqual({ kind: 'empty', text: '无' });
  });

  it('按接口顺序本地化展示', () => {
    expect(describeModalities(['IMAGE', 'TEXT'], labels)).toEqual({
      kind: 'list',
      text: '图片、文本',
    });
  });
});

describe('可选文本展示', () => {
  it('null 与空字符串都视为暂无数据', () => {
    expect(describeOptionalText(null, '暂无数据')).toEqual({ kind: 'unknown', text: '暂无数据' });
    expect(describeOptionalText('', '暂无数据')).toEqual({ kind: 'unknown', text: '暂无数据' });
  });

  it('零值数字保留为零而不是暂无数据', () => {
    expect(describeOptionalNumber(0, '暂无数据')).toEqual({ kind: 'value', text: '0' });
    expect(describeOptionalNumber(null, '暂无数据')).toEqual({ kind: 'unknown', text: '暂无数据' });
    expect(describeOptionalNumber(8192, '暂无数据')).toEqual({ kind: 'value', text: '8192' });
  });

  it('已验证空标签数组显示无标签', () => {
    expect(describeTags([], '暂无数据', '无标签')).toEqual({ kind: 'empty', text: '无标签' });
    expect(describeTags(null, '暂无数据', '无标签')).toEqual({ kind: 'unknown', text: '暂无数据' });
    expect(describeTags(['a', 'b'], '暂无数据', '无标签')).toEqual({ kind: 'list', text: 'a、b' });
  });

  it('长文本保持完整值', () => {
    const long = 'x'.repeat(600);
    expect(describeOptionalText(long, '暂无数据')).toEqual({ kind: 'value', text: long });
  });
});

describe('发布日期本地化', () => {
  it('按日历日期展示，不因时区转换偏移一天', () => {
    expect(describeReleaseDate('2026-01-31', 'en-US')).toEqual({ kind: 'value', text: '2026-01-31' });
    expect(describeReleaseDate('2026-01-01', 'zh-CN')).toEqual({ kind: 'value', text: '2026-01-01' });
  });

  it('null 显示暂无数据', () => {
    expect(describeReleaseDate(null, 'zh-CN')).toEqual({ kind: 'unknown', text: '暂无数据' });
  });
});

describe('长文本截断', () => {
  it('长值在页面截断但保留完整文本属性', () => {
    const long = 'y'.repeat(400);
    const result = truncateForDisplay(long, 80);
    expect(result.display.length).toBeLessThanOrEqual(81);
    expect(result.full).toBe(long);
    expect(result.truncated).toBe(true);
  });

  it('短值不截断', () => {
    expect(truncateForDisplay('short', 80)).toEqual({ display: 'short', full: 'short', truncated: false });
  });
});
