import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { Route, Routes } from 'react-router';
import { portalRequest } from '../api/portalClient';
import { ConsoleLayout } from './ConsoleLayout';
import { renderApp } from '../test/renderApp';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(portalRequest).mockResolvedValue({
    data: { siteName: '测试站', apiBaseUrls: [] },
    requestId: 'req-console',
  });
});

function setup() {
  return renderApp(
    <Routes>
      <Route element={<ConsoleLayout />}>
        <Route path="/dashboard" element={<h1>控制台内容</h1>} />
      </Route>
    </Routes>,
    { authStatus: 'authenticated', initialEntries: ['/dashboard'] },
  );
}

describe('ConsoleLayout 控制台壳', () => {
  it('侧栏只展示已交付入口与页面标题', async () => {
    const { container } = setup();
    await screen.findByRole('heading', { name: '控制台内容' });
    expect(screen.getByRole('link', { name: '控制台' })).toBeInTheDocument();
    const text = container.textContent ?? '';
    expect(text).not.toMatch(/密钥|日志|钱包|用户管理/);
  });

  it('移动抽屉可开关', async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByRole('heading', { name: '控制台内容' });
    await user.click(screen.getByRole('button', { name: '菜单' }));
    await screen.findByRole('dialog');
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
  });
});
