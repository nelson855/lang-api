import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { PortalApiError } from '../api/envelope';
import { portalRequest } from '../api/portalClient';
import { renderApp } from '../test/renderApp';
import { WalletPage } from './WalletPage';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

const profile = { id: 42, username: 'ordinary', displayName: 'Ordinary', email: null };

const LONG_REF = `req-${'0123456789abcdef'.repeat(12)}`;

function summaryOk() {
  return {
    data: {
      baselineVersion: 'p2-2026-09-22-a',
      range: { start: '2026-09-01T00:00:00Z', end: '2026-09-08T00:00:00Z', timezone: 'UTC', granularity: 'HOUR' },
      recordCount: { value: '3', unit: 'records', availability: 'AVAILABLE', reasonCode: null },
      quotaTotal: { value: '60', unit: 'quota', availability: 'AVAILABLE', reasonCode: null },
      moneyTotal: {
        value: null,
        currency: null,
        availability: 'UNAVAILABLE',
        reasonCode: 'CURRENCY_CONVERSION_NOT_VERIFIED',
      },
      coverage: { type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null },
    },
    requestId: 'req-w',
  };
}

function transactionsOk() {
  return {
    data: {
      baselineVersion: 'p2-2026-09-22-a',
      range: { start: '2026-09-01T00:00:00Z', end: '2026-09-08T00:00:00Z', timezone: 'UTC', granularity: 'HOUR' },
      type: null,
      page: 1,
      pageSize: 20,
      total: 21,
      availability: 'PARTIAL',
      reasonCode: null,
      coverage: [
        { type: 'TOPUP', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' },
        { type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null },
        { type: 'REFUND', availability: 'UNAVAILABLE', reasonCode: 'SOURCE_NOT_AVAILABLE' },
      ],
      items: [
        {
          transactionId: 'CONSUMPTION_abc123',
          occurredAt: '2026-09-01T12:00:00Z',
          type: 'CONSUMPTION',
          direction: 'DEBIT',
          amount: '60',
          unit: 'QUOTA',
          currency: null,
          status: 'SUCCEEDED',
          remark: null,
          referenceId: LONG_REF,
        },
      ],
    },
    requestId: 'req-w',
  };
}

describe('钱包页面可访问性', () => {
  beforeEach(() => {
    vi.mocked(portalRequest).mockClear();
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      const url = String(path);
      if (url.includes('/portal/api/public-config')) {
        return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-w' };
      }
      if (url.includes('/portal/api/account/balance')) {
        return { data: { quota: '500000', amount: '1.0', currency: 'USD' }, requestId: 'req-w' };
      }
      if (url.includes('/portal/api/account/consumption-summary')) {
        return summaryOk();
      }
      if (url.includes('/portal/api/account/transactions')) {
        return transactionsOk();
      }
      if (url.includes('/portal/api/account/topup-options')) {
        return {
          data: { enabled: false, methods: [], currency: 'USD', reason: 'NOT_CONFIGURED' },
          requestId: 'req-w',
        };
      }
      if (url.includes('/portal/api/account/topups')) {
        return { data: { items: [], page: 1, pageSize: 20, total: 0 }, requestId: 'req-w' };
      }
      throw new PortalApiError(502, 'UPSTREAM_ERROR', 'unexpected', 'req-w');
    });
  });

  it('标题层级唯一 h1,五个语义区域均为具名 region', async () => {
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });
    await screen.findAllByText('gpt-4o').catch(() => null);
    await screen.findByText('60');

    const h1 = screen.getAllByRole('heading', { level: 1 });
    expect(h1).toHaveLength(1);
    expect(h1[0]).toHaveTextContent('钱包');
    for (const name of ['余额', '范围内消费', '统一流水', '充值未开放', '充值记录']) {
      expect(screen.getByRole('region', { name })).toBeInTheDocument();
    }
    const h2 = screen.getAllByRole('heading', { level: 2 });
    expect(h2.map((h) => h.textContent)).toEqual([
      '余额',
      '范围内消费',
      '统一流水',
      '充值未开放',
      '充值记录',
    ]);
  });

  it('筛选与分页控件具名且可聚焦,分页具名导航', async () => {
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });
    await screen.findByText('60');

    expect(screen.getByRole('combobox', { name: '时间范围' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: '交易类型' })).toBeInTheDocument();
    const navs = screen.getAllByRole('navigation');
    expect(navs.length).toBeGreaterThan(0);
    for (const nav of navs) {
      expect(nav.getAttribute('aria-label')?.trim().length).toBeGreaterThan(0);
    }
  });

  it('加载、错误、覆盖说明使用 status/alert/note 分工', async () => {
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      const url = String(path);
      if (url.includes('/portal/api/public-config')) {
        return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-w' };
      }
      if (url.includes('/portal/api/account/consumption-summary')) {
        throw new PortalApiError(502, 'UPSTREAM_ERROR', 'up', 'req-w');
      }
      if (url.includes('/portal/api/account/balance')) {
        return { data: { quota: '500000', amount: '1.0', currency: 'USD' }, requestId: 'req-w' };
      }
      if (url.includes('/portal/api/account/transactions')) {
        return transactionsOk();
      }
      if (url.includes('/portal/api/account/topup-options')) {
        return {
          data: { enabled: false, methods: [], currency: 'USD', reason: 'NOT_CONFIGURED' },
          requestId: 'req-w',
        };
      }
      if (url.includes('/portal/api/account/topups')) {
        return { data: { items: [], page: 1, pageSize: 20, total: 0 }, requestId: 'req-w' };
      }
      throw new PortalApiError(502, 'UPSTREAM_ERROR', 'unexpected', 'req-w');
    });
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });

    const summary = await screen.findByLabelText(/范围内消费/);
    expect(await within(summary).findByRole('alert')).toBeInTheDocument();
    const ledger = await screen.findByLabelText(/统一流水/);
    const notes = await within(ledger).findAllByRole('note');
    expect(notes.length).toBeGreaterThanOrEqual(2);
  });

  it('键盘可聚焦筛选器并可操作分页,方向语义不只依赖颜色', async () => {
    const user = userEvent.setup();
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });
    await screen.findByText('60');

    const range = screen.getByRole('combobox', { name: '时间范围' });
    range.focus();
    expect(document.activeElement).toBe(range);
    const type = screen.getByRole('combobox', { name: '交易类型' });
    type.focus();
    expect(document.activeElement).toBe(type);

    const ledger = screen.getByLabelText(/统一流水/);
    const next = await within(ledger).findByRole('button', { name: '下一页' });
    next.focus();
    await user.keyboard('{Enter}');
    await screen.findByText(/第 2/);

    const directionCell = within(ledger).getAllByText('支出')[0].closest('td, p');
    expect(directionCell?.querySelector('span[aria-hidden="true"]')).not.toBeNull();
  });

  it('长来源引用完整渲染且可换行,无障碍树无违规', async () => {
    const { container } = renderApp(<WalletPage />, {
      authStatus: 'authenticated',
      authProfile: profile,
    });
    const ledger = await screen.findByLabelText(/统一流水/);
    await within(ledger).findByRole('table');
    // jsdom 会切分超长文本节点,用 textContent 断言完整引用仍在 DOM 中。
    expect(ledger.textContent).toContain(LONG_REF);
    const cards = ledger.querySelector('.wallet-cards');
    expect(cards?.textContent).toContain(LONG_REF);
    expect(await axe(container)).toHaveNoViolations();
  });

  it('加载状态不抢夺焦点,不引入自定义动画', async () => {    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });
    expect(document.activeElement?.tagName).toBe('BODY');
    await screen.findByText('60');
    expect(document.activeElement?.tagName).toBe('BODY');

    const statuses = document.querySelectorAll('[role="status"]');
    for (const el of statuses) {
      expect(el.hasAttribute('tabindex')).toBe(false);
    }

    const walletCss = readFileSync(resolve(process.cwd(), 'src/pages/WalletPage.css'), 'utf8');
    expect(walletCss).not.toMatch(/animation|transition/);
    const globalCss = readFileSync(resolve(process.cwd(), 'src/design/global.css'), 'utf8');
    expect(globalCss).toContain('prefers-reduced-motion');
  });

  it('中英渲染均不泄漏原始文案键', async () => {
    for (const locale of ['zh-CN', 'en-US'] as const) {
      const { unmount } = renderApp(<WalletPage />, {
        authStatus: 'authenticated',
        authProfile: profile,
        locale,
      });
      const ledger = await screen.findByLabelText(locale === 'zh-CN' ? /统一流水/ : /Unified ledger/);
      await within(ledger).findByRole('table');
      expect(document.body.textContent).not.toContain('pages.wallet.');
      unmount();
    }
  });
});
