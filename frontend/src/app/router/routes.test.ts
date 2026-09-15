import { describe, expect, it } from 'vitest';
import { ROUTE_META, getPublicNavItems } from './routes';
import { zhCN } from '../../i18n/resources/zhCN';

function resolveKey(tree: Record<string, unknown>, path: string): unknown {
  return path.split('.').reduce<unknown>((node, part) => {
    if (typeof node === 'object' && node !== null) {
      return (node as Record<string, unknown>)[part];
    }
    return undefined;
  }, tree);
}

describe('集中路由元数据', () => {
  it('固定首批路由的布局、访问级别与标题键', () => {
    const byId = new Map(ROUTE_META.map((meta) => [meta.id, meta]));
    expect(byId.get('home')).toMatchObject({ path: '/', layout: 'public', access: 'public' });
    expect(byId.get('models')).toMatchObject({ path: '/models', layout: 'public', access: 'public' });
    expect(byId.get('docs')).toMatchObject({ path: '/docs', layout: 'public', access: 'public' });
    expect(byId.get('terms')).toMatchObject({ path: '/terms', layout: 'public', access: 'public' });
    expect(byId.get('privacy')).toMatchObject({ path: '/privacy', layout: 'public', access: 'public' });
    expect(byId.get('regions')).toMatchObject({ path: '/regions', layout: 'public', access: 'public' });
    expect(byId.get('login')).toMatchObject({ path: '/login', layout: 'auth', access: 'public-only' });
    expect(byId.get('register')).toMatchObject({ path: '/register', layout: 'auth', access: 'public-only' });
    expect(byId.get('dashboard')).toMatchObject({ path: '/dashboard', layout: 'console', access: 'protected' });
    expect(byId.get('notFound')).toMatchObject({ path: '*', layout: 'public', access: 'public' });
  });

  it('标题键均存在于中文资源且路径唯一', () => {
    const paths = ROUTE_META.map((meta) => meta.path);
    expect(new Set(paths).size).toBe(paths.length);
    for (const meta of ROUTE_META) {
      expect(typeof resolveKey(zhCN, meta.titleKey), meta.titleKey).toBe('string');
      if (meta.descriptionKey) {
        expect(typeof resolveKey(zhCN, meta.descriptionKey), meta.descriptionKey).toBe('string');
      }
    }
  });

  it('公开导航只含已交付入口且顺序稳定', () => {
    expect(getPublicNavItems().map((item) => item.id)).toEqual(['home', 'models', 'docs', 'regions']);
  });
});
