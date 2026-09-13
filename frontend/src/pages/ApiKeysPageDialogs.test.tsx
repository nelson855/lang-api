import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
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
    createApiKey: vi.fn(),
    updateApiKey: vi.fn(),
    revealApiKey: vi.fn(),
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
const createMock = vi.mocked(apiKeys.createApiKey);
const updateMock = vi.mocked(apiKeys.updateApiKey);
const revealMock = vi.mocked(apiKeys.revealApiKey);

function row(name: string, id = 7) {
  return {
    id,
    name,
    maskedKey: 'sk-fN95**********CMHQ',
    status: 'enabled' as const,
    createdAt: '2026-09-12T02:40:07Z',
    expiresAt: null,
    quota: { unlimited: false, remaining: 100, unit: 'quota' as const },
    usedQuota: { value: 0, unit: 'quota' as const },
    modelRestrictions: { enabled: false, models: [] as string[] },
    allowedIps: [] as string[],
  };
}

describe('API Key 创建与编辑对话', () => {
  beforeEach(() => {
    vi.mocked(portalRequest).mockResolvedValue({
      data: { siteName: '测试站', apiBaseUrls: [] },
      requestId: 'req-pages',
    });
    listMock.mockResolvedValue({
      data: { items: [row('alpha')], page: 1, pageSize: 20, total: 1 },
      requestId: 'req-1',
    });
    createMock.mockResolvedValue({ data: { created: true as const }, requestId: 'req-2' });
    updateMock.mockResolvedValue({ data: { updated: true as const }, requestId: 'req-3' });
  });

  it('创建成功清除筛选回第一页并只提示一次', async () => {
    const user = userEvent.setup();
    renderApp(<ApiKeysPage />, { authStatus: 'authenticated' });
    await screen.findByText('alpha');

    await user.click(screen.getByRole('button', { name: '新建密钥' }));
    await user.type(await screen.findByLabelText('名称', { exact: false }), 'beta');
    await user.clear(screen.getByLabelText('剩余额度', { exact: false }));
    await user.type(screen.getByLabelText('剩余额度', { exact: false }), '50');
    await user.click(screen.getByRole('button', { name: /^新建$/ }));

    await waitFor(() => {
      expect(createMock).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'beta', remaining: 50 }),
      );
    });
    expect(await screen.findByText('创建成功')).toBeInTheDocument();
    expect(listMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1 }),
      expect.anything(),
    );

    await user.click(screen.getByRole('button', { name: '知道了' }));
    await waitFor(() => {
      expect(screen.queryByText('创建成功')).not.toBeInTheDocument();
    });
  });

  it('编辑预填并提交补丁', async () => {
    const user = userEvent.setup();
    renderApp(<ApiKeysPage />, { authStatus: 'authenticated' });
    await screen.findByText('alpha');

    await user.click(screen.getByRole('button', { name: '编辑 alpha' }));
    const nameInput = await screen.findByLabelText('名称', { exact: false });
    expect(nameInput).toHaveValue('alpha');
    await user.clear(nameInput);
    await user.type(nameInput, 'alpha-2');
    await user.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => {
      expect(updateMock).toHaveBeenCalledWith(7, expect.objectContaining({ name: 'alpha-2' }));
    });
  });

  it('行内取回打开确认对话', async () => {
    const user = userEvent.setup();
    revealMock.mockResolvedValue({ data: { secret: 'sk-abc' }, requestId: 'req-9' });
    renderApp(<ApiKeysPage />, { authStatus: 'authenticated' });
    await screen.findByText('alpha');

    await user.click(screen.getByRole('button', { name: '显示并复制 alpha' }));
    await user.click(await screen.findByRole('button', { name: '显示明文' }));
    expect(await screen.findByDisplayValue('sk-abc')).toBeInTheDocument();
    expect(revealMock).toHaveBeenCalledWith(7);
  });
});
