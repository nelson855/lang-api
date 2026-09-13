import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PortalApiError } from '../../api/envelope';
import { portalRequest } from '../../api/portalClient';
import { renderApp } from '../../test/renderApp';
import { REVEAL_TTL_MS, RevealDialog } from './RevealDialog';
import type { ApiKey } from '../../api/apiKeys';

vi.mock('../../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

vi.mock('../../api/apiKeys', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/apiKeys')>();
  return { ...actual, revealApiKey: vi.fn() };
});

const apiKeys = await import('../../api/apiKeys');
const revealMock = vi.mocked(apiKeys.revealApiKey);

const KEY: ApiKey = {
  id: 7,
  name: 'alpha',
  maskedKey: 'sk-fN95**********CMHQ',
  status: 'enabled',
  createdAt: '2026-09-12T02:40:07Z',
  expiresAt: null,
  quota: { unlimited: false, remaining: 100, unit: 'quota' },
  usedQuota: { value: 0, unit: 'quota' },
  modelRestrictions: { enabled: false, models: [] },
  allowedIps: [],
};

function setup(ttlMs?: number) {
  const onClose = vi.fn();
  renderApp(<RevealDialog apiKey={KEY} onClose={onClose} ttlMs={ttlMs} />);
  return onClose;
}

describe('API Key 取回与复制', () => {
  beforeEach(() => {
    vi.mocked(portalRequest).mockResolvedValue({
      data: { siteName: '测试站', apiBaseUrls: [] },
      requestId: 'req-pages',
    });
    revealMock.mockReset();
    revealMock.mockResolvedValue({ data: { secret: 'sk-abc' }, requestId: 'req-9' });
  });

  it('默认 60 秒清除且确认前不发请求', () => {
    expect(REVEAL_TTL_MS).toBe(60_000);
    setup();
    return screen.findByText(/可消耗其额度/).then(() => {
      expect(revealMock).not.toHaveBeenCalled();
    });
  });

  it('复制成功立即清除', async () => {
    const user = userEvent.setup();
    const clipboard = { writeText: vi.fn().mockResolvedValue(undefined) };
    vi.stubGlobal('navigator', { clipboard });
    try {
      setup();
      await user.click(await screen.findByRole('button', { name: '显示明文' }));
      expect(await screen.findByDisplayValue('sk-abc')).toBeInTheDocument();

      await user.click(screen.getByRole('button', { name: '复制' }));
      expect(clipboard.writeText).toHaveBeenCalledWith('sk-abc');
      await waitFor(() => {
        expect(screen.queryByDisplayValue('sk-abc')).not.toBeInTheDocument();
      });
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it('剪贴板失败允许手工复制', async () => {
    const user = userEvent.setup();
    const clipboard = { writeText: vi.fn().mockRejectedValue(new Error('denied')) };
    vi.stubGlobal('navigator', { clipboard });
    try {
      setup();
      await user.click(await screen.findByRole('button', { name: '显示明文' }));
      await screen.findByDisplayValue('sk-abc');

      await user.click(screen.getByRole('button', { name: '复制' }));
      expect(await screen.findByText(/复制失败/)).toBeInTheDocument();
      expect(screen.getByDisplayValue('sk-abc')).toBeInTheDocument();
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it('超时自动清除', async () => {
    const user = userEvent.setup();
    setup(30);
    await user.click(await screen.findByRole('button', { name: '显示明文' }));
    await screen.findByDisplayValue('sk-abc');

    await waitFor(
      () => {
        expect(screen.queryByDisplayValue('sk-abc')).not.toBeInTheDocument();
      },
      { timeout: 2000 },
    );
  });

  it('会话失效关闭对话框', async () => {
    const user = userEvent.setup();
    revealMock.mockRejectedValueOnce(new PortalApiError(401, 'UNAUTHENTICATED', '登录过期', 'req-10'));
    const onClose = setup();
    await user.click(await screen.findByRole('button', { name: '显示明文' }));
    await waitFor(() => {
      expect(onClose).toHaveBeenCalled();
    });
  });
});
