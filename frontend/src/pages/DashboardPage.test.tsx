import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { PortalApiError } from '../api/envelope';
import { portalRequest } from '../api/portalClient';
import { renderApp } from '../test/renderApp';
import { DashboardPage } from './DashboardPage';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

const profile = { id: 42, username: 'ordinary', displayName: 'Ordinary', email: null };

describe('用量概览局部失败', () => {
  beforeEach(() => {
    vi.mocked(portalRequest).mockReset();
  });

  it('趋势失败时保留余额与摘要并可重试', async () => {
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-d' };
      }
      if (path.includes('/portal/api/account/balance')) {
        return { data: { quota: '500000', amount: '1.0', currency: 'USD' }, requestId: 'req-d' };
      }
      if (path.includes('/portal/api/usage/summary')) {
        return {
          data: { quota: '1200', amount: '0.0024', currency: 'USD', rpm: 30, tpm: 4000, rateWindowSeconds: 60 },
          requestId: 'req-d',
        };
      }
      throw new PortalApiError(502, 'UPSTREAM_ERROR', 'upstream', 'req-d');
    });
    const user = userEvent.setup();
    renderApp(<DashboardPage />, { authStatus: 'authenticated', authProfile: profile });

    expect(await screen.findByText(/1\.0/)).toBeInTheDocument();
    expect(await screen.findByText(/加载失败/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '重试' }));
    expect(await screen.findByText(/加载失败/)).toBeInTheDocument();
  });

  it('趋势为空时不绘制误导性历史', async () => {
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-d' };
      }
      if (path.includes('/portal/api/account/balance')) {
        return { data: { quota: '0', amount: '0.0', currency: 'USD' }, requestId: 'req-d' };
      }
      if (path.includes('/portal/api/usage/summary')) {
        return {
          data: { quota: '0', amount: '0.0', currency: 'USD', rpm: 0, tpm: 0, rateWindowSeconds: 60 },
          requestId: 'req-d',
        };
      }
      return { data: { granularity: 'HOUR', points: [] }, requestId: 'req-d' };
    });
    renderApp(<DashboardPage />, { authStatus: 'authenticated', authProfile: profile });

    expect(await screen.findByText(/暂无用量/)).toBeInTheDocument();
  });
});
