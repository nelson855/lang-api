import { describe, expect, it, beforeEach } from 'vitest';
import {
  DEFAULT_LOCALE,
  detectInitialLocale,
  persistLocale,
  resolveLocale,
} from './locale';

describe('初始语言识别', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('已保存的选择优先于浏览器语言', () => {
    expect(resolveLocale('zh-CN', ['en-US'])).toBe('zh-CN');
    expect(resolveLocale('en-US', ['zh-CN'])).toBe('en-US');
  });

  it('非法保存值回退到浏览器语言', () => {
    expect(resolveLocale('fr-FR', ['en-US'])).toBe('en-US');
    expect(resolveLocale('', ['zh-TW'])).toBe('zh-CN');
  });

  it('无保存值时按浏览器首个支持语言映射', () => {
    expect(resolveLocale(null, ['en-US', 'zh-CN'])).toBe('en-US');
    expect(resolveLocale(null, ['zh-TW', 'en-US'])).toBe('zh-CN');
    expect(resolveLocale(null, ['en-GB'])).toBe('en-US');
  });

  it('都不支持时回退中文默认', () => {
    expect(resolveLocale(null, ['fr-FR'])).toBe('zh-CN');
    expect(resolveLocale(null, [])).toBe('zh-CN');
    expect(DEFAULT_LOCALE).toBe('zh-CN');
  });

  it('持久化后下次启动仍使用保存值', () => {
    persistLocale('en-US');
    expect(detectInitialLocale(['zh-CN'])).toBe('en-US');
  });
});
