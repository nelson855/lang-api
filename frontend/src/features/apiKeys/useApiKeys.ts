import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import {
  createApiKey,
  deleteApiKey,
  fetchApiKey,
  listApiKeys,
  setApiKeyStatus,
  updateApiKey,
  type ApiKeyListParams,
  type CreateApiKeyInput,
  type UpdateApiKeyInput,
} from '../../api/apiKeys';
import { PortalApiError } from '../../api/envelope';
import { clearAuthenticatedScope } from '../auth/authCache';
import {
  apiKeysListKey,
  invalidateApiKeysScope,
  normalizeApiKeysParams,
  type ApiKeysQueryParams,
} from './apiKeysCache';

export function handleApiKeysMutationError(client: QueryClient, userId: number, error: unknown) {
  // 会话失效时清掉认证与密钥范围；其余失败（含结果未知）保留最后一次可信展示，不做乐观更新。
  if (error instanceof PortalApiError && error.code === 'UNAUTHENTICATED') {
    clearAuthenticatedScope(client);
    invalidateApiKeysScope(client, userId);
  }
}

export function useApiKeysQuery(userId: number, params: ApiKeysQueryParams) {
  const normalized = normalizeApiKeysParams(params);
  const listParams: ApiKeyListParams = {
    page: normalized.page,
    pageSize: normalized.pageSize,
    name: normalized.name === '' ? undefined : normalized.name,
    status: normalized.status === '' ? undefined : normalized.status,
  };
  return useQuery({
    queryKey: apiKeysListKey(userId, normalized),
    queryFn: ({ signal }) => listApiKeys(listParams, signal),
    retry: false,
  });
}

export function useApiKeyDetail(userId: number, id: number) {
  return useQuery({
    queryKey: [...apiKeysListKey(userId, { page: 1, pageSize: 1, name: '', status: '' }), 'detail', id],
    queryFn: ({ signal }) => fetchApiKey(id, signal),
    retry: false,
  });
}

function useApiKeyMutation<TData, TVariables>(userId: number, mutationFn: (input: TVariables) => Promise<TData>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn,
    retry: false,
    onSuccess: () => {
      invalidateApiKeysScope(client, userId);
    },
    onError: (error) => {
      handleApiKeysMutationError(client, userId, error);
    },
  });
}

export function useCreateApiKey(userId: number) {
  return useApiKeyMutation(userId, (input: CreateApiKeyInput) => createApiKey(input));
}

export function useUpdateApiKey(userId: number) {
  return useApiKeyMutation(userId, (input: { id: number; patch: UpdateApiKeyInput }) =>
    updateApiKey(input.id, input.patch),
  );
}

export function useSetApiKeyStatus(userId: number) {
  return useApiKeyMutation(userId, (input: { id: number; enabled: boolean }) =>
    setApiKeyStatus(input.id, input.enabled),
  );
}

export function useDeleteApiKey(userId: number) {
  return useApiKeyMutation(userId, (id: number) => deleteApiKey(id));
}
