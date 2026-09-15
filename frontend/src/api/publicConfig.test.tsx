import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import type { ReactNode } from 'react';
import { PUBLIC_CONFIG_QUERY_KEY, fetchPublicConfig, publicConfigSchema } from './publicConfig';
import { usePublicConfig } from './usePublicConfig';
import { portalRequest } from './portalClient';

vi.mock('./portalClient', () => ({
  portalRequest: vi.fn(),
}));

const mockedRequest = vi.mocked(portalRequest);

function wrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

describe('公开配置 schema', () => {
  it('接受有效地址与空数组', () => {
    expect(() =>
      publicConfigSchema.parse({
        siteName: 'Lang API',
        publicationMode: 'PREVIEW',
        siteUrl: '',
        supportUrl: '',
        supportedRegions: [],
        enabledLocales: [],
        apiBaseUrls: [],
      }),
    ).not.toThrow();
    expect(() =>
      publicConfigSchema.parse({
        siteName: 'Lang API',
        publicationMode: 'PREVIEW',
        siteUrl: '',
        supportUrl: '',
        supportedRegions: [],
        enabledLocales: [],
        apiBaseUrls: [{ protocol: 'OPENAI', url: 'https://api.example.com/v1' }],
      }),
    ).not.toThrow();
  });

  it('拒绝缺失站名与非法地址', () => {
    expect(() =>
      publicConfigSchema.parse({
        siteName: '',
        publicationMode: 'PREVIEW',
        siteUrl: '',
        supportUrl: '',
        supportedRegions: [],
        enabledLocales: [],
        apiBaseUrls: [],
      }),
    ).toThrow();
    expect(() =>
      publicConfigSchema.parse({
        siteName: 'Lang API',
        publicationMode: 'PREVIEW',
        siteUrl: '',
        supportUrl: '',
        supportedRegions: [],
        enabledLocales: [],
        apiBaseUrls: [{ protocol: 'openai', url: 'https://api.example.com/v1' }],
      }),
    ).toThrow();
    expect(() =>
      publicConfigSchema.parse({
        siteName: 'Lang API',
        publicationMode: 'PREVIEW',
        siteUrl: '',
        supportUrl: '',
        supportedRegions: [],
        enabledLocales: [],
        apiBaseUrls: [{ protocol: 'OPENAI', url: 'https://user@evil.com/v1' }],
      }),
    ).toThrow();
  });

  it('接受后端发布字段并拒绝非法发布模式与语言', () => {
    expect(() =>
      publicConfigSchema.parse({
        siteName: 'Lang API',
        publicationMode: 'PREVIEW',
        siteUrl: '',
        supportUrl: '',
        supportedRegions: [],
        enabledLocales: [],
        apiBaseUrls: [],
      }),
    ).not.toThrow();
    expect(() =>
      publicConfigSchema.parse({
        siteName: 'Lang API',
        publicationMode: 'PUBLIC',
        siteUrl: 'https://portal.example',
        supportUrl: 'mailto:support@portal.example',
        supportedRegions: ['CN'],
        enabledLocales: ['zh-CN'],
        apiBaseUrls: [],
      }),
    ).not.toThrow();
    expect(() =>
      publicConfigSchema.parse({
        siteName: 'Lang API',
        publicationMode: 'BETA',
        siteUrl: '',
        supportUrl: '',
        supportedRegions: [],
        enabledLocales: [],
        apiBaseUrls: [],
      }),
    ).toThrow();
    expect(() =>
      publicConfigSchema.parse({
        siteName: 'Lang API',
        publicationMode: 'PREVIEW',
        siteUrl: '',
        supportUrl: '',
        supportedRegions: ['CHN'],
        enabledLocales: ['fr-FR'],
        apiBaseUrls: [],
      }),
    ).toThrow();
  });
});

describe('公开配置查询', () => {
  beforeEach(() => {
    mockedRequest.mockReset();
  });

  it('使用固定 key 并把取消信号传给客户端', async () => {
    mockedRequest.mockResolvedValue({ data: { siteName: 'Lang API', publicationMode: 'PREVIEW', siteUrl: '', supportUrl: '', supportedRegions: [], enabledLocales: [], apiBaseUrls: [] }, requestId: 'req-1' });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(() => usePublicConfig(), { wrapper: wrapper(client) });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(PUBLIC_CONFIG_QUERY_KEY).toEqual(['portal', 'public-config']);
    expect(mockedRequest).toHaveBeenCalledTimes(1);
    const options = mockedRequest.mock.calls[0][2] as { signal?: AbortSignal };
    expect(options?.signal).toBeInstanceOf(AbortSignal);
    expect(result.current.data).toEqual({
      data: { siteName: 'Lang API', publicationMode: 'PREVIEW', siteUrl: '', supportUrl: '', supportedRegions: [], enabledLocales: [], apiBaseUrls: [] },
      requestId: 'req-1',
    });
  });

  it('失败时不自动重试，只等手动 refetch', async () => {
    mockedRequest.mockRejectedValue(new Error('网络失败'));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(() => usePublicConfig(), { wrapper: wrapper(client) });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(mockedRequest).toHaveBeenCalledTimes(1);
    mockedRequest.mockResolvedValue({ data: { siteName: 'Lang API', publicationMode: 'PREVIEW', siteUrl: '', supportUrl: '', supportedRegions: [], enabledLocales: [], apiBaseUrls: [] }, requestId: 'req-2' });
    await result.current.refetch();
    expect(mockedRequest).toHaveBeenCalledTimes(2);
  });

  it('fetchPublicConfig 透传信号', async () => {
    mockedRequest.mockResolvedValue({ data: { siteName: 'S', publicationMode: 'PREVIEW', siteUrl: '', supportUrl: '', supportedRegions: [], enabledLocales: [], apiBaseUrls: [] }, requestId: 'r' });
    const controller = new AbortController();
    await fetchPublicConfig(controller.signal);
    expect(mockedRequest.mock.calls[0][2]).toMatchObject({ signal: controller.signal });
  });
});
