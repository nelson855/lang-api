import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { portalRequest } from '../api/portalClient';
import { renderApp } from '../test/renderApp';
import { RequestLogsPage } from './RequestLogsPage';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

const profile = { id: 42, username: 'ordinary', displayName: 'Ordinary', email: null };

/** 基础字段固定，只让增强字段随用例变化，避免用例之间互相耦合。 */
function logItem(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    occurredAt: '2026-09-10T10:00:00Z',
    requestId: 'req-1',
    keyName: 'probe-key-01',
    model: 'gpt-test',
    result: 'SUCCESS',
    inputTokens: 10,
    outputTokens: 20,
    durationMs: 3000,
    stream: true,
    quota: '500000',
    amount: '1.0',
    currency: 'USD',
    ...overrides,
  };
}

function mockItems(items: unknown[], total = items.length) {
  vi.mocked(portalRequest).mockImplementation(async (path: string) => {
    if (path.includes('/portal/api/public-config')) {
      return { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-logs' };
    }
    return { data: { items, page: 1, pageSize: 20, total }, requestId: 'req-logs' };
  });
}

async function renderWith(items: unknown[], options: { locale?: 'zh-CN' | 'en-US' } = {}) {
  mockItems(items);
  renderApp(<RequestLogsPage />, {
    authStatus: 'authenticated',
    authProfile: profile,
    locale: options.locale,
  });
}

describe('请求日志取消辅助指标后保留基础信息和费用', () => {
  beforeEach(() => { vi.mocked(portalRequest).mockReset(); });

  it('非空兼容字段也不展示协议、TTFT 或说明，保留总耗时与 Token', async () => {
    await renderWith([logItem({ protocol: 'OPENAI', firstTokenLatencyMs: 820 })]);
    const table = await screen.findByRole('table');
    expect(within(table).getByText('3000ms')).toBeInTheDocument();
    expect(within(table).getByText('10/20')).toBeInTheDocument();
    expect(screen.queryByText('协议')).not.toBeInTheDocument();
    expect(screen.queryByText('首 Token 延迟')).not.toBeInTheDocument();
    expect(screen.queryByText('OPENAI')).not.toBeInTheDocument();
    expect(screen.queryByText('820ms')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '说明' })).not.toBeInTheDocument();
  });

  it('旧响应省略兼容字段仍可展示基础日志', async () => {
    await renderWith([logItem()]);
    const table = await screen.findByRole('table');
    expect(within(table).getByText('gpt-test')).toBeInTheDocument();
    expect(within(table).getByText('probe-key-01')).toBeInTheDocument();
    expect(within(table).queryAllByText('暂无数据')).toHaveLength(0);
  });

  it('流式状态仅按 stream 展示', async () => {
    await renderWith([logItem({ requestId: 'req-a', stream: true }), logItem({ requestId: 'req-b', stream: false })]);
    const table = await screen.findByRole('table');
    expect(within(table).getByText('流式')).toBeInTheDocument();
    expect(within(table).getByText('非流式')).toBeInTheDocument();
  });

  it('费用完整保留金额和大 quota，说明可用键盘展开', async () => {
    await renderWith([logItem({ amount: '0.000001', quota: '9007199254740993' })]);
    const table = await screen.findByRole('table');
    expect(within(table).getByText('0.000001 USD')).toBeInTheDocument();
    expect(within(table).getByText(/9007199254740993/)).toBeInTheDocument();
    const toggle = screen.getByRole('button', { name: '费用说明' });
    const user = userEvent.setup();
    toggle.focus();
    await user.keyboard('{Enter}');
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('note')).toHaveTextContent(/费用按日志额度/);
  });

  it('英文页面只展示保留字段及费用说明', async () => {
    await renderWith([logItem({ protocol: 'GEMINI', firstTokenLatencyMs: 45 })], { locale: 'en-US' });
    const table = await screen.findByRole('table');
    expect(within(table).getAllByText('Streaming')).toHaveLength(2);
    expect(within(table).getByText('3000ms')).toBeInTheDocument();
    expect(screen.queryByText('Protocol')).not.toBeInTheDocument();
    expect(screen.queryByText('Time to first token')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cost explanation' })).toBeInTheDocument();
  });

  it('真实零费用仍显示为 0.0 USD', async () => {
    await renderWith([logItem({ quota: '0', amount: '0.0' })]);
    const table = await screen.findByRole('table');
    expect(within(table).getByText('0.0 USD')).toBeInTheDocument();
    expect(within(table).getByText(/额度：0 quota/)).toBeInTheDocument();
  });
});
