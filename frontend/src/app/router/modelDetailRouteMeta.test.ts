import { describe, expect, it } from 'vitest';
import { ROUTE_META, getPublicNavItems, metaForPath } from './routeMeta';

describe('模型详情路由元信息', () => {
  it('详情路径匹配到独立元信息而不是未找到', () => {
    const meta = metaForPath('/models/YWJj');
    expect(meta.id).toBe('modelDetail');
    expect(meta.titleKey).toBe('pages.models.detail.title');
    expect(meta.access).toBe('public');
  });

  it('携带查询参数的详情路径同样匹配', () => {
    expect(metaForPath('/models/YS9i').id).toBe('modelDetail');
  });

  it('列表路径仍匹配模型广场', () => {
    expect(metaForPath('/models').id).toBe('models');
    expect(metaForPath('/models/').id).toBe('notFound');
  });

  it('多层路径不被当作合法详情', () => {
    expect(metaForPath('/models/YWJj/extra').id).toBe('notFound');
  });

  it('未知路径回落到未找到', () => {
    expect(metaForPath('/nowhere').id).toBe('notFound');
  });

  it('详情不新增顶级导航项', () => {
    expect(getPublicNavItems().map((item) => item.id)).toEqual(['home', 'models', 'docs', 'regions']);
    expect(ROUTE_META.find((meta) => meta.id === 'modelDetail')?.navKey).toBeUndefined();
  });
});
