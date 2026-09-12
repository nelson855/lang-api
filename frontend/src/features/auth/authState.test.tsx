import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Route, Routes } from 'react-router';
import { renderWithLocale } from '../../test/render';
import {
  AuthStateProvider,
  DefaultAuthProvider,
  RequireAuth,
  type AuthStatus,
} from './authState';

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

  it('默认 provider 为匿名且不读伪造会话', () => {
    localStorage.setItem('lang-api:token', 'fake');
    setupDefault('/dashboard?auth=1');
    expect(screen.getByText('登录页')).toBeInTheDocument();
  });
});
