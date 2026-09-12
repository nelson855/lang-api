import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
    requestId: 'req-drawer',
  });
});

function setup() {
  return renderApp(
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<h1>首页内容</h1>} />
        <Route path="models" element={<h1>模型内容</h1>} />
      </Route>
    </Routes>,
    { initialEntries: ['/'] },
  );
}

describe('移动导航抽屉', () => {
  it('打开后限制焦点，选中目标后关闭并落点新页面', async () => {
    const user = userEvent.setup();
    setup();
    await screen.findAllByText('测试站');
    await user.click(screen.getByRole('button', { name: '菜单' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog.contains(document.activeElement)).toBe(true);
    await user.click(within(dialog).getByRole('link', { name: '模型广场' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(await screen.findByRole('heading', { name: '模型内容' })).toBeInTheDocument();
    expect(document.activeElement?.tagName).toBe('H1');
  });

  it('Escape 关闭并返回菜单按钮', async () => {
    const user = userEvent.setup();
    setup();
    await screen.findAllByText('测试站');
    const menuButton = screen.getByRole('button', { name: '菜单' });
    await user.click(menuButton);
    await screen.findByRole('dialog');
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(menuButton).toHaveFocus();
  });
});
