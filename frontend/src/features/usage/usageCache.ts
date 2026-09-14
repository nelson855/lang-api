import type { QueryClient } from '@tanstack/react-query';
import { AUTH_PROFILE_QUERY_KEY } from '../../api/auth';
import { API_REQUEST_LOGS_QUERY_KEY } from '../../api/requestLogs';
import { USAGE_QUERY_KEY } from '../../api/usage';

export interface NormalizedUsageRange {
  startTime: string;
  endTime: string;
}

const DAY_MS = 24 * 60 * 60 * 1000;
const DEFAULT_RANGE_MS = DAY_MS;
const MAX_RANGE_MS = 30 * DAY_MS;

export function normalizeUsageRange(startTime?: string, endTime?: string, now: number = Date.now()): NormalizedUsageRange {
  const startRaw = startTime?.trim() ? startTime.trim() : '';
  const endRaw = endTime?.trim() ? endTime.trim() : '';
  if (!startRaw && !endRaw) {
    return {
      startTime: new Date(now - DEFAULT_RANGE_MS).toISOString(),
      endTime: new Date(now).toISOString(),
    };
  }
  if (!startRaw || !endRaw) {
    throw new Error('开始与结束时间必须同时提供或同时省略');
  }
  const start = new Date(startRaw).getTime();
  const end = new Date(endRaw).getTime();
  if (!Number.isFinite(start) || !Number.isFinite(end)) {
    throw new Error('时间格式不正确');
  }
  if (!(start < end)) {
    throw new Error('开始时间必须早于结束时间');
  }
  if (end - start > MAX_RANGE_MS) {
    throw new Error('时间跨度不得超过 30 天');
  }
  return {
    startTime: new Date(start).toISOString(),
    endTime: new Date(end).toISOString(),
  };
}

export function usageRangeKey(userId: number, range: NormalizedUsageRange): readonly unknown[] {
  return [...USAGE_QUERY_KEY, userId, range.startTime, range.endTime];
}

export function invalidateUsageScope(client: QueryClient, userId: number) {
  client.removeQueries({ queryKey: [...USAGE_QUERY_KEY, userId] });
}

export function clearUsageScope(client: QueryClient) {
  client.removeQueries({ queryKey: AUTH_PROFILE_QUERY_KEY });
  client.removeQueries({ queryKey: USAGE_QUERY_KEY });
  client.removeQueries({ queryKey: API_REQUEST_LOGS_QUERY_KEY });
}
