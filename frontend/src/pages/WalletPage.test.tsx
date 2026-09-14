import { screen } from '@testing-library/react';
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

const record = {
  orderId: 'ORD-1',
  requestedAmount: '10.5',
  currency: 'USD',
  paymentMethod: 'ALIPAY',
  status: 'SUCCEEDED',
  createdAt: '2023-11-14T22:13:20Z',
  completedAt: '2023-11-14T22:15:00Z',
};

describe('钱包页面', () => {
  beforeEach(() => {
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-w' };
      }
      if (path.includes('/portal/api/account/balance')) {
        return { data: { quota: '500000', amount: '1.0', currency: 'USD' }, requestId: 'req-w' };
      }
      if (path.includes('/portal/api/account/topup-options')) {
        return {
          data: { enabled: false, methods: [], currency: 'USD', reason: 'NOT_CONFIGURED' },
          requestId: 'req-w',
        };
      }
      if (path.includes('/portal/api/account/topups')) {
        return { data: { items: [], page: 1, pageSize: 20, total: 0 }, requestId: 'req-w' };
      }
      throw new PortalApiError(502, 'UPSTREAM_ERROR', 'upstream', 'req-w');
    });
  });

  it('记录失败时保留余额与关闭说明并可重试，不渲染充值控件', async () => {
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-w' };
      }
      if (path.includes('/portal/api/account/balance')) {
        return { data: { quota: '500000', amount: '1.0', currency: 'USD' }, requestId: 'req-w' };
      }
      if (path.includes('/portal/api/account/topup-options')) {
        return {
          data: { enabled: false, methods: [], currency: 'USD', reason: 'NOT_CONFIGURED' },
          requestId: 'req-w',
        };
      }
      throw new PortalApiError(502, 'UPSTREAM_ERROR', 'upstream', 'req-w');
    });
    const user = userEvent.setup();
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });

    expect(await screen.findByText(/1\.0/)).toBeInTheDocument();
    expect(await screen.findByText(/充值未开放/)).toBeInTheDocument();
    expect(await screen.findByText(/加载失败/)).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).toBeNull();
    expect(screen.queryByRole('button', { name: /充值|支付/ })).toBeNull();

    await user.click(screen.getByRole('button', { name: '重试' }));
    expect(await screen.findByText(/加载失败/)).toBeInTheDocument();
  });

  it('空记录显示真实空状态', async () => {
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });

    expect(await screen.findByText(/暂无充值记录/)).toBeInTheDocument();
  });

  it('记录展示订单号、金额与文字状态并可翻页', async () => {
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-w' };
      }
      if (path.includes('/portal/api/account/balance')) {
        return { data: { quota: '500000', amount: '1.0', currency: 'USD' }, requestId: 'req-w' };
      }
      if (path.includes('/portal/api/account/topup-options')) {
        return {
          data: { enabled: false, methods: [], currency: 'USD', reason: 'NOT_CONFIGURED' },
          requestId: 'req-w',
        };
      }
      if (path.includes('page=2')) {
        return {
          data: { items: [{ ...record, orderId: 'ORD-2' }], page: 2, pageSize: 1, total: 2 },
          requestId: 'req-w',
        };
      }
      return {
        data: { items: [record], page: 1, pageSize: 1, total: 2 },
        requestId: 'req-w',
      };
    });
    const user = userEvent.setup();
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });

    expect(await screen.findByText('ORD-1')).toBeInTheDocument();
    expect((await screen.findAllByText(/已成功/)).length).toBeGreaterThan(0);
    expect((await screen.findAllByText(/支付宝/)).length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: '下一页' }));
    expect(await screen.findByText('ORD-2')).toBeInTheDocument();
  });

  it('会话失效时记录区域不展示本地错误', async () => {
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-w' };
      }
      if (path.includes('/portal/api/account/balance')) {
        return { data: { quota: '500000', amount: '1.0', currency: 'USD' }, requestId: 'req-w' };
      }
      if (path.includes('/portal/api/account/topup-options')) {
        return {
          data: { enabled: false, methods: [], currency: 'USD', reason: 'NOT_CONFIGURED' },
          requestId: 'req-w',
        };
      }
      throw new PortalApiError(401, 'UNAUTHENTICATED', 'expired', 'req-w');
    });
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile });

    expect(await screen.findByText(/1\.0/)).toBeInTheDocument();
    const records = await screen.findByLabelText(/充值记录/);
    expect(records.querySelector('[role="alert"]')).toBeNull();
  });

  it('英文环境展示对应标题与关闭说明', async () => {
    renderApp(<WalletPage />, { authStatus: 'authenticated', authProfile: profile, locale: 'en-US' });

    expect(await screen.findByRole('heading', { name: 'Wallet' })).toBeInTheDocument();
    expect(await screen.findByText(/Top-up unavailable/)).toBeInTheDocument();
  });
});
