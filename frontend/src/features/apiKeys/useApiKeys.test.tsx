import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { AUTH_PROFILE_QUERY_KEY } from '../../api/auth';
import { PortalApiError } from '../../api/envelope';
import { handleApiKeysMutationError, useApiKeysQuery, useCreateApiKey } from './useApiKeys';

vi.mock('../../api/apiKeys', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/apiKeys')>();
  return {
    ...actual,
    listApiKeys: vi.fn(),
    createApiKey: vi.fn(),
  };
});

const { listApiKeys, createApiKey } = await import('../../api/apiKeys');
const listMock = vi.mocked(listApiKeys);
const createMock = vi.mocked(createApiKey);

function wrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

function failure(code: string) {
  return new PortalApiError(502, code, code, 'req-1');
}

describe('API Key 查询与变更钩子', () => {
  it('查询失败只请求一次且透出错误', async () => {
    listMock.mockRejectedValueOnce(failure('UPSTREAM_ERROR'));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    const { result } = renderHook(
      () => useApiKeysQuery(42, { page: 1, pageSize: 20, name: '', status: '' }),
      { wrapper: wrapper(client) },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(listMock).toHaveBeenCalledTimes(1);
    expect(result.current.error).toMatchObject({ code: 'UPSTREAM_ERROR' });
  });

  it('会话失效清理认证与密钥范围，结果未知保留展示数据', () => {
    const client = new QueryClient();
    client.setQueryData(AUTH_PROFILE_QUERY_KEY, { id: 1 });
    client.setQueryData(['portal', 'api-keys', 42, 1, 20, '', ''], { items: [1] });

    handleApiKeysMutationError(client, 42, failure('UNAUTHENTICATED'));
    expect(client.getQueryData(AUTH_PROFILE_QUERY_KEY)).toBeUndefined();
    expect(client.getQueryData(['portal', 'api-keys', 42, 1, 20, '', ''])).toBeUndefined();

    client.setQueryData(['portal', 'api-keys', 42, 1, 20, '', ''], { items: [1] });
    handleApiKeysMutationError(client, 42, failure('OPERATION_RESULT_UNKNOWN'));
    expect(client.getQueryData(['portal', 'api-keys', 42, 1, 20, '', ''])).toEqual({ items: [1] });
  });

  it('创建成功失效列表且失败不重放', async () => {
    createMock.mockResolvedValueOnce({ data: { created: true as const }, requestId: 'req-2' });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    client.setQueryData(['portal', 'api-keys', 42, 1, 20, '', ''], { items: [] });

    const { result } = renderHook(() => useCreateApiKey(42), { wrapper: wrapper(client) });
    await result.current.mutateAsync({ name: 'key-1' });

    expect(createMock).toHaveBeenCalledTimes(1);
    expect(client.getQueryData(['portal', 'api-keys', 42, 1, 20, '', ''])).toBeUndefined();
  });
});
