import type { QueryClient } from '@tanstack/react-query';
import { API_KEYS_QUERY_KEY } from '../../api/apiKeys';

export interface ApiKeysQueryParams {
  page: number;
  pageSize: number;
  name?: string;
  status?: string;
}

export interface NormalizedApiKeysParams {
  page: number;
  pageSize: number;
  name: string;
  status: string;
}

export function normalizeApiKeysParams(params: ApiKeysQueryParams): NormalizedApiKeysParams {
  return {
    page: params.page,
    pageSize: params.pageSize,
    name: (params.name ?? '').trim(),
    status: (params.status ?? '').trim().toLowerCase(),
  };
}

export function apiKeysListKey(userId: number, params: ApiKeysQueryParams): readonly unknown[] {
  const normalized = normalizeApiKeysParams(params);
  return [
    ...API_KEYS_QUERY_KEY,
    userId,
    normalized.page,
    normalized.pageSize,
    normalized.name,
    normalized.status,
  ];
}

export function withFirstPage(params: NormalizedApiKeysParams): NormalizedApiKeysParams {
  return { ...params, page: 1 };
}

export function invalidateApiKeysScope(client: QueryClient, userId: number) {
  client.removeQueries({ queryKey: [...API_KEYS_QUERY_KEY, userId] });
}

export function fallbackPageAfterDelete(page: number, pageSize: number, totalAfter: number): number {
  if (page > 1 && totalAfter <= (page - 1) * pageSize) {
    return page - 1;
  }
  return page;
}
