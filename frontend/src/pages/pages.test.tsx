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

const FAKE_MODELS = ['GPT-', '价格', '余额', '已登录', '调用成功', '$', '¥'];

beforeEach(() => {
  vi.mocked(portalRequest).mockResolvedValue({
    data: { siteName: '测试站', apiBaseUrls: [] },
    requestId: 'req-pages',
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
});

describe('阶段占位页诚实表达', () => {
  it('模型与文档页为空状态且无伪造数据', async () => {
    const { container, unmount } = renderApp(<ModelsPage />);
    await screen.findByText(/模型数据尚未接入/);
    for (const fake of FAKE_MODELS) {
      expect(container.textContent).not.toContain(fake);
    }
    unmount();
    renderApp(<DocsPage />);
    await screen.findByText(/后续阶段接入/);
  });

  it('登录注册页无表单且不宣称成功', async () => {
    const { container, unmount } = renderApp(<LoginPage />);
    await screen.findByText(/尚未开放/);
    expect(container.querySelector('form')).toBeNull();
    expect(container.textContent).not.toContain('登录成功');
    unmount();
    const second = renderApp(<RegisterPage />);
    await screen.findByText(/暂不开放/);
    expect(second.container.querySelector('form')).toBeNull();
  });

  it('控制台页为真实占位', async () => {
    const { container } = renderApp(<DashboardPage />, { authStatus: 'authenticated' });
    await screen.findByText(/后续阶段接入/);
    for (const fake of FAKE_MODELS) {
      expect(container.textContent).not.toContain(fake);
    }
  });

  it('未知页提供返回首页入口', async () => {
    renderApp(<NotFoundPage />);
    expect(await screen.findByRole('link', { name: /返回首页/ })).toHaveAttribute('href', '/');
  });
});
