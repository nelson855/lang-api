import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { portalRequest } from '../api/portalClient';
import { renderApp } from '../test/renderApp';
import { ApiKeysPage } from './ApiKeysPage';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

vi.mock('../api/apiKeys', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/apiKeys')>();
  return { ...actual, listApiKeys: vi.fn() };
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

function row(name: string, status: 'enabled' | 'disabled' | 'expired' | 'exhausted' = 'enabled') {
  return {
    id: 7,
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

describe('API Key 页面无障碍与布局', () => {
  beforeEach(() => {
    vi.mocked(portalRequest).mockResolvedValue({
      data: { siteName: '测试站', apiBaseUrls: [] },
      requestId: 'req-pages',
    });
    listMock.mockResolvedValue({
      data: { items: [row('alpha'), row('old', 'expired')], page: 1, pageSize: 20, total: 2 },
      requestId: 'req-1',
    });
  });

  it('列表无 axe 违规且状态图标文字并用', async () => {
    const { container } = renderApp(<ApiKeysPage />, { authStatus: 'authenticated' });
    await screen.findByText('alpha');
    expect(await axe(container)).toHaveNoViolations();

    const badges = screen.getAllByText('启用');
    expect(badges.length).toBeGreaterThan(0);
    for (const badge of badges) {
      const wrapper = badge.closest('.status-badge');
      expect(wrapper?.querySelector('svg[aria-hidden="true"]')).not.toBeNull();
    }
  });

  it('键盘可达主要操作且不依赖悬停', async () => {
    const user = userEvent.setup();
    renderApp(<ApiKeysPage />, { authStatus: 'authenticated' });
    await screen.findByText('alpha');

    await user.tab();
    expect(document.activeElement?.tagName).toBe('INPUT');
    await user.tab();
    expect(document.activeElement?.tagName).toBe('SELECT');
    for (const name of ['新建密钥', '编辑 alpha', '停用 alpha', '删除 alpha', '显示并复制 alpha']) {
      expect(screen.getByRole('button', { name })).toBeVisible();
    }
  });

  it('减少动效与窄屏样式存在', () => {
    const pageCss = readFileSync(resolve(process.cwd(), 'src/pages/ApiKeysPage.css'), 'utf8');
    const badgeCss = readFileSync(
      resolve(process.cwd(), 'src/features/apiKeys/StatusBadge.css'),
      'utf8',
    );
    expect(pageCss).toContain('@media (max-width: 640px)');
    expect(pageCss).toContain('prefers-reduced-motion');
    expect(badgeCss).toContain('prefers-reduced-motion');
  });
});
