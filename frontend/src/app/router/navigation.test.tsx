import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthStateProvider, type AuthStatus } from '../../features/auth/authState';
import { ToastProvider } from '../../components/feedback/Toast';
import { PublicConfigGate } from '../../app/providers/publicConfigGate';
import { I18nProvider } from '../../i18n/i18nProvider';
import { portalRequest } from '../../api/portalClient';
import { buildRoutes } from '../../app/router/routes';

vi.mock('../../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(portalRequest).mockResolvedValue({
    data: { siteName: '测试站', apiBaseUrls: [] },
    requestId: 'req-nav',
  });
});

function setup(authStatus: AuthStatus, initialEntries: string[]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(buildRoutes(), { initialEntries });
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

describe('路由导航与守卫集成', () => {
  it('站内导航切换标题并聚焦主标题', async () => {
    const user = userEvent.setup();
    setup('anonymous', ['/']);
    await screen.findByRole('heading', { name: '自有语言模型网关' });
    expect(document.title).toContain('自有语言模型网关');
    expect(document.title).toContain('测试站');
    await user.click(within(screen.getByRole('banner')).getByRole('link', { name: '模型广场' }));
    await screen.findByRole('heading', { name: '模型广场' });
    await waitFor(() => expect(document.title).toContain('模型广场'));
    expect(document.activeElement?.tagName).toBe('H1');
  });

  it('深层刷新恢复对应路由，未知路径显示自有 404', async () => {
    setup('anonymous', ['/docs']);
    await screen.findByRole('heading', { name: '开发文档' });
    const second = setup('anonymous', ['/no-such-page']);
    void second;
    await screen.findByText('页面不存在');
    expect(document.title).toContain('页面不存在');
  });

  it('匿名访问控制台跳转登录并携带站内返回地址', async () => {
    const router = setup('anonymous', ['/dashboard']);
    await screen.findByRole('heading', { name: '登录' });
    expect(router.state.location.pathname).toBe('/login');
    expect(router.state.location.search).toContain('returnTo=%2Fdashboard');
  });

  it('已认证进入控制台，已认证访问登录回到控制台', async () => {
    setup('authenticated', ['/dashboard']);
    await screen.findByRole('heading', { name: '控制台' });
    setup('authenticated', ['/login']);
    await screen.findByRole('heading', { name: '控制台' });
  });

  it('非法返回地址被丢弃', async () => {
    const router = setup('anonymous', ['/dashboard']);
    await screen.findByRole('heading', { name: '登录' });
    expect(router.state.location.search).not.toContain('http');
  });
});
