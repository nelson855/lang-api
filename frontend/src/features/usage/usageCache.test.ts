import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';
import { AUTH_PROFILE_QUERY_KEY } from '../../api/auth';
import { API_REQUEST_LOGS_QUERY_KEY } from '../../api/requestLogs';
import { USAGE_QUERY_KEY } from '../../api/usage';
import {
  clearUsageScope,
  normalizeUsageRange,
  usageRangeKey,
} from './usageCache';

describe('用量查询缓存', () => {
  it('同一范围的摘要与趋势共享规范化边界', () => {
    const now = new Date('2026-09-10T12:00:00Z').getTime();
    const range = normalizeUsageRange(undefined, undefined, now);
    expect(range).toEqual({
      startTime: '2026-09-09T12:00:00.000Z',
      endTime: '2026-09-10T12:00:00.000Z',
    });
    expect(usageRangeKey(42, range)).toEqual([
      ...USAGE_QUERY_KEY,
      42,
      range.startTime,
      range.endTime,
    ]);
  });

  it('拒绝超过 30 天的范围', () => {
    expect(() =>
      normalizeUsageRange('2026-08-01T00:00:00.000Z', '2026-09-10T12:00:00.000Z'),
    ).toThrow();
  });

  it('会话结束清理认证与用量范围', () => {
    const client = new QueryClient();
    client.setQueryData(AUTH_PROFILE_QUERY_KEY, { id: 42 });
    client.setQueryData([...USAGE_QUERY_KEY, 42, 'a', 'b'], { quota: '1' });
    client.setQueryData([...API_REQUEST_LOGS_QUERY_KEY, 42, 'x'], { total: 0 });

    clearUsageScope(client);

    expect(client.getQueryData(AUTH_PROFILE_QUERY_KEY)).toBeUndefined();
    expect(client.getQueryData([...USAGE_QUERY_KEY, 42, 'a', 'b'])).toBeUndefined();
    expect(client.getQueryData([...API_REQUEST_LOGS_QUERY_KEY, 42, 'x'])).toBeUndefined();
  });
});
