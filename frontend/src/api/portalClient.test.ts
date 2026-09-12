import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { z } from 'zod';
import { portalRequest } from './portalClient';
import { PortalApiError, InvalidPortalResponseError, PortalCancelledError } from './envelope';

const dataSchema = z.object({ siteName: z.string() });

function jsonResponse(body: unknown, init?: ResponseInit & { rawHeaders?: Record<string, string> }) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json', ...(init?.headers as Record<string, string>) },
    ...init,
  });
}

describe('统一 Portal API 客户端请求契约', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('发送同源凭证与请求标识并支持取消', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue(jsonResponse({ requestId: 'req-1', data: { siteName: 'Lang API' } }));
    const controller = new AbortController();
    const result = await portalRequest('/portal/api/public-config', dataSchema, {
      signal: controller.signal,
    });
    expect(result).toEqual({ data: { siteName: 'Lang API' }, requestId: 'req-1' });
    const [, init] = fetchMock.mock.calls[0];
    expect(fetchMock.mock.calls[0][0]).toBe('/portal/api/public-config');
    expect(init?.credentials).toBe('same-origin');
    expect((init?.headers as Record<string, string>)['Accept']).toBe('application/json');
    expect((init?.headers as Record<string, string>)['X-Request-Id']).toMatch(
      /^[0-9a-f-]{36}$/i,
    );
    expect(init?.signal).toBe(controller.signal);
  });

  it('失败包装转为统一错误并保留 requestId', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ requestId: 'req-9', error: { code: 'E', message: '失败' } }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    const error = await portalRequest('/portal/api/x', dataSchema).catch((e) => e);
    expect(error).toBeInstanceOf(PortalApiError);
    expect((error as PortalApiError).requestId).toBe('req-9');
  });

  it('HTML 响应不泄露原文', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response('<html>evil</html>', { status: 200, headers: { 'Content-Type': 'text/html' } }),
    );
    const error = await portalRequest('/portal/api/x', dataSchema).catch((e) => e);
    expect(error).toBeInstanceOf(InvalidPortalResponseError);
    expect(String(error?.message ?? error)).not.toContain('evil');
  });

  it('取消请求产生独立错误类型', async () => {
    const abort = new DOMException('aborted', 'AbortError');
    vi.mocked(fetch).mockRejectedValue(abort);
    const error = await portalRequest('/portal/api/x', dataSchema).catch((e) => e);
    expect(error).toBeInstanceOf(PortalCancelledError);
  });
});
