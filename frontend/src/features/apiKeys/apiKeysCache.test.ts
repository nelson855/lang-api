import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';
import {
  apiKeysListKey,
  fallbackPageAfterDelete,
  invalidateApiKeysScope,
  normalizeApiKeysParams,
  withFirstPage,
} from './apiKeysCache';

describe('API Key 查询键与缓存边界', () => {
  it('按用户与规范化条件隔离', () => {
    const base = { page: 1, pageSize: 20, name: 'probe', status: 'enabled' };
    expect(apiKeysListKey(1, base)).toEqual(['portal', 'api-keys', 1, 1, 20, 'probe', 'enabled']);
    expect(apiKeysListKey(2, base)).not.toEqual(apiKeysListKey(1, base));
    expect(apiKeysListKey(1, { ...base, page: 2 })).not.toEqual(apiKeysListKey(1, base));
  });

  it('名称去空格与状态归一化', () => {
    expect(normalizeApiKeysParams({ page: 1, pageSize: 20, name: '  Probe ', status: 'Enabled' }))
      .toEqual({ page: 1, pageSize: 20, name: 'Probe', status: 'enabled' });
    expect(normalizeApiKeysParams({ page: 1, pageSize: 20 }))
      .toEqual({ page: 1, pageSize: 20, name: '', status: '' });
  });

  it('条件变化回到第一页', () => {
    expect(withFirstPage({ page: 3, pageSize: 20, name: 'a', status: '' }))
      .toEqual({ page: 1, pageSize: 20, name: 'a', status: '' });
  });

  it('失效只清当前用户范围', () => {
    const client = new QueryClient();
    client.setQueryData(['portal', 'api-keys', 1, 1, 20, '', ''], { items: [] });
    client.setQueryData(['portal', 'api-keys', 2, 1, 20, '', ''], { items: [] });

    invalidateApiKeysScope(client, 1);

    expect(client.getQueryData(['portal', 'api-keys', 1, 1, 20, '', ''])).toBeUndefined();
    expect(client.getQueryData(['portal', 'api-keys', 2, 1, 20, '', ''])).toEqual({ items: [] });
  });

  it('删除页最后一项后页码回退', () => {
    expect(fallbackPageAfterDelete(3, 20, 40)).toBe(2);
    expect(fallbackPageAfterDelete(3, 20, 41)).toBe(3);
    expect(fallbackPageAfterDelete(1, 20, 0)).toBe(1);
    expect(fallbackPageAfterDelete(2, 20, 20)).toBe(1);
  });
});
