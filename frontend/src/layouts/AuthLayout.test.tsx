import { screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { Route, Routes } from 'react-router';
import { portalRequest } from '../api/portalClient';
import { AuthLayout } from './AuthLayout';
import { renderApp } from '../test/renderApp';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(portalRequest).mockResolvedValue({
    data: { siteName: '测试站', apiBaseUrls: [] },
    requestId: 'req-auth-layout',
  });
});

describe('AuthLayout 认证壳', () => {
  it('聚焦账户操作且无虚假登录方式', async () => {
    const { container } = renderApp(
      <Routes>
        <Route element={<AuthLayout />}>
          <Route path="/login" element={<h1>登录</h1>} />
        </Route>
      </Routes>,
      { initialEntries: ['/login'] },
    );
    await screen.findByRole('heading', { name: '登录' });
    expect(screen.getByRole('link', { name: '测试站' })).toHaveAttribute('href', '/');
    expect(screen.getByRole('link', { name: '返回首页' })).toHaveAttribute('href', '/');
    const text = container.textContent ?? '';
    expect(text).not.toMatch(/微信|GitHub|OAuth|第三方|验证码/);
    expect(container.querySelector('form')).toBeNull();
  });
});
