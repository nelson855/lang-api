import { screen, render } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { portalRequest } from '../api/portalClient';
import { App } from './App';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

function renderApp() {
  return render(<App initialLocale="zh-CN" />);
}

describe('应用入口装配', () => {
  beforeEach(() => {
    vi.mocked(portalRequest).mockReset();
  });

  it('启动成功后渲染首页', async () => {
    vi.mocked(portalRequest).mockResolvedValue({
      data: { siteName: 'Lang API', apiBaseUrls: [] },
      requestId: 'req-app',
    });
    renderApp();
    expect(await screen.findByRole('heading', { name: '自有语言模型网关' })).toBeInTheDocument();
  });

  it('配置失败时只显示启动错误而不渲染应用', async () => {
    vi.mocked(portalRequest).mockRejectedValue(new Error('挂了'));
    renderApp();
    await screen.findByText('启动失败');
    expect(screen.queryByRole('heading', { name: '自有语言模型网关' })).toBeNull();
  });

  it('卸载时清理干净不抛错', async () => {
    vi.mocked(portalRequest).mockResolvedValue({
      data: { siteName: 'Lang API', apiBaseUrls: [] },
      requestId: 'req-app-unmount',
    });
    const { unmount } = renderApp();
    await screen.findByRole('heading', { name: '自有语言模型网关' });
    expect(() => unmount()).not.toThrow();
  });

  it('首页无严重可访问性问题', async () => {
    vi.mocked(portalRequest).mockResolvedValue({
      data: { siteName: 'Lang API', apiBaseUrls: [] },
      requestId: 'req-app-axe',
    });
    const { container } = renderApp();
    await screen.findByRole('heading', { name: '自有语言模型网关' });
    expect(await axe(container)).toHaveNoViolations();
  });
});
