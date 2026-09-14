import type { QueryClient } from '@tanstack/react-query';
import { API_REQUEST_LOGS_QUERY_KEY } from '../../api/requestLogs';

export interface RequestLogsQueryParams {
  page: number;
  pageSize: number;
  result: string;
  keyName?: string;
  model?: string;
  startTime?: string;
  endTime?: string;
}

export interface NormalizedRequestLogsParams {
  page: number;
  pageSize: number;
  result: string;
  keyName: string;
  model: string;
  startTime: string;
  endTime: string;
}

export function normalizeRequestLogsParams(params: RequestLogsQueryParams): NormalizedRequestLogsParams {
  return {
    page: params.page,
    pageSize: params.pageSize,
    result: (params.result ?? '').trim().toUpperCase() || 'SUCCESS',
    keyName: (params.keyName ?? '').trim(),
    model: (params.model ?? '').trim(),
    startTime: (params.startTime ?? '').trim(),
    endTime: (params.endTime ?? '').trim(),
  };
}

export function requestLogsListKey(userId: number, params: RequestLogsQueryParams): readonly unknown[] {
  const normalized = normalizeRequestLogsParams(params);
  return [
    ...API_REQUEST_LOGS_QUERY_KEY,
    userId,
    normalized.page,
    normalized.pageSize,
    normalized.result,
    normalized.keyName,
    normalized.model,
    normalized.startTime,
    normalized.endTime,
  ];
}

export function withFirstPage(params: NormalizedRequestLogsParams): NormalizedRequestLogsParams {
  return { ...params, page: 1 };
}

export function invalidateRequestLogsScope(client: QueryClient, userId: number) {
  client.removeQueries({ queryKey: [...API_REQUEST_LOGS_QUERY_KEY, userId] });
}
