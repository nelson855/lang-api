import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { portalRequest } from '../api/portalClient';
import { renderApp } from '../test/renderApp';
import { SettingsPage } from './SettingsPage';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

vi.mock('../api/wallet', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/wallet')>();
  return { ...actual, updateProfile: vi.fn() };
});

const profile = { id: 42, username: 'ordinary', displayName: 'Ordinary', email: 'a@example.test' };

describe('个人设置表单', () => {
  beforeEach(() => {
    vi.mocked(portalRequest).mockReset();
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-s' };
      }
      throw new Error(`unexpected request: ${path}`);
    });
  });

  it('以当前资料预填，邮箱只读且无手机号输入', async () => {
    renderApp(<SettingsPage />, { authStatus: 'authenticated', authProfile: profile });

    expect(await screen.findByRole('heading', { name: '个人设置' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: '基础资料' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: '修改密码' })).toBeInTheDocument();
    expect(screen.getByLabelText(/用户名/)).toHaveValue('ordinary');
    expect(screen.getByLabelText(/显示名/)).toHaveValue('Ordinary');
    const email = screen.getByLabelText(/邮箱/);
    expect(email).toHaveValue('a@example.test');
    expect(email).toHaveAttribute('readonly');
    expect(screen.queryByLabelText(/手机|电话/)).toBeNull();
  });

  it('当前密码缺失时不提交', async () => {
    const user = userEvent.setup();
    renderApp(<SettingsPage />, { authStatus: 'authenticated', authProfile: profile });

    await user.clear(await screen.findByLabelText(/当前密码/));
    await user.click(screen.getByRole('button', { name: '保存修改' }));

    expect(await screen.findByText(/当前密码.*必填|必填.*当前密码|请填写当前密码/)).toBeInTheDocument();
  });

  it('新密码与确认不一致时不提交', async () => {
    const user = userEvent.setup();
    renderApp(<SettingsPage />, { authStatus: 'authenticated', authProfile: profile });

    await user.type(await screen.findByLabelText(/当前密码/), 'correct-123');
    await user.type(screen.getByLabelText(/^新密码/), 'brand-new-123');
    await user.type(screen.getByLabelText(/确认新密码/), 'different-123');
    await user.click(screen.getByRole('button', { name: '保存修改' }));

    expect(await screen.findByText(/两次.*不一致|不一致/)).toBeInTheDocument();
  });

  it('用户名过短时客户端直接拦截', async () => {
    const user = userEvent.setup();
    renderApp(<SettingsPage />, { authStatus: 'authenticated', authProfile: profile });

    const username = await screen.findByLabelText(/用户名/);
    await user.clear(username);
    await user.type(username, 'ab');
    await user.type(screen.getByLabelText(/当前密码/), 'correct-123');
    await user.click(screen.getByRole('button', { name: '保存修改' }));

    expect(await screen.findByText(/用户名.*3|至少 3/)).toBeInTheDocument();
  });
});

