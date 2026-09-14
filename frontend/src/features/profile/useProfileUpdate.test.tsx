import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AUTH_PROFILE_QUERY_KEY } from '../../api/auth';
import { PortalApiError } from '../../api/envelope';
import { WALLET_QUERY_KEY } from '../../api/wallet';
import { USAGE_QUERY_KEY } from '../../api/usage';
import {
  PROFILE_UPDATE_MUTATION_KEY,
  useProfileUpdateMutation,
} from './useProfileUpdate';

vi.mock('../../api/wallet', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/wallet')>();
  return {
    ...actual,
    updateProfile: vi.fn(),
  };
});

vi.mock('../../api/auth', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/auth')>();
  return {
    ...actual,
    fetchCsrf: vi.fn(),
  };
});

const { updateProfile } = await import('../../api/wallet');
const updateMock = vi.mocked(updateProfile);

function client() {
  return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
}

function wrapper(c: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={c}>{children}</QueryClientProvider>;
  };
}

const input = {
  username: 'newname',
  displayName: 'New Name',
  currentPassword: 'correct-123',
  newPassword: 'brand-new-123',
  confirmPassword: 'brand-new-123',
};

const updated = {
  data: { id: 42, username: 'newname', displayName: 'New Name', email: null },
  requestId: 'req-new',
};

describe('资料更新 mutation', () => {
  beforeEach(() => vi.clearAllMocks());
  afterEach(() => vi.unstubAllGlobals());

  it('失败不自动重试且成功时原子替换唯一 profile 缓存', async () => {
    updateMock.mockRejectedValueOnce(new PortalApiError(400, 'INVALID_ARGUMENT', 'bad', 'req-1'));
    const c = client();
    c.setQueryData(AUTH_PROFILE_QUERY_KEY, {
      data: { id: 42, username: 'ordinary', displayName: 'Ordinary', email: null },
      requestId: 'req-old',
    });

    const failed = renderHook(() => useProfileUpdateMutation(), { wrapper: wrapper(c) });
    failed.result.current.mutate(input);

    await waitFor(() => expect(failed.result.current.isError).toBe(true));
    expect(updateMock).toHaveBeenCalledTimes(1);
    expect(c.getQueryData(AUTH_PROFILE_QUERY_KEY)).toMatchObject({
      data: { username: 'ordinary' },
    });

    updateMock.mockResolvedValueOnce(updated);
    const succeeded = renderHook(() => useProfileUpdateMutation(), { wrapper: wrapper(c) });
    succeeded.result.current.mutate(input);

    await waitFor(() => expect(succeeded.result.current.isSuccess).toBe(true));
    expect(c.getQueryData(AUTH_PROFILE_QUERY_KEY)).toEqual(updated);
  });

  it('用户名变化后使用户作用域数据失效，结果未知时保留旧缓存并带回 requestId', async () => {
    const c = client();
    c.setQueryData([...USAGE_QUERY_KEY, 42, 'balance'], { quota: '1' });
    c.setQueryData([...WALLET_QUERY_KEY, 42, 'options'], { enabled: false });
    updateMock.mockResolvedValueOnce(updated);

    const { result } = renderHook(() => useProfileUpdateMutation(), { wrapper: wrapper(c) });
    result.current.mutate(input);

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(c.getQueryState([...USAGE_QUERY_KEY, 42, 'balance'])?.isInvalidated).toBe(true);
    expect(c.getQueryState([...WALLET_QUERY_KEY, 42, 'options'])?.isInvalidated).toBe(true);
  });

  it('结果未知时不重放且错误带回 requestId 供显式重读', async () => {
    updateMock.mockRejectedValueOnce(
      new PortalApiError(502, 'OPERATION_RESULT_UNKNOWN', 'unknown', 'req-unknown'),
    );
    const c = client();
    c.setQueryData(AUTH_PROFILE_QUERY_KEY, {
      data: { id: 42, username: 'ordinary', displayName: 'Ordinary', email: null },
      requestId: 'req-old',
    });

    const { result } = renderHook(() => useProfileUpdateMutation(), { wrapper: wrapper(c) });
    result.current.mutate(input);

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(updateMock).toHaveBeenCalledTimes(1);
    const error = result.current.error as PortalApiError;
    expect(error.code).toBe('OPERATION_RESULT_UNKNOWN');
    expect(error.requestId).toBe('req-unknown');
    expect(c.getQueryData(AUTH_PROFILE_QUERY_KEY)).toMatchObject({
      data: { username: 'ordinary' },
    });
  });

  it('密码不进入查询与 mutation 缓存', async () => {
    updateMock.mockResolvedValueOnce(updated);
    const c = client();

    const { result } = renderHook(() => useProfileUpdateMutation(), { wrapper: wrapper(c) });
    result.current.mutate(input);

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    await waitFor(() =>
      expect(
        c.getMutationCache().findAll({ mutationKey: PROFILE_UPDATE_MUTATION_KEY }),
      ).toHaveLength(0),
    );
    const snapshot = JSON.stringify(
      c.getQueryCache().getAll().map((q) => q.state.data),
    );
    expect(snapshot).not.toContain('correct-123');
    expect(snapshot).not.toContain('brand-new-123');
  });
});
