import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  buildTopupsUrl,
  fetchTopupOptions,
  fetchTopupPage,
  updateProfile,
} from './wallet';
import { InvalidPortalResponseError, PortalNetworkError } from './envelope';

function response(data: unknown) {
  return new Response(JSON.stringify({ requestId: 'req-wallet', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

const record = {
  orderId: 'ORD-1',
  requestedAmount: '10.5',
  currency: 'USD',
  paymentMethod: 'ALIPAY',
  status: 'SUCCEEDED',
  createdAt: '2023-11-14T22:13:20Z',
  completedAt: '2023-11-14T22:15:00Z',
};

describe('钱包与资料更新 Portal API 边界', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn()));
  afterEach(() => vi.unstubAllGlobals());

  it('充值能力关闭状态字段严格且不接受上游残留字段', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      response({ enabled: false, methods: [], currency: 'USD', reason: 'NOT_CONFIGURED' }),
    );

    const result = await fetchTopupOptions();
    expect(result.data).toEqual({
      enabled: false,
      methods: [],
      currency: 'USD',
      reason: 'NOT_CONFIGURED',
    });
  });

  it('拒绝带有残留支付配置或未知原因的能力响应', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      response({ enabled: false, methods: [], currency: 'USD', reason: 'NOT_CONFIGURED', pay_methods: ['alipay'] }),
    );
    await expect(fetchTopupOptions()).rejects.toBeInstanceOf(InvalidPortalResponseError);

    vi.mocked(fetch).mockResolvedValueOnce(
      response({ enabled: true, methods: ['alipay'], currency: 'USD', reason: 'READY' }),
    );
    await expect(fetchTopupOptions()).rejects.toBeInstanceOf(InvalidPortalResponseError);
  });

  it('充值记录分页字段严格：十进制金额、枚举与可空完成时间', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      response({ items: [{ ...record, completedAt: null, status: 'PENDING' }], page: 1, pageSize: 20, total: 0 }),
    );

    const result = await fetchTopupPage(1, 20);
    expect(result.data.items[0]).toMatchObject({ orderId: 'ORD-1', status: 'PENDING', completedAt: null });
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/portal/api/account/topups?page=1&pageSize=20');
  });

  it('拒绝金额非十进制、未知状态或携带上游内部字段的记录页', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      response({ items: [{ ...record, requestedAmount: 'ten' }], page: 1, pageSize: 20, total: 1 }),
    );
    await expect(fetchTopupPage(1, 20)).rejects.toBeInstanceOf(InvalidPortalResponseError);

    vi.mocked(fetch).mockResolvedValueOnce(
      response({ items: [{ ...record, status: 'weird' }], page: 1, pageSize: 20, total: 1 }),
    );
    await expect(fetchTopupPage(1, 20)).rejects.toBeInstanceOf(InvalidPortalResponseError);

    vi.mocked(fetch).mockResolvedValueOnce(
      response({ items: [{ ...record, user_id: 42, money: '1' }], page: 1, pageSize: 20, total: 1 }),
    );
    await expect(fetchTopupPage(1, 20)).rejects.toBeInstanceOf(InvalidPortalResponseError);
  });

  it('充值记录地址只接受正整数分页', () => {
    expect(buildTopupsUrl(2, 50)).toBe('/portal/api/account/topups?page=2&pageSize=50');
    expect(() => buildTopupsUrl(0, 20)).toThrow();
    expect(() => buildTopupsUrl(1, 101)).toThrow();
  });

  it('资料更新用 PUT 发送白名单字段且网络失败时不重放', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock
      .mockResolvedValueOnce(response({ token: 'csrf-token' }))
      .mockResolvedValueOnce(
        response({ id: 42, username: 'newname', displayName: 'New Name', email: null }),
      );

    const result = await updateProfile({
      username: 'newname',
      displayName: 'New Name',
      currentPassword: 'correct-123',
    });

    expect(result.data.username).toBe('newname');
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1][0]).toBe('/portal/api/profile');
    expect(fetchMock.mock.calls[1][1]?.method).toBe('PUT');
    const sent = JSON.parse(fetchMock.mock.calls[1][1]?.body as string) as Record<string, unknown>;
    expect(Object.keys(sent).sort()).toEqual(['currentPassword', 'displayName', 'username']);
    expect((fetchMock.mock.calls[1][1]?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('csrf-token');
  });

  it('资料更新网络失败只尝试一次且拒绝上游多余字段', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(response({ token: 'csrf-token' }))
      .mockRejectedValueOnce(new TypeError('offline'));

    await expect(
      updateProfile({ username: 'newname', displayName: null, currentPassword: 'correct-123' }),
    ).rejects.toBeInstanceOf(PortalNetworkError);
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2);

    vi.mocked(fetch)
      .mockResolvedValueOnce(response({ token: 'csrf-token' }))
      .mockResolvedValueOnce(
        response({ id: 42, username: 'newname', displayName: null, email: null, role: 1 }),
      );
    await expect(
      updateProfile({ username: 'newname', displayName: null, currentPassword: 'correct-123' }),
    ).rejects.toBeInstanceOf(InvalidPortalResponseError);
  });
});
