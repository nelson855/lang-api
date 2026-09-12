import { describe, expect, it } from 'vitest';
import { createAppQueryClient } from './queryClient';

describe('应用级 Query 默认行为', () => {
  it('查询默认不自动重试、窗口聚焦不刷新', () => {
    const client = createAppQueryClient();
    const queryDefaults = client.getDefaultOptions().queries;
    expect(queryDefaults?.retry).toBe(false);
    expect(queryDefaults?.refetchOnWindowFocus).toBe(false);
  });

  it('mutation 默认不重放', () => {
    const client = createAppQueryClient();
    expect(client.getDefaultOptions().mutations?.retry).toBe(false);
  });

  it('每次创建返回独立实例', () => {
    expect(createAppQueryClient()).not.toBe(createAppQueryClient());
  });
});
