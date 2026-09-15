import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Route, Routes } from 'react-router';
import { renderWithLocale } from '../test/render';
import { RegisterPage } from './RegisterPage';

const authMocks = vi.hoisted(() => ({
  fetchAuthOptions: vi.fn(),
  register: vi.fn(),
}));

vi.mock('../api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/auth')>()),
  fetchAuthOptions: authMocks.fetchAuthOptions,
  register: authMocks.register,
}));

const openOptions = {
  data: { registrationEnabled: true, emailVerificationEnabled: false, captchaEnabled: false },
  requestId: 'req-options',
};

function setup() {
  return renderWithLocale(
    <Routes>
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/login" element={<p>登录页</p>} />
    </Routes>,
    'zh-CN',
    { initialEntries: ['/register'] },
  );
}

describe('RegisterPage', () => {
  beforeEach(() => {
    authMocks.fetchAuthOptions.mockReset();
    authMocks.register.mockReset();
  });

  it('等待 options 后只在策略开启时展示最小用户名密码表单', async () => {
    let resolveOptions: ((value: typeof openOptions) => void) | undefined;
    authMocks.fetchAuthOptions.mockReturnValue(new Promise((resolve) => { resolveOptions = resolve; }));
    const { container } = setup();
    expect(screen.getByRole('status')).toBeInTheDocument();
    resolveOptions?.(openOptions);
    expect(await screen.findByRole('textbox', { name: '用户名' })).toBeInTheDocument();
    expect(container.querySelectorAll('input[type="password"]')).toHaveLength(2);
    expect(screen.queryByLabelText(/邮箱|验证码|邀请码/)).toBeNull();
  });

  it('策略关闭时显示管理员预建账号说明且不展示表单', async () => {
    authMocks.fetchAuthOptions.mockResolvedValue({
      data: { ...openOptions.data, registrationEnabled: false },
      requestId: 'req-options',
    });
    setup();
    expect(await screen.findByText('当前环境未开放注册，请联系管理员预建账号。')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: '用户名' })).toBeNull();
  });

  it('成功注册后引导到登录页', async () => {
    const user = userEvent.setup();
    authMocks.fetchAuthOptions.mockResolvedValue(openOptions);
    authMocks.register.mockResolvedValue({ data: null, requestId: 'req-register' });
    setup();
    await user.type(await screen.findByRole('textbox', { name: '用户名' }), 'nelson');
    await user.type(screen.getByLabelText('密码'), 'password-1');
    await user.type(screen.getByLabelText('确认密码'), 'password-1');
    await user.click(screen.getByRole('button', { name: '注册' }));
    expect(await screen.findByText('登录页')).toBeInTheDocument();
    expect(authMocks.register).toHaveBeenCalledWith(
      { username: 'nelson', password: 'password-1', confirmPassword: 'password-1' },
      expect.anything(),
    );
  });

  it('策略错误只显示通用请求失败文案', async () => {
    const user = userEvent.setup();
    authMocks.fetchAuthOptions.mockResolvedValue(openOptions);
    authMocks.register.mockRejectedValueOnce(new Error('upstream registration policy detail'));
    setup();
    await user.type(await screen.findByRole('textbox', { name: '用户名' }), 'nelson');
    await user.type(screen.getByLabelText('密码'), 'password-1');
    await user.type(screen.getByLabelText('确认密码'), 'password-1');
    await user.click(screen.getByRole('button', { name: '注册' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('请求失败，请稍后重试。');
    expect(screen.queryByText('upstream registration policy detail')).toBeNull();
  });

  it('三种关闭原因显示准确说明且始终提供法律入口', async () => {
    const cases = [
      { reason: 'ADMIN_DISABLED', text: '当前环境未开放注册，请联系管理员预建账号。' },
      { reason: 'PREVIEW_MODE', text: '当前为预览版本，暂未开放注册。' },
      { reason: 'LEGAL_UNAVAILABLE', text: '法律正文尚未就绪，暂未开放注册。' },
    ] as const;
    for (const { reason, text } of cases) {
      authMocks.fetchAuthOptions.mockResolvedValue({
        data: { ...openOptions.data, registrationEnabled: false, registrationDisabledReason: reason },
        requestId: 'req-options',
      });
      const { unmount } = setup();
      expect(await screen.findByText(text)).toBeInTheDocument();
      expect(screen.getByRole('link', { name: '用户协议' })).toHaveAttribute('href', '/terms');
      expect(screen.getByRole('link', { name: '隐私政策' })).toHaveAttribute('href', '/privacy');
      expect(screen.queryByRole('textbox', { name: '用户名' })).toBeNull();
      unmount();
    }
  });

  it('加载与开放状态始终提供法律入口', async () => {
    let resolveOptions: ((value: typeof openOptions) => void) | undefined;
    authMocks.fetchAuthOptions.mockReturnValue(new Promise((resolve) => { resolveOptions = resolve; }));
    const first = setup();
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '用户协议' })).toHaveAttribute('href', '/terms');
    first.unmount();
    authMocks.fetchAuthOptions.mockResolvedValue(openOptions);
    setup();
    expect(await screen.findByRole('textbox', { name: '用户名' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '用户协议' })).toHaveAttribute('href', '/terms');
    expect(screen.getByRole('link', { name: '隐私政策' })).toHaveAttribute('href', '/privacy');
    resolveOptions?.(openOptions);
  });
});
