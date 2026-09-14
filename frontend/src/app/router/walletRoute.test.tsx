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

vi.mock('../../api/usage', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/usage')>();
  return { ...actual, fetchBalance: vi.fn() };
});

vi.mock('../../api/wallet', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/wallet')>();
  return { ...actual, fetchTopupOptions: vi.fn(), fetchTopupPage: vi.fn() };
});

vi.mock('../../features/auth/authState', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../features/auth/authState')>();
  return {
    ...actual,
    useAuthProfile: () => ({ id: 42, username: 'ordinary', displayName: 'Ord', email: null }),
  };
});

const { fetchBalance } = await import('../../api/usage');
const { fetchTopupOptions, fetchTopupPage } = await import('../../api/wallet');

function setup(authStatus: AuthStatus, path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  vi.mocked(portalRequest).mockResolvedValue({
    data: { siteName: '测试站', apiBaseUrls: [] },
    requestId: 'req-nav',
  });
  vi.mocked(fetchBalance).mockResolvedValue({
    data: { quota: '1000000', amount: '2.0', currency: 'USD' },
    requestId: 'req-1',
  });
  vi.mocked(fetchTopupOptions).mockResolvedValue({
    data: { enabled: false, methods: [], currency: 'USD', reason: 'NOT_CONFIGURED' },
    requestId: 'req-1',
  });
  vi.mocked(fetchTopupPage).mockResolvedValue({
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

describe('钱包路由与导航', () => {
  it('路由元数据落在控制台守卫下且标题键存在', () => {
    const meta = ROUTE_META.find((item) => item.id === 'wallet');
    expect(meta).toMatchObject({
      path: '/dashboard/wallet',
      layout: 'console',
      access: 'protected',
      titleKey: 'pages.wallet.title',
    });
    expect(typeof (zhCN.pages as Record<string, unknown>)['wallet']).toBe('object');
  });

  it('控制台导航含钱包入口且公开导航不受影响', async () => {
    const { getConsoleNavItems, getPublicNavItems } = await import('./routes');
    expect(getConsoleNavItems().map((item) => item.id)).toEqual(['apiKeys', 'requestLogs', 'wallet', 'settings']);
    expect(getPublicNavItems().map((item) => item.id)).toEqual(['home', 'models', 'docs']);
  });

  it('登录后可见标题与高亮导航', async () => {
    setup('authenticated', '/dashboard/wallet');
    await screen.findByRole('heading', { name: '钱包' });
    const nav = screen.getByRole('link', { name: '钱包' });
    expect(nav).toHaveAttribute('href', '/dashboard/wallet');
    expect(nav).toHaveAttribute('aria-current', 'page');
    await waitFor(() => {
      expect(document.title).toContain('钱包');
    });
  });

  it('未登录带安全回跳且不泄露上游路径', async () => {
    const router = setup('anonymous', '/dashboard/wallet');
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/login');
    });
    const search = router.state.location.search;
    expect(search).toContain(`returnTo=${encodeURIComponent('/dashboard/wallet')}`);
    expect(search).not.toContain('/api/user/topup');
  });

  it('顶层旧式路径按未知路由处理', async () => {
    setup('authenticated', '/wallet');
    await screen.findByRole('heading', { name: '页面不存在' });
    expect(screen.queryByRole('heading', { name: '钱包' })).toBeNull();
  });
});
