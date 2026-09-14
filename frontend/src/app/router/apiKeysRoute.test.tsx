import { screen, waitFor } from '@testing-library/react';
import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ROUTE_META } from './routeMeta';
import { zhCN } from '../../i18n/resources/zhCN';
import { buildRoutes } from './routes';
import { I18nProvider } from '../../i18n/i18nProvider';
import { AuthStateProvider, type AuthStatus } from '../../features/auth/authState';
import { ToastProvider } from '../../components/feedback/Toast';
import { PublicConfigGate } from '../providers/publicConfigGate';
import { portalRequest } from '../../api/portalClient';

vi.mock('../../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

vi.mock('../../api/apiKeys', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/apiKeys')>();
  return { ...actual, listApiKeys: vi.fn() };
});

vi.mock('../../features/auth/authState', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../features/auth/authState')>();
  return {
    ...actual,
    useAuthProfile: () => ({ id: 42, username: 'ordinary', displayName: 'Ord', email: null }),
  };
});

const { listApiKeys } = await import('../../api/apiKeys');
const listMock = vi.mocked(listApiKeys);

function setup(authStatus: AuthStatus, path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  vi.mocked(portalRequest).mockResolvedValue({
    data: { siteName: '测试站', apiBaseUrls: [] },
    requestId: 'req-nav',
  });
  listMock.mockResolvedValue({
    data: { items: [], page: 1, pageSize: 20, total: 0 },
    requestId: 'req-1',
  });
  const router = createMemoryRouter(buildRoutes(), { initialEntries: [path] });
  render(
    <I18nProvider initialLocale="zh-CN">
      <QueryClientProvider client={client}>
        <PublicConfigGate>
          <AuthStateProvider status={authStatus}>
            <ToastProvider>
              <RouterProvider router={router} />
            </ToastProvider>
          </AuthStateProvider>
        </PublicConfigGate>
      </QueryClientProvider>
    </I18nProvider>,
  );
  return router;
}

describe('API Key 路由与导航', () => {
  it('路由元数据落在控制台守卫下且标题键存在', () => {
    const meta = ROUTE_META.find((item) => item.id === 'apiKeys');
    expect(meta).toMatchObject({
      path: '/dashboard/api-keys',
      layout: 'console',
      access: 'protected',
      titleKey: 'pages.apiKeys.title',
    });
    expect(typeof (zhCN.pages as Record<string, unknown>)['apiKeys']).toBe('object');
  });

  it('控制台导航含密钥入口且公开导航不受影响', async () => {
    const { getConsoleNavItems, getPublicNavItems } = await import('./routes');
    expect(getConsoleNavItems().map((item) => item.id)).toEqual(['apiKeys', 'requestLogs', 'wallet', 'settings']);
    expect(getPublicNavItems().map((item) => item.id)).toEqual(['home', 'models', 'docs']);
  });

  it('登录后可见标题与高亮导航', async () => {
    setup('authenticated', '/dashboard/api-keys');
    // 懒加载页面在受限构建环境下解析较慢，放宽等待避免误杀。
    await screen.findByRole('heading', { name: 'API 密钥' }, { timeout: 10_000 });
    const nav = screen.getByRole('link', { name: 'API 密钥' });
    expect(nav).toHaveAttribute('href', '/dashboard/api-keys');
    expect(nav).toHaveAttribute('aria-current', 'page');
  });

  it('未登录带安全回跳且不泄露上游路径', async () => {
    const router = setup('anonymous', '/dashboard/api-keys');
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/login');
    });
    const search = router.state.location.search;
    expect(search).toContain(`returnTo=${encodeURIComponent('/dashboard/api-keys')}`);
    expect(search).not.toContain('/api/token');
  });
});
