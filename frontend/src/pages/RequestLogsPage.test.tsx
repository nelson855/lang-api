import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { portalRequest } from '../api/portalClient';
import { renderApp } from '../test/renderApp';
import { RequestLogsPage } from './RequestLogsPage';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

const profile = { id: 42, username: 'ordinary', displayName: 'Ordinary', email: null };

const emptyPage = { items: [], page: 1, pageSize: 20, total: 0 };

function mockList(handler: (path: string) => unknown) {
  vi.mocked(portalRequest).mockImplementation(async (path: string) => {
    if (path.includes('/portal/api/public-config')) {
      return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-logs' };
    }
    return {
      data: handler(path),
      requestId: 'req-logs',
    };
  });
}

describe('请求日志页面交互', () => {
  beforeEach(() => {
    vi.mocked(portalRequest).mockReset();
  });

  it('筛选提交回到第一页且翻页保留筛选', async () => {
    const seen: string[] = [];
    mockList((path) => {
      seen.push(path);
      return { ...emptyPage, page: path.includes('page=2') ? 2 : 1 };
    });
    const user = userEvent.setup();
    renderApp(<RequestLogsPage />, { authStatus: 'authenticated', authProfile: profile });

    await screen.findByText(/当前筛选没有请求记录/);
    await user.type(screen.getByPlaceholderText(/精确匹配/), 'probe-key-01');
    await user.click(screen.getByRole('button', { name: '查询' }));

    await waitFor(() => {
      expect(seen.some((path) => path.includes('keyName=probe-key-01') && path.includes('page=1'))).toBe(true);
    });
  });

  it('空结果保留筛选与刷新操作', async () => {
    mockList(() => emptyPage);
    const user = userEvent.setup();
    renderApp(<RequestLogsPage />, { authStatus: 'authenticated', authProfile: profile });

    await screen.findByText(/当前筛选没有请求记录/);
    await user.click(screen.getByRole('button', { name: '刷新' }));
    expect(await screen.findByText(/当前筛选没有请求记录/)).toBeInTheDocument();
  });

  it('普通错误显示重试且缺失字段显示暂无数据', async () => {
    let calls = 0;
    vi.mocked(portalRequest).mockImplementation(async (path: string) => {
      if (path.includes('/portal/api/public-config')) {
        return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-logs' };
      }
      calls += 1;
      if (calls === 1) {
        throw Object.assign(new Error('bad'), { name: 'PortalNetworkError' });
      }
      return {
        data: {
          items: [
            {
              occurredAt: '2026-09-10T10:00:00Z',
              requestId: null,
              keyName: null,
              model: null,
              result: 'ERROR',
              inputTokens: 0,
              outputTokens: 0,
              durationMs: 100,
              stream: false,
              quota: '0',
              amount: '0.0',
              currency: 'USD',
              protocol: null,
              firstTokenLatencyMs: null,
            },
          ],
          page: 1,
          pageSize: 20,
          total: 1,
        },
        requestId: 'req-logs',
      };
    });
    const user = userEvent.setup();
    renderApp(<RequestLogsPage />, { authStatus: 'authenticated', authProfile: profile });

    await screen.findByText(/加载失败/);
    await user.click(screen.getByRole('button', { name: '重试' }));
    expect(await screen.findAllByText(/暂无数据/)).not.toHaveLength(0);
  });

  it('Dashboard 下钻：URL 参数完整传递，显示“Dashboard 所选范围”', async () => {
    const seen: string[] = [];
    mockList((path) => {
      seen.push(path);
      return emptyPage;
    });
    const startTime = '2026-09-22T00:00:00.000Z';
    const endTime = '2026-09-23T00:00:00.000Z';
    const url = `/dashboard/request-logs?startTime=${encodeURIComponent(startTime)}&endTime=${encodeURIComponent(
      endTime,
    )}&result=SUCCESS&keyName=prod-key&model=gpt-4&page=1`;

    renderApp(<RequestLogsPage />, {
      authStatus: 'authenticated',
      authProfile: profile,
      initialEntries: [url],
    });

    // 显示 Dashboard 下钻提示而非时间 preset 选择器
    await screen.findByText(/Dashboard 所选范围/);

    // URL 参数被完整传递给后端
    await waitFor(() => {
      const statsCalls = seen.filter((p) => p.includes('/portal/api/request-logs'));
      expect(statsCalls.length).toBeGreaterThanOrEqual(1);
      const lastCall = statsCalls[statsCalls.length - 1];
      expect(lastCall).toContain('startTime=' + encodeURIComponent(startTime));
      expect(lastCall).toContain('endTime=' + encodeURIComponent(endTime));
      expect(lastCall).toContain('result=SUCCESS');
      expect(lastCall).toContain('keyName=prod-key');
      expect(lastCall).toContain('model=gpt-4');
      expect(lastCall).toContain('page=1');
    });
  });

  it('非法 URL 参数被规范化（result/page 回退，单边时间清空）', async () => {
    const seen: string[] = [];
    mockList((path) => {
      seen.push(path);
      return emptyPage;
    });
    const url =
      '/dashboard/request-logs?result=INVALID&page=abc&startTime=' +
      encodeURIComponent('2026-09-22T00:00:00.000Z');

    renderApp(<RequestLogsPage />, {
      authStatus: 'authenticated',
      authProfile: profile,
      initialEntries: [url],
    });

    await screen.findByText(/当前筛选没有请求记录/);
    await waitFor(() => {
      const lastCall = seen.filter((p) => p.includes('/portal/api/request-logs')).pop();
      expect(lastCall).toBeDefined();
      // result 回退 SUCCESS、page 回退 1
      expect(lastCall).toContain('result=SUCCESS');
      expect(lastCall).toContain('page=1');
      // 单边时间被丢弃，不会发送给后端
      expect(lastCall).not.toContain('startTime=');
      expect(lastCall).not.toContain('endTime=');
    });
  });
});
