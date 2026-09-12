import { describe, expect, it } from 'vitest';
import { resolveReturnTo } from './returnTo';

describe('登录返回地址校验', () => {
  it('接受站内相对路径', () => {
    expect(resolveReturnTo('/dashboard')).toBe('/dashboard');
    expect(resolveReturnTo('/models?page=1')).toBe('/models?page=1');
  });

  it('丢弃绝对与协议相对地址', () => {
    expect(resolveReturnTo('https://evil.example.com/steal')).toBe('/dashboard');
    expect(resolveReturnTo('//evil.example.com/portal')).toBe('/dashboard');
  });

  it('丢弃反斜线、穿越与恶意编码', () => {
    expect(resolveReturnTo('/\\evil.com')).toBe('/dashboard');
    expect(resolveReturnTo('/dashboard/../admin')).toBe('/dashboard');
    expect(resolveReturnTo('/%2e%2e/admin')).toBe('/dashboard');
  });

  it('空值与非法输入回退默认页', () => {
    expect(resolveReturnTo(null)).toBe('/dashboard');
    expect(resolveReturnTo('')).toBe('/dashboard');
    expect(resolveReturnTo('/api/models')).toBe('/dashboard');
  });
});
