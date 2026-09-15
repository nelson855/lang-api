import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthStateProvider } from '../../features/auth/authState';
import { ToastProvider } from '../../components/feedback/Toast';
import { PublicConfigGate } from '../../app/providers/publicConfigGate';
import { I18nProvider } from '../../i18n/i18nProvider';
import { portalRequest } from '../../api/portalClient';
import { buildRoutes } from './routes';

vi.mock('../../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

let supportedRegions: string[] = ['CN'];

beforeEach(() => {
  supportedRegions = ['CN'];
  vi.mocked(portalRequest).mockImplementation((path) => {
    if (path === '/portal/api/public-config') {
      return Promise.resolve({
        data: {
          siteName: '测试站',
          publicationMode: 'PREVIEW',
          siteUrl: '',
          supportUrl: '',
          supportedRegions,
          enabledLocales: ['zh-CN', 'en-US'],
          apiBaseUrls: [],
        },
        requestId: 'req-config',
      });
    }
    if (path === '/portal/api/legal/terms') {
      return Promise.resolve({
        data: { type: 'TERMS', title: '用户协议', contentHtml: '<p>第一条</p>', locale: 'zh-CN' },
        requestId: 'req-terms',
      });
    }
    if (path === '/portal/api/legal/privacy') {
      return Promise.resolve({
        data: { type: 'PRIVACY', title: '隐私政策', contentHtml: '<p>隐私第一条</p>', locale: 'zh-CN' },
        requestId: 'req-privacy',
      });
    }
    return Promise.resolve({
      data: {
        registrationEnabled: false,
        registrationDisabledReason: 'PREVIEW_MODE',
        emailVerificationEnabled: false,
        captchaEnabled: false,
      },
      requestId: 'req-options',
    });
  });
});

function setup(initialEntries: string[]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(buildRoutes(), { initialEntries });
  render(
    <I18nProvider initialLocale="zh-CN">
      <QueryClientProvider client={client}>
        <PublicConfigGate>
          <AuthStateProvider status="anonymous">
            <ToastProvider>
              <RouterProvider router={router} />
            </ToastProvider>
          </AuthStateProvider>
        </PublicConfigGate>
      </QueryClientProvider>
    </I18nProvider>,
  );
}

describe('公开法律与地区路由直接访问', () => {
  it('直接刷新 /terms 显示服务端用户协议', async () => {
    setup(['/terms']);
    expect(await screen.findByRole('heading', { name: '用户协议' })).toBeInTheDocument();
    expect(await screen.findByText('第一条')).toBeInTheDocument();
  });

  it('直接刷新 /privacy 显示服务端隐私政策', async () => {
    setup(['/privacy']);
    expect(await screen.findByRole('heading', { name: '隐私政策' })).toBeInTheDocument();
    expect(await screen.findByText('隐私第一条')).toBeInTheDocument();
  });

  it('直接刷新 /regions 显示后端地区名称与代码', async () => {
    setup(['/regions']);
    expect(await screen.findByText('中国')).toBeInTheDocument();
    expect(await screen.findByText('CN')).toBeInTheDocument();
  });

  it('空地区不填充推测数据', async () => {
    supportedRegions = [];
    setup(['/regions']);
    expect(await screen.findByText('服务地区暂未公布。')).toBeInTheDocument();
    expect(screen.queryByText('中国')).not.toBeInTheDocument();
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });
});
