import { screen, within } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { Route, Routes } from 'react-router';
import { portalRequest } from '../api/portalClient';
import { PublicLayout } from './PublicLayout';
import { renderApp } from '../test/renderApp';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(portalRequest).mockResolvedValue({
    data: { siteName: '测试站', apiBaseUrls: [] },
    requestId: 'req-layout',
  });
});

function setup(path = '/') {
  return renderApp(
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<h1>首页内容</h1>} />
        <Route path="models" element={<h1>模型内容</h1>} />
        <Route path="docs" element={<h1>文档内容</h1>} />
      </Route>
    </Routes>,
    { initialEntries: [path] },
  );
}

describe('PublicLayout 公开站点壳', () => {
  it('桌面导航展示主要目的地与当前选中', async () => {
    setup('/models');
    expect((await screen.findAllByText('测试站')).length).toBeGreaterThan(0);
    const header = screen.getByRole('banner');
    const modelsLink = within(header).getByRole('link', { name: '模型广场' });
    expect(modelsLink).toHaveAttribute('aria-current', 'page');
    expect(within(header).getByRole('link', { name: '首页' })).not.toHaveAttribute('aria-current');
    expect(within(header).getByRole('link', { name: '登录' })).toHaveAttribute('href', '/login');
    expect(within(header).getByRole('link', { name: '注册' })).toHaveAttribute('href', '/register');
  });

  it('页脚只含真实可用入口', async () => {
    const { container } = setup('/');
    await screen.findAllByText('测试站');
    const footer = container.querySelector('footer');
    expect(footer).not.toBeNull();
    const hrefs = [...(footer as HTMLElement).querySelectorAll('a')].map((a) =>
      a.getAttribute('href'),
    );
    expect(hrefs.length).toBeGreaterThan(0);
    for (const href of hrefs) {
      expect(href?.startsWith('/')).toBe(true);
    }
    expect(footer?.textContent).not.toMatch(/隐私|条款|privacy|terms/i);
  });

  it('提供语言切换器', async () => {
    setup('/');
    expect(await screen.findByRole('group', { name: '语言' })).toBeInTheDocument();
  });
});
