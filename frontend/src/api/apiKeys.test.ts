import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  apiKeyPageSchema,
  apiKeySchema,
  apiKeyStatusSchema,
  createApiKeyResponseSchema,
  revealSecretSchema,
} from './apiKeys';
import { InvalidPortalResponseError, PortalNetworkError } from './envelope';

function response(data: unknown) {
  return new Response(JSON.stringify({ requestId: 'req-keys', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function validKey() {
  return {
    id: 7,
    name: 'probe',
    maskedKey: 'sk-fN95**********CMHQ',
    status: 'enabled',
    createdAt: '2026-09-12T02:40:07Z',
    expiresAt: null,
    quota: { unlimited: false, remaining: 100, unit: 'quota' },
    usedQuota: { value: 3, unit: 'quota' },
    modelRestrictions: { enabled: true, models: ['gpt-4o'] },
    allowedIps: ['1.1.1.1'],
  };
}

describe('API Key 运行时模型', () => {
  it('接受合法列表项并拒绝上游字段', () => {
    expect(apiKeySchema.safeParse(validKey()).success).toBe(true);
    expect(apiKeySchema.safeParse({ ...validKey(), remain_quota: 100 }).success).toBe(false);
    expect(apiKeySchema.safeParse({ ...validKey(), group: 'default' }).success).toBe(false);
    expect(apiKeySchema.safeParse({ ...validKey(), key: 'sk-full' }).success).toBe(false);
  });

  it('拒绝非法枚举、非 ISO 时间与无单位额度', () => {
    expect(apiKeyStatusSchema.safeParse('sleeping').success).toBe(false);
    expect(apiKeySchema.safeParse({ ...validKey(), status: 'sleeping' }).success).toBe(false);
    expect(apiKeySchema.safeParse({ ...validKey(), createdAt: '昨天' }).success).toBe(false);
    expect(
      apiKeySchema.safeParse({ ...validKey(), quota: { unlimited: false, remaining: 100 } }).success,
    ).toBe(false);
    expect(
      apiKeyPageSchema.safeParse({ items: [], page: 0, pageSize: 20, total: 0 }).success,
    ).toBe(false);
  });

  it('创建响应不含编号与明文，取回响应要求单前缀明文', () => {
    expect(createApiKeyResponseSchema.safeParse({ created: true }).success).toBe(true);
    expect(createApiKeyResponseSchema.safeParse({ created: true, id: 7 }).success).toBe(false);
    expect(createApiKeyResponseSchema.safeParse({ created: true, secret: 'sk-x' }).success).toBe(false);
    expect(revealSecretSchema.safeParse({ secret: 'sk-abc' }).success).toBe(true);
    expect(revealSecretSchema.safeParse({ secret: 'abc' }).success).toBe(false);
    expect(revealSecretSchema.safeParse({ secret: 'sk-sk-abc' }).success).toBe(false);
  });
});

describe('API Key Portal 请求', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn()));
  afterEach(() => vi.unstubAllGlobals());

  it('创建先引导 CSRF 且只走相对路径', async () => {
    const { createApiKey } = await import('./apiKeys');
    const fetchMock = vi.mocked(fetch);
    fetchMock
      .mockResolvedValueOnce(response({ token: 'csrf-token' }))
      .mockResolvedValueOnce(response({ created: true }));

    const result = await createApiKey({ name: 'key-1' });

    expect(result.data).toEqual({ created: true });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1][0]).toBe('/portal/api/api-keys');
    expect(
      (fetchMock.mock.calls[1][1]?.headers as Record<string, string>)['X-XSRF-TOKEN'],
    ).toBe('csrf-token');
  });

  it('列表透传查询串与取消信号且不自动重试', async () => {
    const { listApiKeys } = await import('./apiKeys');
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(
      response({ items: [], page: 1, pageSize: 20, total: 0 }),
    );
    const controller = new AbortController();

    await listApiKeys({ page: 1, pageSize: 20, name: 'probe', status: 'enabled' }, controller.signal);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toBe('/portal/api/api-keys?page=1&pageSize=20&name=probe&status=enabled');
    expect(fetchMock.mock.calls[0][1]?.signal).toBe(controller.signal);
  });

  it('取消的列表请求转为取消错误且网络失败不重放', async () => {
    const { listApiKeys } = await import('./apiKeys');
    const fetchMock = vi.mocked(fetch);
    const controller = new AbortController();
    controller.abort();
    fetchMock.mockRejectedValueOnce(new DOMException('aborted', 'AbortError'));

    await expect(listApiKeys({ page: 1, pageSize: 20 }, controller.signal)).rejects.toMatchObject({
      name: 'PortalCancelledError',
    });

    fetchMock.mockRejectedValueOnce(new TypeError('offline'));
    await expect(listApiKeys({ page: 1, pageSize: 20 })).rejects.toBeInstanceOf(PortalNetworkError);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('取回明文使用专用路径且非法响应被拒绝', async () => {    const { revealApiKey } = await import('./apiKeys');
    const fetchMock = vi.mocked(fetch);
    fetchMock
      .mockResolvedValueOnce(response({ token: 'csrf-token' }))
      .mockResolvedValueOnce(response({ secret: 'sk-abc' }));

    const result = await revealApiKey(7);

    expect(result.data).toEqual({ secret: 'sk-abc' });
    expect(fetchMock.mock.calls[1][0]).toBe('/portal/api/api-keys/7/reveal');

    fetchMock
      .mockResolvedValueOnce(response({ token: 'csrf-token' }))
      .mockResolvedValueOnce(response({ secret: 'no-prefix' }));
    await expect(revealApiKey(8)).rejects.toBeInstanceOf(InvalidPortalResponseError);
  });

  it('取证明文不经过查询缓存且地址不带明文', async () => {
    const { revealApiKey } = await import('./apiKeys');
    const { QueryClient } = await import('@tanstack/react-query');
    const fetchMock = vi.mocked(fetch);
    fetchMock
      .mockResolvedValueOnce(response({ token: 'csrf-token' }))
      .mockResolvedValueOnce(response({ secret: 'sk-abc' }));
    const client = new QueryClient();

    const result = await revealApiKey(7);

    expect(result.data).toEqual({ secret: 'sk-abc' });
    expect(client.getQueryCache().getAll()).toHaveLength(0);
    for (const call of fetchMock.mock.calls) {
      expect(String(call[0])).not.toContain('sk-abc');
    }
  });
});
