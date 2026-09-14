import { describe, expect, it } from 'vitest';
import { buildRoutes, ROUTE_META, getConsoleNavItems } from './routes';
import { zhCN } from '../../i18n/resources/zhCN';
import { enUS } from '../../i18n/resources/enUS';

function resolveKey(tree: Record<string, unknown>, path: string): unknown {
  return path.split('.').reduce<unknown>((node, part) => {
    if (typeof node === 'object' && node !== null) {
      return (node as Record<string, unknown>)[part];
    }
    return undefined;
  }, tree);
}

describe('请求日志控制台路由', () => {
  it('元数据含受保护的请求日志入口', () => {
    const byId = new Map(ROUTE_META.map((meta) => [meta.id, meta]));
    expect(byId.get('requestLogs')).toMatchObject({
      path: '/dashboard/request-logs',
      layout: 'console',
      access: 'protected',
    });
  });

  it('标题键在中英文资源中均存在', () => {
    const meta = ROUTE_META.find((item) => item.id === 'requestLogs');
    expect(meta).toBeDefined();
    expect(typeof resolveKey(zhCN, meta!.titleKey)).toBe('string');
    expect(typeof resolveKey(enUS, meta!.titleKey)).toBe('string');
    expect(typeof resolveKey(zhCN, meta!.navKey!)).toBe('string');
    expect(typeof resolveKey(enUS, meta!.navKey!)).toBe('string');
  });

  it('控制台导航含请求日志且顶层旧路径未知', () => {
    expect(getConsoleNavItems().map((item) => item.id)).toContain('requestLogs');
    expect(ROUTE_META.find((meta) => meta.path === '/request-logs')).toBeUndefined();
    expect(ROUTE_META.find((meta) => meta.path === '/usage')).toBeUndefined();
  });

  it('路由树在控制台下挂载请求日志页', () => {
    const routes = buildRoutes();
    const dashboard = routes.find((route) => route.path === 'dashboard');
    expect(dashboard).toBeDefined();
    const paths = (dashboard!.children ?? []).map((child) => child.path);
    expect(paths).toContain('request-logs');
  });
});
