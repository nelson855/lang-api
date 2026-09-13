import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useQueryClient } from '@tanstack/react-query';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { Route, Routes, useLocation } from 'react-router';
import { PortalApiError } from '../api/envelope';
import { AUTH_PROFILE_QUERY_KEY } from '../api/auth';
import { AuthStateProvider, PublicOnlyRoute } from '../features/auth/authState';
import { renderWithLocale } from '../test/render';
import { LoginPage, loginErrorKind } from './LoginPage';

const authMocks = vi.hoisted(() => ({ login: vi.fn() }));

vi.mock('../api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/auth')>()),
  login: authMocks.login,
}));

const profile = {
  id: 7,
  username: 'nelson',
  displayName: 'Nelson',
  email: 'nelson@example.com',
};

function LocationProbe() {
  const location = useLocation();
  const queryClient = useQueryClient();
  const cached = queryClient.getQueryData(AUTH_PROFILE_QUERY_KEY);
  return <p>{`${location.pathname}${location.search}:${cached ? 'cached' : 'empty'}`}</p>;
}

function setup(path = '/login') {
  return renderWithLocale(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="*" element={<LocationProbe />} />
    </Routes>,
    'zh-CN',
    { initialEntries: [path] },
  );
}

describe('LoginPage', () => {
  beforeEach(() => authMocks.login.mockReset());

  it('未满足最小字段校验时不提交', async () => {
    const user = userEvent.setup();
    setup();
    await user.click(screen.getByRole('button', { name: '登录' }));
    expect(authMocks.login).not.toHaveBeenCalled();
  });

  it('提交期间禁用重复提交', async () => {
    const user = userEvent.setup();
    let finishLogin: ((value: { data: typeof profile; requestId: string }) => void) | undefined;
    authMocks.login.mockReturnValue(new Promise((resolve) => { finishLogin = resolve; }));
    setup();
    await user.type(screen.getByRole('textbox', { name: '用户名' }), 'nelson');
    await user.type(screen.getByLabelText('密码'), 'password-1');
    await user.click(screen.getByRole('button', { name: '登录' }));
    expect(screen.getByRole('button', { name: '提交中…' })).toBeDisabled();
    expect(authMocks.login).toHaveBeenCalledTimes(1);
    finishLogin?.({ data: profile, requestId: 'req-login' });
    expect(await screen.findByText('/dashboard:cached')).toBeInTheDocument();
  });

  it('将无效凭据归一为不含上游详情的安全错误类型', () => {
    expect(loginErrorKind(new PortalApiError(401, 'INVALID_CREDENTIALS', 'upstream detail'))).toBe('invalidCredentials');
    expect(loginErrorKind(new PortalApiError(503, 'UPSTREAM_ERROR', 'private upstream'))).toBe('requestFailed');
  });

  it('登录成功缓存最小 profile 并仅跳转合法 returnTo', async () => {
    const user = userEvent.setup();
    authMocks.login.mockResolvedValue({ data: profile, requestId: 'req-login' });
    setup('/login?returnTo=%2Fdocs%3Ffrom%3Dlogin');
    await user.type(screen.getByRole('textbox', { name: '用户名' }), 'nelson');
    await user.type(screen.getByLabelText('密码'), 'password-1');
    await user.click(screen.getByRole('button', { name: '登录' }));
    expect(await screen.findByText('/docs?from=login:cached')).toBeInTheDocument();
  });

  it('拒绝外部 returnTo 并将已登录用户从登录页重定向', async () => {
    const user = userEvent.setup();
    authMocks.login.mockResolvedValue({ data: profile, requestId: 'req-login' });
    setup('/login?returnTo=https%3A%2F%2Fevil.example');
    await user.type(screen.getByRole('textbox', { name: '用户名' }), 'nelson');
    await user.type(screen.getByLabelText('密码'), 'password-1');
    await user.click(screen.getByRole('button', { name: '登录' }));
    expect(await screen.findByText('/dashboard:cached')).toBeInTheDocument();

    renderWithLocale(
      <AuthStateProvider status="authenticated">
        <Routes>
          <Route path="/login" element={<PublicOnlyRoute><LoginPage /></PublicOnlyRoute>} />
          <Route path="/dashboard" element={<p>控制台</p>} />
        </Routes>
      </AuthStateProvider>,
      'zh-CN',
      { initialEntries: ['/login'] },
    );
    expect(await screen.findByText('控制台')).toBeInTheDocument();
  });
});
