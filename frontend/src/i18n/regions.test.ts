import { describe, expect, it } from 'vitest';
import { regionName } from './regions';

describe('受控地区代码本地化映射', () => {
  it('把后端地区代码映射为当前语言名称', () => {
    expect(regionName('CN', 'zh-CN')).toBe('中国');
    expect(regionName('CN', 'en-US')).toBe('China');
  });

  it('大小写与空白不影响映射', () => {
    expect(regionName(' cn ', 'zh-CN')).toBe('中国');
  });

  it('未知代码直接展示代码本身而不编造名称', () => {
    expect(regionName('XX', 'zh-CN')).toBe('XX');
  });
});
