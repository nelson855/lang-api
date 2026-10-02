import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PortalApiError } from '../../api/envelope';
import { useModelDetailQuery, useModelProvidersQuery } from './useCatalogQueries';
import {
  modelDetailQueryKey,
  type CatalogDetailResponse,
  type CatalogProvidersResponse,
} from '../../api/models';

vi.mock('../../api/models', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/models')>();
  return { ...actual, fetchModelDetail: vi.fn(), fetchModelProviders: vi.fn() };
});

const { fetchModelDetail, fetchModelProviders } = await import('../../api/models');
const detailMock = vi.mocked(fetchModelDetail);
const providersMock = vi.mocked(fetchModelProviders);

function wrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

function newClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

/** 读取详情缓存中的模型 ID；未缓存时返回 undefined。 */
function cachedModelId(client: QueryClient, modelRef: string): string | undefined {
  const entry = client.getQueryData<{ data: CatalogDetailResponse; requestId: string }>(
    modelDetailQueryKey(modelRef),
  );
  return entry?.data.model.id;
}

function detailBody(id: string): { data: CatalogDetailResponse; requestId: string } {
  return {
    requestId: 'req-detail',
    data: {
      pricingVersion: 'p2-2026-09-22-a',
      model: {
        id,
        displayName: null,
        provider: 'Example',
        availability: 'AVAILABLE',
        pricing: null,
        contextWindowTokens: null,
        maxOutputTokens: null,
        inputModalities: null,
        outputModalities: null,
        capabilities: { toolCalling: null, reasoning: null, structuredOutput: null, attachments: null },
        releaseDate: null,
        description: null,
        tags: null,
        sortOrder: null,
        enhancedPricing: null,
      },
    },
  };
}

function providersBody(): { data: CatalogProvidersResponse; requestId: string } {
  return {
    requestId: 'req-providers',
    data: {
      pricingVersion: 'p2-2026-09-22-a',
      providers: [{ value: 'Example', label: 'Example', modelCount: 1 }],
    },
  };
}

describe('useModelProvidersQuery', () => {
  beforeEach(() => providersMock.mockReset());

  it('匿名只读加载供应商选项', async () => {
    providersMock.mockResolvedValueOnce(providersBody());
    const client = newClient();
    const { result } = renderHook(() => useModelProvidersQuery(), { wrapper: wrapper(client) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data.providers).toHaveLength(1);
    expect(providersMock).toHaveBeenCalledTimes(1);
  });

  it('失败不重试并保留错误类型', async () => {
    providersMock.mockRejectedValueOnce(new PortalApiError(502, 'UPSTREAM_ERROR', '上游不可用'));
    const client = newClient();
    const { result } = renderHook(() => useModelProvidersQuery(), { wrapper: wrapper(client) });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBeInstanceOf(PortalApiError);
    expect(providersMock).toHaveBeenCalledTimes(1);
  });

  it('透传 AbortSignal', async () => {
    const seen: AbortSignal[] = [];
    providersMock.mockImplementation(async (signal) => {
      if (signal) {
        seen.push(signal);
      }
      return providersBody();
    });
    const client = newClient();
    const { result } = renderHook(() => useModelProvidersQuery(), { wrapper: wrapper(client) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(seen.length).toBe(1);
    expect(typeof seen[0].aborted).toBe('boolean');
  });
});

describe('useModelDetailQuery', () => {
  beforeEach(() => detailMock.mockReset());

  it('按引用加载详情并透出成功响应', async () => {
    detailMock.mockResolvedValueOnce(detailBody('abc'));
    const client = newClient();
    const { result } = renderHook(() => useModelDetailQuery('YWJj'), { wrapper: wrapper(client) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data.model.id).toBe('abc');
  });

  it('非法引用不发起请求', async () => {
    const client = newClient();
    const { result } = renderHook(() => useModelDetailQuery('YWJj='), { wrapper: wrapper(client) });
    await waitFor(() => expect(result.current.fetchStatus).toBe('idle'));
    expect(detailMock).not.toHaveBeenCalled();
    expect(result.current.data).toBeUndefined();
    expect(cachedModelId(client, 'YWJj=')).toBeUndefined();
  });

  it('404 透出为 PortalApiError', async () => {
    detailMock.mockRejectedValueOnce(new PortalApiError(404, 'NOT_FOUND', '模型不存在'));
    const client = newClient();
    const { result } = renderHook(() => useModelDetailQuery('YWJj'), { wrapper: wrapper(client) });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect((result.current.error as PortalApiError).status).toBe(404);
  });

  it('取消请求不污染缓存', async () => {
    const abortError = Object.assign(new Error('aborted'), { name: 'AbortError' });
    detailMock.mockRejectedValueOnce(abortError);
    const client = newClient();
    const { result } = renderHook(() => useModelDetailQuery('YWJj'), { wrapper: wrapper(client) });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(cachedModelId(client, 'YWJj')).toBeUndefined();
  });

  it('不同引用各自缓存，互不覆盖', async () => {
    detailMock.mockResolvedValueOnce(detailBody('abc'));
    detailMock.mockResolvedValueOnce(detailBody('xyz'));
    const client = newClient();
    const first = renderHook(() => useModelDetailQuery('YWJj'), { wrapper: wrapper(client) });
    await waitFor(() => expect(first.result.current.isSuccess).toBe(true));
    const second = renderHook(() => useModelDetailQuery('eHl6'), { wrapper: wrapper(client) });
    await waitFor(() => expect(second.result.current.isSuccess).toBe(true));

    expect(cachedModelId(client, 'YWJj')).toBe('abc');
    expect(cachedModelId(client, 'eHl6')).toBe('xyz');
    expect(detailMock).toHaveBeenCalledTimes(2);
  });

  it('模型切换后旧响应不覆盖新模型缓存', async () => {
    const gates: Array<(value: { data: CatalogDetailResponse; requestId: string }) => void> = [];
    detailMock.mockImplementationOnce(
      () => new Promise((resolve) => { gates.push(resolve); }),
    );
    detailMock.mockResolvedValueOnce(detailBody('xyz'));

    const client = newClient();
    renderHook(() => useModelDetailQuery('YWJj'), { wrapper: wrapper(client) });
    expect(cachedModelId(client, 'YWJj')).toBeUndefined();

    renderHook(() => useModelDetailQuery('eHl6'), { wrapper: wrapper(client) });
    await waitFor(() => expect(cachedModelId(client, 'eHl6')).toBe('xyz'));

    // A 的迟到响应写进自己的缓存键，不得覆盖 B。
    for (const gate of gates) gate(detailBody('abc'));
    await waitFor(() => expect(cachedModelId(client, 'YWJj')).toBe('abc'));
    expect(cachedModelId(client, 'eHl6')).toBe('xyz');
  });
});
