import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';
import { API_REQUEST_LOGS_QUERY_KEY } from '../../api/requestLogs';
import {
  invalidateRequestLogsScope,
  normalizeRequestLogsParams,
  requestLogsListKey,
  withFirstPage,
} from './requestLogsCache';

describe('请求日志查询缓存', () => {
  it('查询键包含用户标识与已提交筛选', () => {
    const key = requestLogsListKey(42, {
      page: 2,
      pageSize: 20,
      result: 'ERROR',
      keyName: 'k',
      model: 'm',
      startTime: 's',
      endTime: 'e',
    });
    expect(key).toEqual([...API_REQUEST_LOGS_QUERY_KEY, 42, 2, 20, 'ERROR', 'k', 'm', 's', 'e']);
  });

  it('筛选提交回到第一页', () => {
    const normalized = normalizeRequestLogsParams({ page: 3, pageSize: 20, result: 'SUCCESS' });
    expect(withFirstPage(normalized).page).toBe(1);
  });

  it('清理指定用户的日志范围', () => {
    const client = new QueryClient();
    client.setQueryData([...API_REQUEST_LOGS_QUERY_KEY, 42, 1, 20, 'SUCCESS', '', '', '', ''], { total: 0 });
    client.setQueryData([...API_REQUEST_LOGS_QUERY_KEY, 43, 1, 20, 'SUCCESS', '', '', '', ''], { total: 0 });

    invalidateRequestLogsScope(client, 42);

    expect(client.getQueryData([...API_REQUEST_LOGS_QUERY_KEY, 42, 1, 20, 'SUCCESS', '', '', '', ''])).toBeUndefined();
    expect(client.getQueryData([...API_REQUEST_LOGS_QUERY_KEY, 43, 1, 20, 'SUCCESS', '', '', '', ''])).toEqual({ total: 0 });
  });
});
