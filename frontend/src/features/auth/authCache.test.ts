import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';
import { AUTH_PROFILE_QUERY_KEY } from '../../api/auth';
import { DASHBOARD_QUERY_KEY } from '../../api/dashboard';
import { API_REQUEST_LOGS_QUERY_KEY } from '../../api/requestLogs';
import { USAGE_QUERY_KEY } from '../../api/usage';
import { cacheAuthenticatedProfile, clearAuthenticatedScope } from './authCache';

const profile = { id: 7, username: 'nelson', displayName: 'Nelson', email: 'nelson@example.com' };

describe('认证 profile 缓存', () => {
  it('登录或 refresh 成功时写入同一唯一 profile 缓存', () => {
    const client = new QueryClient();
    const refreshed = { data: profile, requestId: 'req-refresh' };
    cacheAuthenticatedProfile(client, refreshed);
    expect(client.getQueryData(AUTH_PROFILE_QUERY_KEY)).toEqual(refreshed);
  });

  it('logout 或未认证后清理整组认证 scope', () => {
    const client = new QueryClient();
    cacheAuthenticatedProfile(client, { data: profile, requestId: 'req-login' });
    clearAuthenticatedScope(client);
    expect(client.getQueryData(AUTH_PROFILE_QUERY_KEY)).toBeUndefined();
  });
});

describe('认证作用域统一清理', () => {
  it('logout 清空 profile、usage、request-log 与 dashboard 快照', () => {
    const client = new QueryClient();
    cacheAuthenticatedProfile(client, { data: profile, requestId: 'req-auth' });
    client.setQueryData([...USAGE_QUERY_KEY, 7, 'a'], { quota: '1' });
    client.setQueryData([...API_REQUEST_LOGS_QUERY_KEY, 7, 'x'], { total: 0 });
    client.setQueryData([...DASHBOARD_QUERY_KEY, 7, 's'], { data: 'dash' });

    clearAuthenticatedScope(client);

    expect(client.getQueryData(AUTH_PROFILE_QUERY_KEY)).toBeUndefined();
    expect(client.getQueryData([...USAGE_QUERY_KEY, 7, 'a'])).toBeUndefined();
    expect(client.getQueryData([...API_REQUEST_LOGS_QUERY_KEY, 7, 'x'])).toBeUndefined();
    expect(client.getQueryData([...DASHBOARD_QUERY_KEY, 7, 's'])).toBeUndefined();
  });

  it('虽同一命名空间，dashboard 不同用户快照仍独立', () => {
    const client = new QueryClient();
    client.setQueryData([...DASHBOARD_QUERY_KEY, 7, 's'], { data: 'dash-7' });
    client.setQueryData([...DASHBOARD_QUERY_KEY, 9, 's'], { data: 'dash-9' });
    clearAuthenticatedScope(client);
    expect(client.getQueryData([...DASHBOARD_QUERY_KEY, 7, 's'])).toBeUndefined();
    expect(client.getQueryData([...DASHBOARD_QUERY_KEY, 9, 's'])).toBeUndefined();
  });
});
