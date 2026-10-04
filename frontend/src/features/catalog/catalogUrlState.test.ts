import { describe, expect, it } from 'vitest';
import {
  buildCatalogSearch,
  parseCatalogConditions,
  resolveProviderCondition,
} from './catalogUrlState';

describe('目录 URL 条件解析', () => {
  it('读取 q 与 provider', () => {
    expect(parseCatalogConditions('?q=flash&provider=DeepSeek')).toEqual({
      search: 'flash',
      provider: 'DeepSeek',
    });
  });

  it('缺失参数给出空条件', () => {
    expect(parseCatalogConditions('')).toEqual({ search: '', provider: null });
    expect(parseCatalogConditions('?q=&provider=')).toEqual({ search: '', provider: null });
  });

  it('忽略首尾空白但保留厂商大小写与内部空格', () => {
    expect(parseCatalogConditions('?q=%20flash%20')).toEqual({ search: 'flash', provider: null });
    expect(parseCatalogConditions('?provider=Open%20AI')).toEqual({ search: '', provider: 'Open AI' });
  });

  it('重复参数取首个', () => {
    expect(parseCatalogConditions('?q=first&q=second')).toEqual({ search: 'first', provider: null });
    expect(parseCatalogConditions('?provider=A&provider=B')).toEqual({ search: '', provider: 'A' });
  });

  it('忽略未使用的参数，不参与筛选', () => {
    expect(parseCatalogConditions('?returnTo=%2Fadmin&sort=name')).toEqual({
      search: '',
      provider: null,
    });
  });

  it('解码特殊字符与中文', () => {
    expect(parseCatalogConditions(`?q=${encodeURIComponent('中文 & 空格')}`)).toEqual({
      search: '中文 & 空格',
      provider: null,
    });
  });
});

describe('目录 URL 条件序列化', () => {
  it('只序列化支持的条件', () => {
    expect(buildCatalogSearch({ search: 'flash', provider: 'DeepSeek' })).toBe('?q=flash&provider=DeepSeek');
  });

  it('省略空参数', () => {
    expect(buildCatalogSearch({ search: '', provider: null })).toBe('');
    expect(buildCatalogSearch({ search: '   ', provider: null })).toBe('');
    expect(buildCatalogSearch({ search: 'flash', provider: null })).toBe('?q=flash');
  });

  it('编码特殊字符值', () => {
    expect(buildCatalogSearch({ search: 'a&b=c', provider: '厂商 1' })).toBe(
      '?q=a%26b%3Dc&provider=%E5%8E%82%E5%95%86+1',
    );
  });

  it('往返保持条件一致', () => {
    const conditions = { search: '中文 空格', provider: 'Open AI' };
    expect(parseCatalogConditions(buildCatalogSearch(conditions))).toEqual(conditions);
  });
});

describe('厂商条件解析', () => {
  it('当前供应商目录中存在的厂商保留为选中', () => {
    const options = [
      { value: 'DeepSeek', label: 'DeepSeek', modelCount: 2 },
      { value: 'OpenAI', label: 'OpenAI', modelCount: 1 },
    ];
    expect(resolveProviderCondition('DeepSeek', options)).toEqual({
      status: 'known',
      value: 'DeepSeek',
    });
  });

  it('大小写不同视为不同厂商', () => {
    expect(resolveProviderCondition('deepseek', [{ value: 'DeepSeek', label: 'DeepSeek', modelCount: 1 }]).status).toBe(
      'unknown',
    );
  });

  it('未加载目录时保持条件，不静默清除', () => {
    expect(resolveProviderCondition('DeepSeek', null)).toEqual({
      status: 'pending',
      value: 'DeepSeek',
    });
  });

  it('已加载目录但不存在的厂商标记为未知，供清除', () => {
    expect(resolveProviderCondition('Gone', [])).toEqual({ status: 'unknown', value: 'Gone' });
    expect(resolveProviderCondition('Gone', [{ value: 'A', label: 'A', modelCount: 1 }])).toEqual({
      status: 'unknown',
      value: 'Gone',
    });
  });

  it('空条件不参与解析', () => {
    expect(resolveProviderCondition(null, [])).toEqual({ status: 'none', value: null });
  });
});
