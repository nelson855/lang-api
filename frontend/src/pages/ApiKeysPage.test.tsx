import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PortalApiError } from '../api/envelope';
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

// 页面级测试聚焦查询时机与状态语义；Radix Select 弹层在 jsdom 内打开存在已知限制，
// 这里把 Select 替换为原生 select 以保留值映射和提交时机断言，弹层键盘与碰撞由 E2E 覆盖。
vi.mock('../components/ui/Select', () => ({
  Select: ({
    options,
    value,
    onValueChange,
    id,
    disabled,
    'aria-label': ariaLabel,
  }: {
    options: Array<{ value: string; label: string; disabled?: boolean }>;
    value: string;
    onValueChange: (value: string) => void;
    id?: string;
    disabled?: boolean;
    'aria-label'?: string;
  }) => (
    <select
      id={id}
      aria-label={ariaLabel}
      disabled={disabled}
      value={value}
      onChange={(event) => onValueChange(event.target.value)}
    >
      {options.map((option) => (
        <option key={option.value} value={option.value} disabled={option.disabled}>
          {option.label}
        </option>
      ))}
    </select>
  ),
}));

const { listApiKeys } = await import('../api/apiKeys');
const listMock = vi.mocked(listApiKeys);

function row(name: string, status: 'enabled' | 'disabled' | 'expired' | 'exhausted' = 'enabled', id = 7) {
  return {
    id,
    name,
    maskedKey: 'sk-fN95**********CMHQ',
    status,
    createdAt: '2026-09-12T02:40:07Z',
    expiresAt: null,
    quota: { unlimited: false, remaining: 100, unit: 'quota' as const },
    usedQuota: { value: 3, unit: 'quota' as const },
    modelRestrictions: { enabled: false, models: [] as string[] },
    allowedIps: [] as string[],
  };
}

function pageResponse(items: ReturnType<typeof row>[], total = items.length) {
  return { data: { items, page: 1, pageSize: 20, total }, requestId: 'req-1' };
}

describe('API Key 列表浏览', () => {
  beforeEach(() => {
    vi.mocked(portalRequest).mockResolvedValue({
      data: { siteName: '测试站', apiBaseUrls: [] },
      requestId: 'req-pages',
    });
  });
  it('加载、首次无密钥与数据行', async () => {
    listMock.mockReturnValueOnce(new Promise(() => {}));
    const { unmount } = renderApp(<ApiKeysPage />, { authStatus: 'authenticated' });
    expect(await screen.findByText(/正在加载/)).toBeInTheDocument();
    unmount();

    listMock.mockResolvedValueOnce(pageResponse([]));
    renderApp(<ApiKeysPage />, { authStatus: 'authenticated' });
    expect(await screen.findByText('还没有 API 密钥')).toBeInTheDocument();
  });

  it('搜索与筛选重置到第一页并发起新查询', async () => {
    const user = userEvent.setup();
    listMock.mockResolvedValue(pageResponse([row('alpha'), row('beta')]));
    renderApp(<ApiKeysPage />, { authStatus: 'authenticated' });
    await screen.findByText('alpha');

    const search = screen.getByLabelText('搜索密钥');
    await user.clear(search);
    await user.type(search, 'beta');
    await waitFor(() => {
      expect(listMock).toHaveBeenCalledWith(
        expect.objectContaining({ page: 1, name: 'beta' }),
        expect.anything(),
      );
    });

    const status = screen.getByLabelText('状态筛选');
    await user.selectOptions(status, 'disabled');
    await waitFor(() => {
      expect(listMock).toHaveBeenCalledWith(
        expect.objectContaining({ page: 1, status: 'disabled' }),
        expect.anything(),
      );
    });
  });

  it('分页切换、错误重试与结果未知提示', async () => {
    const user = userEvent.setup();
    const many = Array.from({ length: 20 }, (_, index) => row(`key-${index}`, 'enabled', index + 1));
    listMock.mockResolvedValueOnce({ data: { items: many, page: 1, pageSize: 20, total: 41 }, requestId: 'req-1' });
    renderApp(<ApiKeysPage />, { authStatus: 'authenticated' });
    await screen.findByText('key-0');

    await user.click(screen.getByRole('button', { name: '下一页' }));
    await waitFor(() => {
      expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ page: 2 }), expect.anything());
    });

    listMock.mockRejectedValueOnce(new PortalApiError(500, 'UPSTREAM_ERROR', '上游异常', 'req-bad'));
    const rerender = renderApp(<ApiKeysPage key="err" />, { authStatus: 'authenticated' });
    await screen.findByText('加载失败');
    await user.click(screen.getByRole('button', { name: '重试' }));
    rerender.unmount();

    listMock.mockRejectedValueOnce(new PortalApiError(502, 'OPERATION_RESULT_UNKNOWN', '结果未知', 'req-unknown'));
    renderApp(<ApiKeysPage key="unknown" />, { authStatus: 'authenticated' });
    const notice = await screen.findByText('结果未知');
    expect(within(notice.closest('div') as HTMLElement).getByText(/req-unknown/)).toBeInTheDocument();
  });
});
