import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PortalApiError } from '../api/envelope';
import { portalRequest } from '../api/portalClient';
import { renderApp } from '../test/renderApp';
import { ApiKeysPage } from './ApiKeysPage';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

vi.mock('../api/apiKeys', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/apiKeys')>();
  return {
    ...actual,
    listApiKeys: vi.fn(),
    setApiKeyStatus: vi.fn(),
    deleteApiKey: vi.fn(),
  };
});

vi.mock('../features/auth/authState', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../features/auth/authState')>();
  return {
    ...actual,
    useAuthProfile: () => ({ id: 42, username: 'ordinary', displayName: 'Ord', email: null }),
  };
});

const apiKeys = await import('../api/apiKeys');
const listMock = vi.mocked(apiKeys.listApiKeys);
const statusMock = vi.mocked(apiKeys.setApiKeyStatus);
const deleteMock = vi.mocked(apiKeys.deleteApiKey);

function row(name: string, status: 'enabled' | 'disabled' | 'expired' = 'enabled', id = 7) {
  return {
    id,
    name,
    maskedKey: 'sk-fN95**********CMHQ',
    status,
    createdAt: '2026-09-12T02:40:07Z',
    expiresAt: null,
    quota: { unlimited: false, remaining: 100, unit: 'quota' as const },
    usedQuota: { value: 0, unit: 'quota' as const },
    modelRestrictions: { enabled: false, models: [] as string[] },
    allowedIps: [] as string[],
  };
}

describe('API Key 启停与删除确认', () => {
  beforeEach(() => {
    vi.mocked(portalRequest).mockResolvedValue({
      data: { siteName: '测试站', apiBaseUrls: [] },
      requestId: 'req-pages',
    });
    listMock.mockResolvedValue({
      data: { items: [row('alpha'), row('old', 'expired', 8)], page: 1, pageSize: 20, total: 2 },
      requestId: 'req-1',
    });
    statusMock.mockResolvedValue({ data: { enabled: false }, requestId: 'req-2' });
    deleteMock.mockResolvedValue({ data: { deleted: true as const }, requestId: 'req-3' });
  });

  it('停用需确认并显示风险与名称，提交期间防重复', async () => {
    const user = userEvent.setup();
    renderApp(<ApiKeysPage />, { authStatus: 'authenticated' });
    await screen.findByText('alpha');

    await user.click(screen.getByRole('button', { name: '停用 alpha' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog.contains(document.activeElement)).toBe(true);
    expect(within(dialog).getByText(/立即失效/)).toBeInTheDocument();
    expect(within(dialog).getByText('alpha')).toBeInTheDocument();

    const confirm = within(dialog).getByRole('button', { name: '确认停用' });
    await user.click(confirm);
    await user.click(confirm);
    await waitFor(() => {
      expect(statusMock).toHaveBeenCalledWith(7, false);
    });
    expect(statusMock).toHaveBeenCalledTimes(1);
  });

  it('过期密钥不提供虚假启用', async () => {
    const user = userEvent.setup();
    renderApp(<ApiKeysPage />, { authStatus: 'authenticated' });
    await screen.findByText('old');

    expect(screen.queryByRole('button', { name: '启用 old' })).not.toBeInTheDocument();
    // Select 弹层未打开时选项不渲染；状态徽标本身已表达"已过期"
    expect(screen.getAllByText('已过期').length).toBeGreaterThanOrEqual(1);
    await user.click(screen.getByRole('button', { name: '停用 alpha' }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
  });

  it('删除确认含名称，失败给刷新引导', async () => {
    const user = userEvent.setup();
    renderApp(<ApiKeysPage />, { authStatus: 'authenticated' });
    await screen.findByText('alpha');

    await user.click(screen.getByRole('button', { name: '删除 alpha' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('alpha')).toBeInTheDocument();
    expect(within(dialog).getByText(/不可恢复/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: '确认删除' }));
    await waitFor(() => {
      expect(deleteMock).toHaveBeenCalledWith(7);
    });

    deleteMock.mockRejectedValueOnce(
      new PortalApiError(502, 'OPERATION_RESULT_UNKNOWN', '结果未知', 'req-del-unknown'),
    );
    await user.click(screen.getByRole('button', { name: '删除 alpha' }));
    const retryDialog = await screen.findByRole('dialog');
    await user.click(within(retryDialog).getByRole('button', { name: '确认删除' }));
    expect(await within(retryDialog).findByText(/req-del-unknown/)).toBeInTheDocument();
  });
});
