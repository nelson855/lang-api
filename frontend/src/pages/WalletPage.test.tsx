import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PortalApiError } from '../api/envelope';
import { portalRequest } from '../api/portalClient';
import { renderApp } from '../test/renderApp';
import { WalletPage } from './WalletPage';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

const profile = { id: 42, username: 'ordinary', displayName: 'Ordinary', email: null };

const RANGE_7D = {
  start: '2026-09-01T00:00:00Z',
  end: '2026-09-08T00:00:00Z',
  timezone: 'UTC',
  granularity: 'HOUR',
} as const;

function summaryOk(overrides = {}) {
  return {
    data: {
      baselineVersion: 'p2-2026-09-22-a',
      range: { ...RANGE_7D },
      recordCount: { value: '3', unit: 'records', availability: 'AVAILABLE', reasonCode: null },
      quotaTotal: { value: '60', unit: 'quota', availability: 'AVAILABLE', reasonCode: null },
      moneyTotal: {
        value: null,
        currency: null,
        availability: 'UNAVAILABLE',
        reasonCode: 'CURRENCY_CONVERSION_NOT_VERIFIED',
      },
      coverage: { type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null },
      ...overrides,
    },
    requestId: 'req-w',
  };
}

const CONSUMPTION_ITEM = {
  transactionId: 'CONSUMPTION_abc123',
  occurredAt: '2026-09-01T12:00:00Z',
  type: 'CONSUMPTION',
  direction: 'DEBIT',
  amount: '60',
  unit: 'QUOTA',
  currency: null,
  status: 'SUCCEEDED',
  remark: 'gpt-4o',
  referenceId: 'req-abc',
};

function transactionsOk(overrides = {}) {
  return {
    data: {
      baselineVersion: 'p2-2026-09-22-a',
      range: { ...RANGE_7D },
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
      items: [{ ...CONSUMPTION_ITEM }],
      ...overrides,
    },
    requestId: 'req-w',
  };
}

const topupRecord = {
  orderId: 'ORD-1',
  requestedAmount: '10.5',
  currency: 'USD',
  paymentMethod: 'ALIPAY',
  status: 'SUCCEEDED',
  createdAt: '2023-11-14T22:13:20Z',
  completedAt: '2023-11-14T22:15:00Z',
};

interface FixtureOptions {
  balance?: 'ok' | 'fail' | 'unauth';
  summary?: 'ok' | 'zero' | 'partial' | 'fail' | 'unauth';
  transactions?: 'ok' | 'empty' | 'topupBlocked' | 'fail' | 'unauth';
  options?: 'ok' | 'fail';
  topups?: 'ok' | 'empty' | 'fail';
}

