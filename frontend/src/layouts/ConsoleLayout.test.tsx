import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { Route, Routes } from 'react-router';
import { portalRequest } from '../api/portalClient';
import { ConsoleLayout } from './ConsoleLayout';
import { renderApp } from '../test/renderApp';

const authMocks = vi.hoisted(() => ({ logout: vi.fn() }));

vi.mock('../api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/auth')>()),
  logout: authMocks.logout,
}));

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(portalRequest).mockResolvedValue({
    data: { siteName: '测试站', apiBaseUrls: [] },
    requestId: 'req-console',
  });
  authMocks.logout.mockReset();
});

const profile = { id: 7, username: 'nelson', displayName: 'Nelson', email: 'nelson@example.com' };

function setup() {
  return renderApp(
    <Routes>
      <Route element={<ConsoleLayout />}>
        <Route path="/dashboard" element={<h1>控制台内容</h1>} />
      </Route>
      <Route path="/" element={<h1>匿名首页</h1>} />
    </Routes>,
    { authStatus: 'authenticated', authProfile: profile, initialEntries: ['/dashboard'] },
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

  it('展示最小用户信息，退出期间阻止重复提交，并在成功后清除旧内容', async () => {
    const user = userEvent.setup();
    let finishLogout: ((value: { data: null; requestId: string }) => void) | undefined;
    authMocks.logout.mockReturnValue(new Promise((resolve) => { finishLogout = resolve; }));
    setup();
    await screen.findByRole('heading', { name: '控制台内容' });
    expect(screen.getByText('Nelson')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '退出登录' }));
    expect(screen.getByRole('button', { name: '提交中…' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: '提交中…' }));
    expect(authMocks.logout).toHaveBeenCalledTimes(1);
    finishLogout?.({ data: null, requestId: 'req-logout' });
    expect(await screen.findByRole('heading', { name: '匿名首页' })).toBeInTheDocument();
    expect(screen.queryByText('控制台内容')).toBeNull();
  });

  it('上游撤销未确认时仍进入匿名状态', async () => {
    const user = userEvent.setup();
    authMocks.logout.mockRejectedValueOnce(new Error('revocation unconfirmed'));
    setup();
    await screen.findByRole('heading', { name: '控制台内容' });
    await user.click(screen.getByRole('button', { name: '退出登录' }));
    expect(await screen.findByRole('heading', { name: '匿名首页' })).toBeInTheDocument();
    expect(screen.queryByText('控制台内容')).toBeNull();
  });
});
