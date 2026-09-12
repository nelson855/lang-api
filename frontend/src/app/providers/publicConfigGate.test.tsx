import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import type { ReactNode } from 'react';
import { PortalApiError } from '../../api/envelope';
import { portalRequest } from '../../api/portalClient';
import { PublicConfigGate, usePublicConfigData } from './publicConfigGate';

vi.mock('../../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

const mockedRequest = vi.mocked(portalRequest);

function setup(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

function SiteNameProbe() {
  const config = usePublicConfigData();
  return <p>{`站点:${config.siteName} 地址数:${config.apiBaseUrls.length}`}</p>;
}

describe('PublicConfigGate 启动门禁', () => {
  beforeEach(() => {
    mockedRequest.mockReset();
  });

  it('加载期间显示启动状态', () => {
    mockedRequest.mockReturnValue(new Promise(() => {}));
    setup(
      <PublicConfigGate>
        <SiteNameProbe />
      </PublicConfigGate>,
    );
    expect(screen.getByText(/正在启动/)).toBeInTheDocument();
  });

  it('成功后向子树提供只读运行时配置', async () => {
    mockedRequest.mockResolvedValue({
      data: { siteName: 'Lang API', apiBaseUrls: [] },
      requestId: 'req-1',
    });
    setup(
      <PublicConfigGate>
        <SiteNameProbe />
      </PublicConfigGate>,
    );
    await waitFor(() => expect(screen.getByText('站点:Lang API 地址数:0')).toBeInTheDocument());
  });

  it('空地址视为合法状态而非示例域名', async () => {
    mockedRequest.mockResolvedValue({
      data: { siteName: 'Lang API', apiBaseUrls: [] },
      requestId: 'req-1',
    });
    setup(
      <PublicConfigGate>
        <SiteNameProbe />
      </PublicConfigGate>,
    );
    await waitFor(() => expect(screen.getByText(/地址数:0/)).toBeInTheDocument());
    expect(document.body.textContent).not.toMatch(/example\.com/);
  });

  it('失败显示 requestId 并支持手动重试', async () => {
    mockedRequest.mockRejectedValueOnce(new PortalApiError(500, 'E', '服务失败', 'req-9'));
    mockedRequest.mockResolvedValueOnce({
      data: { siteName: 'Lang API', apiBaseUrls: [] },
      requestId: 'req-10',
    });
    setup(
      <PublicConfigGate>
        <SiteNameProbe />
      </PublicConfigGate>,
    );
    await waitFor(() => expect(screen.getByText(/req-9/)).toBeInTheDocument());
    expect(mockedRequest).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: /重试/ }));
    await waitFor(() => expect(screen.getByText(/站点:Lang API/)).toBeInTheDocument());
    expect(mockedRequest).toHaveBeenCalledTimes(2);
  });
});