describe('个人设置提交交互', () => {
  beforeEach(async () => {
    vi.mocked(portalRequest).mockReset();
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-s' };
      }
      throw new Error(`unexpected request: ${path}`);
    });
    const { updateProfile } = await import('../api/wallet');
    vi.mocked(updateProfile).mockReset();
  });

  it('成功后清空密码并显示成功反馈', async () => {
    const { updateProfile } = await import('../api/wallet');
    vi.mocked(updateProfile).mockResolvedValueOnce({
      data: { id: 42, username: 'newname', displayName: 'New Name', email: 'a@example.test' },
      requestId: 'req-ok',
    });
    const user = userEvent.setup();
    renderApp(<SettingsPage />, { authStatus: 'authenticated', authProfile: profile });

    await user.type(await screen.findByLabelText(/当前密码/), 'correct-123');
    await user.click(screen.getByRole('button', { name: '保存修改' }));

    expect(await screen.findByText(/资料已更新/)).toBeInTheDocument();
    expect(screen.getByLabelText(/当前密码/)).toHaveValue('');
  });

  it('提交中禁用按钮防止重复提交', async () => {
    const { updateProfile } = await import('../api/wallet');
    let resolveUpdate!: (value: unknown) => void;
    vi.mocked(updateProfile).mockReturnValueOnce(
      new Promise((resolve) => {
        resolveUpdate = resolve as (value: unknown) => void;
      }) as ReturnType<typeof updateProfile>,
    );
    const user = userEvent.setup();
    renderApp(<SettingsPage />, { authStatus: 'authenticated', authProfile: profile });

    await user.type(await screen.findByLabelText(/当前密码/), 'correct-123');
    await user.click(screen.getByRole('button', { name: '保存修改' }));

    expect(screen.getByRole('button', { name: '提交中…' })).toBeDisabled();
    resolveUpdate({
      data: { id: 42, username: 'ordinary', displayName: 'Ordinary', email: 'a@example.test' },
      requestId: 'req-ok',
    });
    expect(await screen.findByText(/资料已更新/)).toBeInTheDocument();
    expect(vi.mocked(updateProfile)).toHaveBeenCalledTimes(1);
  });

  it('当前密码错误关联到密码框并清空密码', async () => {
    const { updateProfile } = await import('../api/wallet');
    const { PortalApiError } = await import('../api/envelope');
    vi.mocked(updateProfile).mockRejectedValueOnce(
      new PortalApiError(400, 'INVALID_ARGUMENT', 'bad', 'req-bad'),
    );
    const user = userEvent.setup();
    renderApp(<SettingsPage />, { authStatus: 'authenticated', authProfile: profile });

    await user.type(await screen.findByLabelText(/当前密码/), 'wrong-123');
    await user.click(screen.getByRole('button', { name: '保存修改' }));

    expect(await screen.findByText(/当前密码不正确/)).toBeInTheDocument();
    expect(screen.getByLabelText(/当前密码/)).toHaveValue('');
  });

  it('用户名冲突关联到用户名框并保留输入', async () => {
    const { updateProfile } = await import('../api/wallet');
    const { PortalApiError } = await import('../api/envelope');
    vi.mocked(updateProfile).mockRejectedValueOnce(
      new PortalApiError(409, 'RESOURCE_CONFLICT', 'taken', 'req-taken'),
    );
    const user = userEvent.setup();
    renderApp(<SettingsPage />, { authStatus: 'authenticated', authProfile: profile });

    const username = await screen.findByLabelText(/用户名/);
    await user.clear(username);
    await user.type(username, 'taken');
    await user.type(screen.getByLabelText(/当前密码/), 'correct-123');
    await user.click(screen.getByRole('button', { name: '保存修改' }));

    expect(await screen.findByText(/用户名已被占用/)).toBeInTheDocument();
    expect(screen.getByLabelText(/用户名/)).toHaveValue('taken');
    expect(screen.getByLabelText(/当前密码/)).toHaveValue('');
  });

  it('结果未确认时提示重读并带回请求标识', async () => {
    const { updateProfile } = await import('../api/wallet');
    const { PortalApiError } = await import('../api/envelope');
    vi.mocked(updateProfile).mockRejectedValueOnce(
      new PortalApiError(502, 'OPERATION_RESULT_UNKNOWN', 'unknown', 'req-unknown'),
    );
    const user = userEvent.setup();
    renderApp(<SettingsPage />, { authStatus: 'authenticated', authProfile: profile });

    await user.type(await screen.findByLabelText(/当前密码/), 'correct-123');
    await user.click(screen.getByRole('button', { name: '保存修改' }));

    expect(await screen.findByText(/结果未确认/)).toBeInTheDocument();
    expect(await screen.findByText(/req-unknown/)).toBeInTheDocument();
    expect(vi.mocked(updateProfile)).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole('button', { name: '重新读取资料' }));
    expect(vi.mocked(updateProfile)).toHaveBeenCalledTimes(1);
  });

  it('英文环境展示对应标题与分区', async () => {
    renderApp(<SettingsPage />, { authStatus: 'authenticated', authProfile: profile, locale: 'en-US' });

    expect(await screen.findByRole('heading', { name: 'Settings' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Profile' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Change password' })).toBeInTheDocument();
  });
});
