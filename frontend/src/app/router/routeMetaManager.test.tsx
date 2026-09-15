import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthStateProvider } from '../../features/auth/authState';
import { ToastProvider } from '../../components/feedback/Toast';
import { PublicConfigGate } from '../../app/providers/publicConfigGate';
import { I18nProvider } from '../../i18n/i18nProvider';
import { PortalApiError } from '../../api/envelope';
import { portalRequest } from '../../api/portalClient';
import { buildRoutes } from './routes';

vi.mock('../../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

let mode = 'PREVIEW';
let siteUrl = '';
let termsAvailable = true;

function configData() {
  return {
    siteName: '测试站',
    publicationMode: mode,
    siteUrl,
    supportUrl: '',
    supportedRegions: [],
    enabledLocales: ['zh-CN', 'en-US'],
    apiBaseUrls: [],
  };
}

beforeEach(() => {
  mode = 'PREVIEW';
  siteUrl = '';
  termsAvailable = true;
  document.head.querySelectorAll('title, meta[name="description"], meta[name="robots"], link[rel="canonical"]')
    .forEach((node) => node.remove());
  vi.mocked(portalRequest).mockImplementation((path) => {
    if (path === '/portal/api/public-config') {
      return Promise.resolve({ data: configData(), requestId: 'req-config' });
    }
    if (path === '/portal/api/legal/terms') {
      return termsAvailable
        ? Promise.resolve({
            data: { type: 'TERMS', title: '用户协议', contentHtml: '<p>第一条</p>', locale: 'zh-CN' },
            requestId: 'req-terms',
          })
        : Promise.reject(new PortalApiError(404, 'NOT_FOUND', '不存在', 'req-terms'));
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

function metaContent(name: string): string | null {
  return document.head.querySelector(`meta[name="${name}"]`)?.getAttribute('content') ?? null;
}

describe('运行时路由元信息管理', () => {
  it('路由与语言切换更新唯一标题与描述', async () => {
    setup(['/']);
    await screen.findByRole('heading', { name: '自有语言模型网关' });
    await waitFor(() => expect(document.title).toContain('自有语言模型网关'));
    const firstDescription = metaContent('description');
    expect(firstDescription).toBeTruthy();
    expect(document.head.querySelectorAll('title')).toHaveLength(1);
    expect(document.head.querySelectorAll('meta[name="description"]')).toHaveLength(1);

    fireEvent.click(screen.getByRole('button', { name: 'English' }));
    await screen.findByRole('heading', { name: 'Self-hosted language model gateway' });
    await waitFor(() => expect(document.title).toContain('Self-hosted language model gateway'));
    expect(document.head.querySelectorAll('title')).toHaveLength(1);
    expect(document.head.querySelectorAll('meta[name="description"]')).toHaveLength(1);
    expect(metaContent('description')).toBeTruthy();
    expect(metaContent('description')).not.toBe(firstDescription);
  });

  it('预览模式禁止索引且不生成 canonical', async () => {
    setup(['/models']);
    await screen.findByRole('heading', { name: '模型广场' });
    await waitFor(() => expect(metaContent('robots')).toBe('noindex,nofollow'));
    expect(document.head.querySelector('link[rel="canonical"]')).toBeNull();
  });

  it('正式有效页面允许索引并生成 canonical', async () => {
    mode = 'PUBLIC';
    siteUrl = 'https://portal.example';
    setup(['/models']);
    await screen.findByRole('heading', { name: '模型广场' });
    await waitFor(() => expect(metaContent('robots')).toBe('index,follow'));
    expect(document.head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://portal.example/models',
    );
  });

  it('未知路径与未发布法律页禁止索引', async () => {
    setup(['/no-such-page']);
    await screen.findByText('页面不存在');
    await waitFor(() => expect(metaContent('robots')).toBe('noindex,nofollow'));
    expect(document.head.querySelector('link[rel="canonical"]')).toBeNull();
  });

  it('未发布法律正文页禁止索引且不残留上一页标签', async () => {
    mode = 'PUBLIC';
    siteUrl = 'https://portal.example';
    termsAvailable = false;
    setup(['/models']);
    await screen.findByRole('heading', { name: '模型广场' });
    await waitFor(() => expect(metaContent('robots')).toBe('index,follow'));
    setup(['/terms']);
    await screen.findByText('法律正文尚未发布');
    await waitFor(() => expect(metaContent('robots')).toBe('noindex,nofollow'));
    expect(document.head.querySelector('link[rel="canonical"]')).toBeNull();
    expect(document.head.querySelectorAll('meta[name="robots"]')).toHaveLength(1);
  });
});
