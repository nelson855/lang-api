import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Route, Routes } from 'react-router';
import { PortalApiError } from '../../api/envelope';
import { portalRequest } from '../../api/portalClient';
import { renderWithLocale } from '../../test/render';
import {
  AuthStateProvider,
  DefaultAuthProvider,
  RequireAuth,
  type AuthStatus,
} from './authState';

vi.mock('../../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

const profile = {
  id: 7,
  username: 'nelson',
  displayName: 'Nelson',
  email: 'nelson@example.com',
};

function setup(status: AuthStatus, path = '/dashboard') {
  return renderWithLocale(
    <AuthStateProvider status={status}>
      <Routes>
        <Route path="/login" element={<p>登录页</p>} />
        <Route
          path="/dashboard"
          element={
            <RequireAuth>
              <p>受保护内容</p>
            </RequireAuth>
          }
        />
      </Routes>
    </AuthStateProvider>,
    'zh-CN',
    { initialEntries: [path] },
  );
}

function setupDefault(path: string) {
  return renderWithLocale(
    <DefaultAuthProvider>
      <Routes>
        <Route path="/login" element={<p>登录页</p>} />
        <Route
          path="/dashboard"
          element={
            <RequireAuth>
              <p>受保护内容</p>
            </RequireAuth>
          }
        />
      </Routes>
    </DefaultAuthProvider>,
    'zh-CN',
    { initialEntries: [path] },
  );
}

describe('鉴权路由守卫', () => {
  beforeEach(() => vi.mocked(portalRequest).mockReset());

  it('检查中显示加载且不闪现受保护内容', () => {
    setup('checking');
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText('受保护内容')).toBeNull();
  });

  it('匿名跳转登录并保留站内返回路径', () => {
    setup('anonymous');
    expect(screen.getByText('登录页')).toBeInTheDocument();
    expect(screen.queryByText('受保护内容')).toBeNull();
  });

  it('无权限显示 403', () => {
    setup('forbidden');
    expect(screen.getByText('无权限')).toBeInTheDocument();
  });

  it('已认证放行受保护内容', () => {
    setup('authenticated');
    expect(screen.getByText('受保护内容')).toBeInTheDocument();
  });

  it('默认 provider 在 profile 请求期间维持 checking 状态', async () => {
    let resolveProfile: ((value: { data: typeof profile; requestId: string }) => void) | undefined;
    vi.mocked(portalRequest).mockReturnValue(new Promise((resolve) => { resolveProfile = resolve; }));
    setupDefault('/dashboard');
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText('受保护内容')).toBeNull();
    resolveProfile?.({ data: profile, requestId: 'req-profile' });
    expect(await screen.findByText('受保护内容')).toBeInTheDocument();
  });

  it('默认 provider 在刷新页面后通过唯一 profile 查询恢复已认证状态', async () => {
    vi.mocked(portalRequest).mockResolvedValue({ data: profile, requestId: 'req-profile' });
    setupDefault('/dashboard');
    expect(await screen.findByText('受保护内容')).toBeInTheDocument();
    expect(portalRequest).toHaveBeenCalledWith('/portal/api/profile', expect.anything(), expect.anything());
  });

  it('默认 provider 仅根据 profile 接口判定匿名且不读伪造会话', async () => {
    vi.mocked(portalRequest).mockRejectedValueOnce(new PortalApiError(401, 'UNAUTHENTICATED', 'expired'));
    localStorage.setItem('lang-api:token', 'fake');
    setupDefault('/dashboard?auth=1');
    expect(await screen.findByText('登录页')).toBeInTheDocument();
  });

  it('profile 返回 403 时进入 forbidden 状态', async () => {
    vi.mocked(portalRequest).mockRejectedValueOnce(new PortalApiError(403, 'FORBIDDEN', 'denied'));
    setupDefault('/dashboard');
    expect(await screen.findByText('无权限')).toBeInTheDocument();
  });
});
