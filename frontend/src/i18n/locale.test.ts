import { describe, expect, it, beforeEach } from 'vitest';
import {
  DEFAULT_LOCALE,
  detectInitialLocale,
  isLocaleAllowed,
  normalizeEnabledLocales,
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

describe('运行时启用语言约束', () => {
  it('失效持久值在未启用时被忽略', () => {
    expect(resolveLocale('zh-CN', ['zh-CN'], ['en-US'])).toBe('en-US');
    expect(resolveLocale('en-US', ['en-US'], ['zh-CN'])).toBe('zh-CN');
  });

  it('浏览器偏好只映射到已启用语言', () => {
    expect(resolveLocale(null, ['en-US', 'zh-CN'], ['zh-CN'])).toBe('zh-CN');
    expect(resolveLocale(null, ['zh-CN'], ['en-US'])).toBe('en-US');
  });

  it('启用列表为空或全非法时回退中文默认', () => {
    expect(resolveLocale(null, ['en-US'], [])).toBe('zh-CN');
    expect(resolveLocale(null, ['en-US'], ['fr-FR'])).toBe('zh-CN');
  });

  it('启用列表规范化去重并保持配置顺序', () => {
    expect(normalizeEnabledLocales(['en-US', 'zh-CN', 'en-US', 'fr-FR', null])).toEqual([
      'en-US',
      'zh-CN',
    ]);
    expect(normalizeEnabledLocales([])).toEqual([]);
  });

  it('法律正文语言不在启用集合时判定不匹配', () => {
    expect(isLocaleAllowed('en-US', ['zh-CN'])).toBe(false);
    expect(isLocaleAllowed('zh-CN', ['zh-CN', 'en-US'])).toBe(true);
    expect(isLocaleAllowed('en-US', [])).toBe(false);
  });
});
