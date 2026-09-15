// api-boundary-exempt: 页面路由测试需要构造含绝对地址的真实公开配置载荷
import { screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { portalRequest } from '../api/portalClient';
import { renderApp } from '../test/renderApp';
import { DashboardPage } from './DashboardPage';
import { DocsPage } from './DocsPage';
import { HomePage } from './HomePage';
import { LoginPage } from './LoginPage';
import { ModelsPage } from './ModelsPage';
import { NotFoundPage } from './NotFoundPage';
import { RegisterPage } from './RegisterPage';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

const FAKE_MODELS = ['GPT-', '余额', '已登录', '调用成功', '¥'];

beforeEach(() => {
  vi.mocked(portalRequest).mockImplementation(async (path: string) => {
    if (path.includes('/portal/api/models')) {
      return { data: { pricingVersion: null, models: [] }, requestId: 'req-pages' };
    }
    if (path.includes('/portal/api/account/balance')) {
      return { data: { quota: '500000', amount: '1.0', currency: 'USD' }, requestId: 'req-pages' };
    }
    if (path.includes('/portal/api/usage/summary')) {
      return {
        data: { quota: '0', amount: '0.0', currency: 'USD', rpm: 0, tpm: 0, rateWindowSeconds: 60 },
        requestId: 'req-pages',
      };
    }
    if (path.includes('/portal/api/usage/timeseries')) {
      return { data: { granularity: 'HOUR', points: [] }, requestId: 'req-pages' };
    }
    return {
      data: { siteName: '测试站', apiBaseUrls: [] },
      requestId: 'req-pages',
    };
  });
});

describe('首页真实骨架', () => {
  it('展示运行时站名与真实阶段入口', async () => {
    const { container } = renderApp(<HomePage />);
    expect(await screen.findByText('测试站', { exact: false })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /模型广场/ })).toHaveAttribute('href', '/models');
    expect(screen.getByRole('link', { name: /开发文档/ })).toHaveAttribute('href', '/docs');
    for (const fake of FAKE_MODELS) {
      expect(container.textContent).not.toContain(fake);
    }
  });

  it('预览且无公开协议时如实表达不可用状态且无阶段占位', async () => {
    vi.mocked(portalRequest).mockImplementation(async () => ({
      data: {
        siteName: '测试站',
        publicationMode: 'PREVIEW',
        siteUrl: '',
        supportUrl: '',
        supportedRegions: [],
        enabledLocales: ['zh-CN'],
        apiBaseUrls: [],
      },
      requestId: 'req-pages',
    }));
    const { container } = renderApp(<HomePage />);
    expect(await screen.findByText(/预览/)).toBeInTheDocument();
    expect(screen.getByText(/模型服务尚未开放/)).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/按阶段接入/);
    expect(screen.getByRole('link', { name: /用户协议/ })).toHaveAttribute('href', '/terms');
    expect(screen.getByRole('link', { name: /隐私政策/ })).toHaveAttribute('href', '/privacy');
  });

  it('正式且有公开协议时不展示预览与不可用说明', async () => {
    vi.mocked(portalRequest).mockImplementation(async () => ({
      data: {
        siteName: '测试站',
        publicationMode: 'PUBLIC',
        siteUrl: 'https://portal.example',
        supportUrl: 'mailto:support@portal.example',
        supportedRegions: ['CN'],
        enabledLocales: ['zh-CN'],
        apiBaseUrls: [{ protocol: 'OPENAI', url: 'https://api.example.com/v1' }],
      },
      requestId: 'req-pages',
    }));
    const { container } = renderApp(<HomePage />);
    await screen.findByText('测试站', { exact: false });
    expect(container.textContent).not.toMatch(/预览/);
    expect(container.textContent).not.toMatch(/模型服务尚未开放/);
    expect(screen.getByRole('link', { name: /模型广场/ })).toHaveAttribute('href', '/models');
  });
});

describe('阶段占位页诚实表达', () => {
  it('模型与文档页为空状态且无伪造数据', async () => {
    const { container, unmount } = renderApp(<ModelsPage />);
    await screen.findByText(/尚未配置公开模型/);
    for (const fake of FAKE_MODELS) {
      expect(container.textContent).not.toContain(fake);
    }
    unmount();
    renderApp(<DocsPage />);
    await screen.findByText(/鉴权/);
  });

  it('登录注册页提供最小用户名密码表单且不展示未实现能力', async () => {
    const { container, unmount } = renderApp(<LoginPage />);
    expect(await screen.findByRole('textbox', { name: /用户名/ })).toBeInTheDocument();
    expect(container.querySelector('input[type="password"]')).not.toBeNull();
    expect(container.textContent).not.toContain('OAuth');
    unmount();
    const second = renderApp(<RegisterPage />);
    expect(await screen.findByRole('textbox', { name: /用户名/ })).toBeInTheDocument();
    expect(second.container.querySelectorAll('input[type="password"]')).toHaveLength(2);
  });

  it('控制台页展示真实余额与基础用量区域', async () => {
    const { container } = renderApp(<DashboardPage />, {
      authStatus: 'authenticated',
      authProfile: { id: 42, username: 'ordinary', displayName: 'Ordinary', email: null },
    });
    await screen.findByText(/当前余额/);
    expect(await screen.findByText(/区间消费/)).toBeInTheDocument();
    expect(await screen.findByText(/小时趋势/)).toBeInTheDocument();
    for (const fake of FAKE_MODELS.filter((item) => item !== '余额')) {
      expect(container.textContent).not.toContain(fake);
    }
  });

  it('未知页提供返回首页入口', async () => {
    renderApp(<NotFoundPage />);
    expect(await screen.findByRole('link', { name: /返回首页/ })).toHaveAttribute('href', '/');
  });

  it('未知页提供有效支持入口且不泄漏错误信息', async () => {
    vi.mocked(portalRequest).mockImplementation(async () => ({
      data: {
        siteName: '测试站',
        publicationMode: 'PREVIEW',
        siteUrl: '',
        supportUrl: 'mailto:support@portal.example',
        supportedRegions: [],
        enabledLocales: ['zh-CN'],
        apiBaseUrls: [],
      },
      requestId: 'req-pages',
    }));
    const { container } = renderApp(<NotFoundPage />);
    expect(await screen.findByRole('link', { name: /返回首页/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /联系支持/ })).toHaveAttribute(
      'href',
      'mailto:support@portal.example',
    );
    expect(container.textContent).not.toMatch(/requestId|req-|404 Not Found|Error:/);
  });
});
