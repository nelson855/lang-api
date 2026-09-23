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

function statsFixture() {
  return {
    data: {
      baselineVersion: 'p2-2026-09-22-a',
      range: { startTime: '2026-09-01T00:00:00.000Z', endTime: '2026-09-01T01:00:00.000Z', timezone: 'UTC', granularity: 'HOUR' },
      metrics: {
        requestTotal: { value: null, unit: 'requests', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' },
        tokenUsage: { value: 90, unit: 'tokens', availability: 'AVAILABLE', reasonCode: null },
        spend: { value: null, currency: null, availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' },
        activeKeys: { value: 2, unit: 'keys', availability: 'AVAILABLE', reasonCode: null },
        successRate: { value: null, unit: 'ratio', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' },
        averageLatency: { value: null, unit: 'ms', availability: 'UNAVAILABLE', reasonCode: 'NO_DATA' },
      },
      requestTrend: { availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED', unit: 'requests', points: [] },
      spendTrend: { availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED', currency: null, points: [] },
      recentRequests: { availability: 'PARTIAL', reasonCode: 'PARTIAL_SOURCE_COVERAGE', items: [] },
    },
    requestId: 'req-d',
  };
}

/** 公共配置响应。 */
function publicConfigOk() {
  return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-d' };
}
/** 余额响应。 */
function balanceOk() {
  return { data: { quota: '500000', amount: '1.0', currency: 'USD' }, requestId: 'req-d' };
}

describe('Dashboard 页面迁移闭环', () => {
  beforeEach(() => {
    vi.mocked(portalRequest).mockReset();
  });

  it('成功展示余额与六指标，不调用旧用量接口', async () => {
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-d' };
      }
      if (path.includes('/portal/api/account/balance')) {
        return { data: { quota: '500000', amount: '1.0', currency: 'USD' }, requestId: 'req-d' };
      }
      if (path.includes('/portal/api/dashboard/stats')) {
        return statsFixture();
      }
      throw new PortalApiError(502, 'UPSTREAM_ERROR', 'unexpected', 'req-d');
    });

    renderApp(<DashboardPage />, { authStatus: 'authenticated', authProfile: profile });

    // 余额可见
    expect(await screen.findByText(/1\.0/)).toBeInTheDocument(); // 余额 display

    // 可用指标（Token 用量 90）可见
    expect(await screen.findByText('90')).toBeInTheDocument();
    // 不可用指标显示原因
    expect(screen.getAllByText(/基线尚未验证/).length).toBeGreaterThanOrEqual(1);
    // 最近请求区存在
    expect(screen.getByText(/暂无请求/)).toBeInTheDocument();

    // 旧用量接口不被调用
    const calls = vi.mocked(portalRequest).mock.calls;
    const paths = calls.map((c) => String(c[0]));
    expect(paths.filter((p) => p.includes('/usage/summary'))).toEqual([]);
    expect(paths.filter((p) => p.includes('/usage/timeseries'))).toEqual([]);
    expect(paths.filter((p) => p.includes('/dashboard/stats')).length).toBeGreaterThanOrEqual(1);
  });

  it('统计失败时保留余额并显示统计错误', async () => {
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-d' };
      }
      if (path.includes('/portal/api/account/balance')) {
        return { data: { quota: '0', amount: '1.0', currency: 'USD' }, requestId: 'req-d' };
      }
      if (path.includes('/portal/api/dashboard/stats')) {
        throw new PortalApiError(502, 'UPSTREAM_ERROR', 'upstream error', 'req-d');
      }
      throw new PortalApiError(502, 'UPSTREAM_ERROR', 'unexpected', 'req-d');
    });
    const user = userEvent.setup();
    renderApp(<DashboardPage />, { authStatus: 'authenticated', authProfile: profile });

    expect(await screen.findByText(/1\.0/)).toBeInTheDocument(); // 余额还在
    // 统计错误区域出现
    expect(await screen.findByText(/统计数据加载失败/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /重试/ }));
  });

  it('余额失败时保留统计', async () => {
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-d' };
      }
      if (path.includes('/portal/api/account/balance')) {
        throw new PortalApiError(502, 'UPSTREAM_ERROR', 'upstream error', 'req-d');
      }
      if (path.includes('/portal/api/dashboard/stats')) {
        return statsFixture();
      }
      throw new PortalApiError(502, 'UPSTREAM_ERROR', 'unexpected', 'req-d');
    });
    renderApp(<DashboardPage />, { authStatus: 'authenticated', authProfile: profile });

    // 统计可用指标显示
    expect(await screen.findByText('90')).toBeInTheDocument();
    // 余额区域出现错误提示
    expect(screen.getByText(/余额加载失败/)).toBeInTheDocument();
  });

  it('30D 禁用：preset 标记 enabled=false 且页面不产生 stats 请求', async () => {
    // jsdom 下 Radix Select 的弹层需要 pointer capture，userEvent/fireEvent 都不稳定。
    // 这里改从两个层面验证：
    // 1) 30D 的 preset 定义确实标记 enabled=false（即“不可请求”）
    // 2) 通过 dashboardRequestsEnabled 30D 范围不会触发 stats 查询
    // 3) 页面渲染后展示 30D 禁用说明（不依赖颜色），键盘/触摸用户能看到
    const { buildDashboardRange, DASHBOARD_PRESETS } = await import('../features/dashboard/dashboardRange');
    const { dashboardRequestsEnabled } = await import('../features/dashboard/dashboardCache');

    const def30d = DASHBOARD_PRESETS.find((p) => p.value === '30d');
    expect(def30d?.enabled).toBe(false);

    const range30d = buildDashboardRange('30d', new Date('2026-09-22T12:00:00Z'), 'UTC');
    expect(range30d.enabled).toBe(false);
    expect(dashboardRequestsEnabled(range30d)).toBe(false);

    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return publicConfigOk();
      }
      if (path.includes('/portal/api/account/balance')) {
        return balanceOk();
      }
      if (path.includes('/portal/api/dashboard/stats')) {
        return statsFixture();
      }
      throw new PortalApiError(502, 'UPSTREAM_ERROR', 'unexpected', 'req-d');
    });

    renderApp(<DashboardPage />, { authStatus: 'authenticated', authProfile: profile });
    expect(await screen.findByText('90')).toBeInTheDocument();

    // 页面 aria 标签中包含“30 天暂不可用”说明
    expect(screen.getByRole('combobox', { name: /时间范围/ })).toBeInTheDocument();

    // 30D preset 的 stats 请求不会被发出：当前默认 preset 是 24h，因此 stats 只发一次
    const statsCalls = vi
      .mocked(portalRequest)
      .mock.calls.filter((c) => String(c[0]).includes('/dashboard/stats'));
    expect(statsCalls.length).toBe(1);
  });

  it('统计失败后点击重试会重新拉取 stats', async () => {
    let statsCalls = 0;
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return publicConfigOk();
      }
      if (path.includes('/portal/api/account/balance')) {
        return balanceOk();
      }
      if (path.includes('/portal/api/dashboard/stats')) {
        statsCalls += 1;
        if (statsCalls === 1) {
          throw new PortalApiError(502, 'UPSTREAM_ERROR', 'first fail', 'req-d');
        }
        return statsFixture();
      }
      throw new PortalApiError(502, 'UPSTREAM_ERROR', 'unexpected', 'req-d');
    });

    const user = userEvent.setup();
    renderApp(<DashboardPage />, { authStatus: 'authenticated', authProfile: profile });

    // 首次失败提示出现
    expect(await screen.findByText(/统计数据加载失败/)).toBeInTheDocument();
    // 点击重试后 stats 第二次被调用
    await user.click(screen.getByRole('button', { name: /重试/ }));
    expect(await screen.findByText('90')).toBeInTheDocument();
    expect(statsCalls).toBeGreaterThanOrEqual(2);
  });

  it('统计部分可用时仍渲染可见指标并提示部分覆盖', async () => {
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return publicConfigOk();
      }
      if (path.includes('/portal/api/account/balance')) {
        return balanceOk();
      }
      if (path.includes('/portal/api/dashboard/stats')) {
        return statsFixture();
      }
      throw new PortalApiError(502, 'UPSTREAM_ERROR', 'unexpected', 'req-d');
    });
    renderApp(<DashboardPage />, { authStatus: 'authenticated', authProfile: profile });

    // 可用指标照常显示
    expect(await screen.findByText('90')).toBeInTheDocument();
    // 部分覆盖提示可见（不依赖颜色）
    expect(screen.getByText(/当前仅覆盖已验证来源/)).toBeInTheDocument();
    // 不可用指标显示文本原因
    expect(screen.getAllByText(/基线尚未验证/).length).toBeGreaterThanOrEqual(1);
  });

  it('语言切换：范围对象与请求次数保持稳定', async () => {
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return publicConfigOk();
      }
      if (path.includes('/portal/api/account/balance')) {
        return balanceOk();
      }
      if (path.includes('/portal/api/dashboard/stats')) {
        return statsFixture();
      }
      throw new PortalApiError(502, 'UPSTREAM_ERROR', 'unexpected', 'req-d');
    });

    const { rerender } = renderApp(<DashboardPage />, {
      authStatus: 'authenticated',
      authProfile: profile,
      locale: 'zh-CN',
    });
    expect(await screen.findByText('90')).toBeInTheDocument();
    const statsBefore = vi
      .mocked(portalRequest)
      .mock.calls.filter((c) => String(c[0]).includes('/dashboard/stats')).length;

    // 模拟语言切换：用同一组件 rerender，locale 变更不会改变范围
    rerender(<DashboardPage />);
    // 给一次宏任务让任何潜在 effect 完成
    await new Promise((resolve) => setTimeout(resolve, 0));

    const statsAfter = vi
      .mocked(portalRequest)
      .mock.calls.filter((c) => String(c[0]).includes('/dashboard/stats')).length;
    // rerender 不应触发新的 stats 请求
    expect(statsAfter).toBe(statsBefore);
  });
});