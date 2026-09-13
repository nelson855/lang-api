import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';
import { AUTH_PROFILE_QUERY_KEY } from '../../api/auth';
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
