import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastProvider } from '../components/feedback/Toast';
import { PublicConfigGate } from '../app/providers/publicConfigGate';
import { I18nProvider } from '../i18n/i18nProvider';
import { portalRequest } from '../api/portalClient';
import { DocsPage } from './DocsPage';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

const requestMock = vi.mocked(portalRequest);

let currentModels = [model('a-model'), model('b-model'), model('中文/模型 v1')];

function model(id: string) {
  return { id, displayName: null, provider: 'Example', availability: 'AVAILABLE', pricing: null };
}

function renderDocs(path: string) {
  const router = createMemoryRouter([{ path: '/docs', element: <DocsPage /> }], {
    initialEntries: [path],
  });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider initialLocale="zh-CN">
        <PublicConfigGate>
          <ToastProvider>
            <RouterProvider router={router} />
          </ToastProvider>
        </PublicConfigGate>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  requestMock.mockReset();
  requestMock.mockImplementation(async (path) => {
    if (path === '/portal/api/public-config') {
      return {
        data: {
          siteName: '测试站',
          publicationMode: 'PREVIEW',
          siteUrl: '',
          supportUrl: '',
          supportedRegions: ['CN'],
          enabledLocales: ['zh-CN', 'en-US'],
          apiBaseUrls: [{ protocol: 'OPENAI', url: '/gateway/v1/example' }],
        },
        requestId: 'req-config',
      } as never;
    }
    if (path === '/portal/api/models') {
      return { data: { pricingVersion: 'v1', models: currentModels }, requestId: 'req-list' } as never;
    }
    throw new Error(`未预期的路径 ${path}`);
  });
});

describe('文档显式模型参数', () => {
  it('指定有效模型时精确使用该模型生成示例', async () => {
    renderDocs('/docs?model=中文%2F模型%20v1');
    // 原始 ID 在示例中原样出现，证明精确匹配而非静默替换。
    await screen.findByRole('heading', { name: '非流式示例' });
    const blocks = [...document.querySelectorAll('.code-block code')].map((n) => n.textContent ?? '');
    expect(blocks.some((text) => text.includes('中文/模型 v1'))).toBe(true);
    expect(screen.queryByText('所选模型不可用')).not.toBeInTheDocument();
  });

  it('指定模型不在目录中时提示不可用并提供返回入口', async () => {
    renderDocs('/docs?model=gone-model');
    expect(await screen.findByText(/所选模型不可用/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '返回模型广场' })).toHaveAttribute('href', '/models');
    expect(screen.queryByRole('heading', { name: '非流式示例' })).not.toBeInTheDocument();
  });

  it('显式空参数不静默替换为默认模型', async () => {
    renderDocs('/docs?model=');
    expect(await screen.findByText(/模型参数不合法|所选模型不可用/)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '非流式示例' })).not.toBeInTheDocument();
  });

  it('超长模型参数被拒绝且不回显原文', async () => {
    const long = 'z'.repeat(200);
    renderDocs(`/docs?model=${long}`);
    expect(await screen.findByText(/模型参数不合法/)).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(long.slice(0, 40)))).not.toBeInTheDocument();
  });

  it('未指定模型时保留按 ID 排序的首个可用模型', async () => {
    currentModels = [model('z-model'), model('a-model')];
    renderDocs('/docs');
    expect((await screen.findAllByText(/a-model/)).length).toBeGreaterThan(0);
    expect(screen.queryAllByText(/z-model/)).toHaveLength(0);
    expect(screen.queryByText(/所选模型不可用/)).not.toBeInTheDocument();
  });

  it('目录为空时不生成可调用示例', async () => {
    currentModels = [];
    renderDocs('/docs');
    expect(await screen.findByText(/当前没有可用模型/)).toBeInTheDocument();
  });
});