function installFixture(opts: FixtureOptions = {}) {
  const { balance = 'ok', summary = 'ok', transactions = 'ok', options = 'ok', topups = 'empty' } = opts;
  vi.mocked(portalRequest).mockClear();
  vi.mocked(portalRequest).mockImplementation(async (path: string) => {
    const url = String(path);
    if (url.includes('/portal/api/public-config')) {
      return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-w' };
    }
    if (url.includes('/portal/api/account/balance')) {
      if (balance === 'fail') throw new PortalApiError(502, 'UPSTREAM_ERROR', 'up', 'req-w');
      if (balance === 'unauth') throw new PortalApiError(401, 'UNAUTHENTICATED', 'expired', 'req-w');
      return { data: { quota: '500000', amount: '1.0', currency: 'USD' }, requestId: 'req-w' };
    }
    if (url.includes('/portal/api/account/consumption-summary')) {
      if (summary === 'fail') throw new PortalApiError(502, 'UPSTREAM_ERROR', 'up', 'req-w');
      if (summary === 'unauth') throw new PortalApiError(401, 'UNAUTHENTICATED', 'expired', 'req-w');
      if (summary === 'zero') {
        return summaryOk({
          recordCount: { value: '0', unit: 'records', availability: 'AVAILABLE', reasonCode: null },
          quotaTotal: { value: '0', unit: 'quota', availability: 'AVAILABLE', reasonCode: null },
        });
      }
      if (summary === 'partial') {
        return summaryOk({
          coverage: {
            type: 'CONSUMPTION',
            availability: 'PARTIAL',
            reasonCode: 'MISSING_STABLE_REFERENCE',
          },
        });
      }
      return summaryOk();
    }
    if (url.includes('/portal/api/account/transactions')) {
      if (transactions === 'fail') throw new PortalApiError(502, 'UPSTREAM_ERROR', 'up', 'req-w');
      if (transactions === 'unauth')
        throw new PortalApiError(401, 'UNAUTHENTICATED', 'expired', 'req-w');
      if (transactions === 'empty') {
        return transactionsOk({
          type: 'CONSUMPTION',
          items: [],
          total: 0,
          availability: 'AVAILABLE',
          coverage: [{ type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null }],
        });
      }
      if (transactions === 'topupBlocked') {
        return transactionsOk({
          type: 'TOPUP',
          items: [],
          total: 0,
          availability: 'UNAVAILABLE',
          reasonCode: 'BASELINE_NOT_VERIFIED',
          coverage: [
            { type: 'TOPUP', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' },
          ],
        });
      }
      return transactionsOk();
    }
    if (url.includes('/portal/api/account/topup-options')) {
      if (options === 'fail') throw new PortalApiError(502, 'UPSTREAM_ERROR', 'up', 'req-w');
      return {
        data: { enabled: false, methods: [], currency: 'USD', reason: 'NOT_CONFIGURED' },
        requestId: 'req-w',
      };
    }
    if (url.includes('/portal/api/account/topups')) {
      if (topups === 'fail') throw new PortalApiError(502, 'UPSTREAM_ERROR', 'up', 'req-w');
      if (topups === 'ok') {
        return { data: { items: [{ ...topupRecord }], page: 1, pageSize: 20, total: 1 }, requestId: 'req-w' };
      }
      return { data: { items: [], page: 1, pageSize: 20, total: 0 }, requestId: 'req-w' };
    }
    throw new PortalApiError(502, 'UPSTREAM_ERROR', 'unexpected', 'req-w');
  });
}

function callsFor(path: string): string[] {
  return vi
    .mocked(portalRequest)
    .mock.calls.map((c) => String(c[0]))
    .filter((url) => url.includes(path));
}

describe('钱包页面五区域独立 fixture', () => {
  beforeEach(() => {
    installFixture();
  });

  it('全部成功时五个区域各自展示对应接口数据', async () => {
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });

    expect(await screen.findByText(/1\.0/)).toBeInTheDocument();
    expect(await screen.findByText(/充值未开放/)).toBeInTheDocument();
    expect(await screen.findByText('60')).toBeInTheDocument();
    expect((await screen.findAllByText('gpt-4o')).length).toBeGreaterThan(0);
    expect(await screen.findByText(/暂无充值记录/)).toBeInTheDocument();

    expect(callsFor('/portal/api/account/balance').length).toBeGreaterThan(0);
    expect(callsFor('/portal/api/account/consumption-summary').length).toBeGreaterThan(0);
    expect(callsFor('/portal/api/account/transactions').length).toBeGreaterThan(0);
    expect(callsFor('/portal/api/account/topup-options').length).toBeGreaterThan(0);
    expect(callsFor('/portal/api/account/topups').length).toBeGreaterThan(0);
  });

  it('汇总失败时仅汇总区域报错,其他成功区域不受影响', async () => {
    installFixture({ summary: 'fail' });
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });

    const summary = await screen.findByLabelText(/范围内消费/);
    expect(await within(summary).findByRole('alert')).toBeInTheDocument();
    expect(await screen.findByText(/1\.0/)).toBeInTheDocument();
    expect((await screen.findAllByText('gpt-4o')).length).toBeGreaterThan(0);
    expect(within(summary).queryByText('60')).toBeNull();
  });
});

