import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchProfile, login, type AuthProfile } from './auth';
import { InvalidPortalResponseError, PortalNetworkError } from './envelope';

function response(data: unknown) {
  return new Response(JSON.stringify({ requestId: 'req-auth', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('认证 Portal API 边界', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn()));
  afterEach(() => vi.unstubAllGlobals());

  it('写操作先获取 CSRF，再仅向相对 Portal 路径发送请求且不自动重试', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock
      .mockResolvedValueOnce(response({ token: 'csrf-token' }))
      .mockResolvedValueOnce(response({ id: 42, username: 'ordinary', displayName: 'Ordinary', email: null }));

    const result = await login({ username: 'ordinary', password: 'correct-horse' });

    expect(result.data).toEqual<AuthProfile>({
      id: 42,
      username: 'ordinary',
      displayName: 'Ordinary',
      email: null,
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe('/portal/api/auth/csrf');
    expect(fetchMock.mock.calls[1][0]).toBe('/portal/api/auth/login');
    expect((fetchMock.mock.calls[1][1]?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('csrf-token');
  });

  it('拒绝带有上游字段的 profile 响应，并在 CSRF 网络失败时不重放写操作', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(response({ id: 42, username: 'ordinary', displayName: null, email: null, role: 100 }));
    await expect(fetchProfile()).rejects.toBeInstanceOf(InvalidPortalResponseError);

    vi.mocked(fetch).mockRejectedValueOnce(new TypeError('offline'));
    await expect(login({ username: 'ordinary', password: 'correct-horse' })).rejects.toBeInstanceOf(PortalNetworkError);
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2);
  });
});
