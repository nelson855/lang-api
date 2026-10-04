import { useQuery } from '@tanstack/react-query';
import {
  MODEL_PROVIDERS_QUERY_KEY,
  fetchModelDetail,
  fetchModelProviders,
  modelDetailQueryKey,
} from '../../api/models';
import { decodeModelRef } from './modelRef';

/**
 * 目录相关查询保持与列表一致的既有策略：30 秒 staleTime、5 分钟 gcTime、
 * 不自动重试、不因窗口聚焦重取。详情按 modelRef 隔离缓存，非法引用不发请求。
 */

const CATALOG_QUERY_POLICY = {
  staleTime: 30_000,
  gcTime: 5 * 60_000,
  retry: false,
  refetchOnWindowFocus: false,
} as const;

export function useModelProvidersQuery() {
  return useQuery({
    queryKey: MODEL_PROVIDERS_QUERY_KEY,
    queryFn: ({ signal }) => fetchModelProviders(signal),
    ...CATALOG_QUERY_POLICY,
  });
}

export function useModelDetailQuery(modelRef: string | null) {
  const valid = modelRef !== null && decodeModelRef(modelRef) !== null;
  return useQuery({
    queryKey: modelDetailQueryKey(modelRef ?? ''),
    queryFn: ({ signal }) => fetchModelDetail(modelRef as string, signal),
    enabled: valid,
    ...CATALOG_QUERY_POLICY,
  });
}