describe('钱包页面筛选与 URL', () => {
  beforeEach(() => {
    installFixture();
  });

  it('同一范围同时驱动汇总与流水,类型筛选进入流水请求', async () => {
    renderApp(<WalletPage />, {
      authStatus: 'authenticated',
      authProfile: profile,
      initialEntries: [
        '/?range=7d&startTime=2026-09-01T00%3A00%3A00Z&endTime=2026-09-08T00%3A00%3A00Z&timezone=UTC&granularity=HOUR&type=CONSUMPTION&page=1',
      ],
    });

    await screen.findAllByText('gpt-4o');

    const summaryUrls = callsFor('/portal/api/account/consumption-summary');
    const txUrls = callsFor('/portal/api/account/transactions');
    expect(summaryUrls.length).toBeGreaterThan(0);
    expect(txUrls.length).toBeGreaterThan(0);
    const summaryParams = new URLSearchParams(summaryUrls[0].split('?')[1]);
    const txParams = new URLSearchParams(txUrls[0].split('?')[1]);
    expect(summaryParams.get('startTime')).toBe(txParams.get('startTime'));
    expect(summaryParams.get('endTime')).toBe(txParams.get('endTime'));
    expect(summaryParams.get('timezone')).toBe(txParams.get('timezone'));
    expect(txParams.get('type')).toBe('CONSUMPTION');
  });

  it('重试不重建边界:两次汇总请求的起止时间相同', async () => {
    installFixture({ summary: 'fail' });
    const user = userEvent.setup();
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });

    const summary = await screen.findByLabelText(/范围内消费/);
    await user.click(await within(summary).findByRole('button', { name: '重试' }));
    await waitFor(() => expect(callsFor('/portal/api/account/consumption-summary').length).toBe(2));

    const starts = callsFor('/portal/api/account/consumption-summary').map(
      (url) => new URLSearchParams(url.split('?')[1]).get('startTime'),
    );
    expect(starts[0]).toBeTruthy();
    expect(starts[0]).toBe(starts[1]);
  });

  it('30D 可发现但不发送聚合请求', async () => {
    renderApp(<WalletPage />, {
      authStatus: 'authenticated',
      authProfile: profile,
      initialEntries: [
        '/?range=30d&startTime=2026-08-11T08%3A00%3A00Z&endTime=2026-09-10T08%3A00%3A00Z&timezone=Asia%2FShanghai&granularity=DAY&type=ALL&page=1',
      ],
    });

    expect(await screen.findAllByText(/30 天范围暂不可用/)).toHaveLength(2);
    await waitFor(() => expect(callsFor('/portal/api/account/balance').length).toBeGreaterThan(0));
    expect(callsFor('/portal/api/account/consumption-summary')).toHaveLength(0);
    expect(callsFor('/portal/api/account/transactions')).toHaveLength(0);
  });
});

describe('钱包页面消费汇总展示', () => {
  beforeEach(() => {
    installFixture();
  });

  it('展示条数、quota 与正式金额不可用原因,不显示 0 USD', async () => {
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });

    const summary = await screen.findByLabelText(/范围内消费/);
    expect(await within(summary).findByText('3')).toBeInTheDocument();
    expect(await within(summary).findByText('60')).toBeInTheDocument();
    expect(await within(summary).findByText(/正式货币换算尚未验证/)).toBeInTheDocument();
    expect(within(summary).queryByText(/0 USD/)).toBeNull();
  });

  it('真实零消费显示零值', async () => {
    installFixture({ summary: 'zero' });
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });

    const summary = await screen.findByLabelText(/范围内消费/);
    const zeros = await within(summary).findAllByText('0');
    expect(zeros.length).toBeGreaterThanOrEqual(2);
  });

  it('部分覆盖持续显示说明', async () => {
    installFixture({ summary: 'partial' });
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });

    const summary = await screen.findByLabelText(/范围内消费/);
    expect(await within(summary).findByText(/缺少稳定引用/)).toBeInTheDocument();
    expect(await within(summary).findByText('60')).toBeInTheDocument();
  });
});

