import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { PortalApiError } from '../../api/envelope';
import { API_REQUEST_LOGS_QUERY_KEY } from '../../api/requestLogs';
import { handleRequestLogsQueryError, useRequestLogsQuery } from './useRequestLogs';

vi.mock('../../api/requestLogs', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/requestLogs')>();
  return {
    ...actual,
    listRequestLogs: vi.fn(),
  };
});

const { listRequestLogs } = await import('../../api/requestLogs');
const listMock = vi.mocked(listRequestLogs);

function wrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

function failure(code: string) {
  return new PortalApiError(502, code, code, 'req-1');
}

describe('请求日志查询钩子', () => {
  it('翻页保留筛选只请求一次', async () => {
    listMock.mockResolvedValueOnce({
      data: { items: [], page: 2, pageSize: 20, total: 0 },
      requestId: 'req-1',
    });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    const { result } = renderHook(
      () =>
        useRequestLogsQuery(42, {
          page: 2,
          pageSize: 20,
          result: 'SUCCESS',
          keyName: 'k',
          model: '',
          startTime: '',
          endTime: '',
        }),
      { wrapper: wrapper(client) },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(listMock).toHaveBeenCalledTimes(1);
    expect(client.getQueryData([...API_REQUEST_LOGS_QUERY_KEY, 42, 2, 20, 'SUCCESS', 'k', '', '', ''])).toBeDefined();
  });

  it('会话失效清理日志范围，普通错误保留筛选项', () => {
    const client = new QueryClient();
    client.setQueryData([...API_REQUEST_LOGS_QUERY_KEY, 42, 1, 20, 'SUCCESS', '', '', '', ''], { total: 0 });

    handleRequestLogsQueryError(client, 42, failure('UNAUTHENTICATED'));
    expect(client.getQueryData([...API_REQUEST_LOGS_QUERY_KEY, 42, 1, 20, 'SUCCESS', '', '', '', ''])).toBeUndefined();

    client.setQueryData([...API_REQUEST_LOGS_QUERY_KEY, 42, 1, 20, 'SUCCESS', '', '', '', ''], { total: 0 });
    handleRequestLogsQueryError(client, 42, failure('UPSTREAM_ERROR'));
    expect(client.getQueryData([...API_REQUEST_LOGS_QUERY_KEY, 42, 1, 20, 'SUCCESS', '', '', '', ''])).toEqual({ total: 0 });
  });
});