describe('钱包页面统一流水展示', () => {
  beforeEach(() => {
    installFixture();
  });

  it('桌面表格展示全部字段,窄屏卡片等价存在', async () => {
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });

    const ledger = await screen.findByLabelText(/统一流水/);
    const table = await within(ledger).findByRole('table');
    for (const header of ['时间', '类型', '方向', '金额', '状态', '备注', '来源']) {
      expect(within(table).getByText(header)).toBeInTheDocument();
    }
    for (const value of ['消费', '支出', '已成功', 'req-abc', 'gpt-4o']) {
      expect(within(ledger).getAllByText(value).length).toBeGreaterThan(0);
    }
    const cards = ledger.querySelector('.wallet-cards');
    expect(cards).not.toBeNull();
    expect(within(cards as HTMLElement).getAllByText('消费').length).toBeGreaterThan(0);
    expect(within(ledger).getByText(/充值流水基线尚未验证/)).toBeInTheDocument();
  });

  it('AVAILABLE 加 total=0 显示真实空,不显示不可用面板', async () => {
    installFixture({ transactions: 'empty' });
    renderApp(<WalletPage />, {
      authStatus: 'authenticated',
      authProfile: profile,
      initialEntries: [
        '/?range=7d&startTime=2026-09-01T00%3A00%3A00Z&endTime=2026-09-08T00%3A00%3A00Z&timezone=UTC&granularity=HOUR&type=CONSUMPTION&page=1',
      ],
    });

    const ledger = await screen.findByLabelText(/统一流水/);
    expect(await within(ledger).findByText(/暂无流水/)).toBeInTheDocument();
    expect(within(ledger).queryByText(/尚未验证/)).toBeNull();
  });

  it('TOPUP 不可用显示阻塞说明而非空列表,旧充值记录不混入', async () => {
    installFixture({ transactions: 'topupBlocked', topups: 'ok' });
    renderApp(<WalletPage />, {
      authStatus: 'authenticated',
      authProfile: profile,
      initialEntries: [
        '/?range=7d&startTime=2026-09-01T00%3A00%3A00Z&endTime=2026-09-08T00%3A00%3A00Z&timezone=UTC&granularity=HOUR&type=TOPUP&page=1',
      ],
    });

    const ledger = await screen.findByLabelText(/统一流水/);
    expect(await within(ledger).findByText(/充值流水基线尚未验证/)).toBeInTheDocument();
    expect(within(ledger).queryByText('ORD-1')).toBeNull();
    const records = await screen.findByLabelText(/充值记录/);
    expect((await within(records).findAllByText('ORD-1')).length).toBeGreaterThan(0);
  });

  it('翻页只改变统一流水页码并保留筛选', async () => {
    const user = userEvent.setup();
    renderApp(<WalletPage />, {
      authStatus: 'authenticated',
      authProfile: profile,
      initialEntries: [
        '/?range=7d&startTime=2026-09-01T00%3A00%3A00Z&endTime=2026-09-08T00%3A00%3A00Z&timezone=UTC&granularity=HOUR&type=ALL&page=1',
      ],
    });

    const ledger = await screen.findByLabelText(/统一流水/);
    await user.click(await within(ledger).findByRole('button', { name: '下一页' }));
    await waitFor(() =>
      expect(
        callsFor('/portal/api/account/transactions').some((url) => url.includes('page=2')),
      ).toBe(true),
    );
    const txUrls = callsFor('/portal/api/account/transactions');
    const firstStart = new URLSearchParams(txUrls[0].split('?')[1]).get('startTime');
    const lastStart = new URLSearchParams(txUrls[txUrls.length - 1].split('?')[1]).get('startTime');
    expect(firstStart).toBeTruthy();
    expect(lastStart).toBe(firstStart);
  });
});

describe('钱包页面充值兼容区', () => {
  beforeEach(() => {
    installFixture();
  });

  it('能力失败时安全关闭并可重试,不隐藏只读区域', async () => {
    installFixture({ options: 'fail' });
    const user = userEvent.setup();
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });

    expect(await screen.findByText(/充值能力暂不可用/)).toBeInTheDocument();
    expect((await screen.findAllByText('gpt-4o')).length).toBeGreaterThan(0);

    const disabled = screen.getByLabelText(/充值未开放/);
    await user.click(within(disabled).getByRole('button', { name: '重试' }));
    await waitFor(() => expect(callsFor('/portal/api/account/topup-options').length).toBe(2));
  });

  it('旧记录非空但 TOPUP 覆盖不可用:两者分区展示且无写控件', async () => {
    installFixture({ topups: 'ok' });
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });

    const records = await screen.findByLabelText(/充值记录/);
    expect((await within(records).findAllByText('ORD-1')).length).toBeGreaterThan(0);
    const ledger = screen.getByLabelText(/统一流水/);
    expect(within(ledger).queryByText('ORD-1')).toBeNull();

    expect(screen.queryByRole('textbox')).toBeNull();
    expect(screen.queryByRole('button', { name: /充值|支付|退款|提现/ })).toBeNull();
  });

  it('会话失效时聚合区域不展示本地错误', async () => {
    installFixture({ summary: 'unauth', transactions: 'unauth' });
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });

    expect(await screen.findByText(/1\.0/)).toBeInTheDocument();
    const summary = await screen.findByLabelText(/范围内消费/);
    expect(summary.querySelector('[role="alert"]')).toBeNull();
  });

  it('英文环境展示新区域标题', async () => {
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile, locale: 'en-US' });

    expect(await screen.findByRole('heading', { name: 'Wallet' })).toBeInTheDocument();
    expect(await screen.findByText(/Consumption in range/)).toBeInTheDocument();
    expect(await screen.findByText(/Unified ledger/)).toBeInTheDocument();
  });
});
