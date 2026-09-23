import { describe, expect, it } from 'vitest';
import {
  parseRequestLogsSearch,
  serializeRequestLogsSearch,
  isDashboardDrilldownRange,
  DASHBOARD_DRILLDOWN_PRESET,
} from './requestLogsUrl';

describe('requestLogsUrl: 解析与序列化', () => {
  it('空 search 返回默认参数', () => {
    const parsed = parseRequestLogsSearch('');
    expect(parsed.page).toBe(1);
    expect(parsed.pageSize).toBe(20);
    expect(parsed.result).toBe('SUCCESS');
    expect(parsed.keyName).toBe('');
    expect(parsed.model).toBe('');
    expect(parsed.startTime).toBe('');
    expect(parsed.endTime).toBe('');
  });

  it('解析 Dashboard 下钻完整参数', () => {
    const search =
      '?startTime=2026-09-22T00%3A00%3A00.000Z&endTime=2026-09-23T00%3A00%3A00.000Z&result=SUCCESS&keyName=prod-key&model=gpt-4&page=1';
    const parsed = parseRequestLogsSearch(search);
    expect(parsed.startTime).toBe('2026-09-22T00:00:00.000Z');
    expect(parsed.endTime).toBe('2026-09-23T00:00:00.000Z');
    expect(parsed.result).toBe('SUCCESS');
    expect(parsed.keyName).toBe('prod-key');
    expect(parsed.model).toBe('gpt-4');
    expect(parsed.page).toBe(1);
  });

  it('缺少可选 Key/模型时回退空字符串', () => {
    const parsed = parseRequestLogsSearch('?page=2&result=ERROR');
    expect(parsed.keyName).toBe('');
    expect(parsed.model).toBe('');
    expect(parsed.page).toBe(2);
    expect(parsed.result).toBe('ERROR');
  });

  it('非法 result 回退 SUCCESS，非法 page 回退 1', () => {
    const parsed = parseRequestLogsSearch('?result=NOT_A_RESULT&page=not-a-number');
    expect(parsed.result).toBe('SUCCESS');
    expect(parsed.page).toBe(1);
  });

  it('page<=0 或溢出回退 1', () => {
    expect(parseRequestLogsSearch('?page=0').page).toBe(1);
    expect(parseRequestLogsSearch('?page=-3').page).toBe(1);
  });

  it('单边时间视为非法并清空两侧', () => {
    const onlyStart = parseRequestLogsSearch('?startTime=2026-09-22T00%3A00%3A00.000Z');
    expect(onlyStart.startTime).toBe('');
    expect(onlyStart.endTime).toBe('');

    const onlyEnd = parseRequestLogsSearch('?endTime=2026-09-23T00%3A00%3A00.000Z');
    expect(onlyEnd.startTime).toBe('');
    expect(onlyEnd.endTime).toBe('');
  });

  it('非 ISO 时间字符串被丢弃', () => {
    const parsed = parseRequestLogsSearch('?startTime=not-a-time&endTime=2026-09-23');
    expect(parsed.startTime).toBe('');
    expect(parsed.endTime).toBe('');
  });

  it('未知参数被忽略', () => {
    const parsed = parseRequestLogsSearch('?foo=bar&page=3');
    expect(parsed.page).toBe(3);
    expect((parsed as unknown as Record<string, unknown>).foo).toBeUndefined();
  });

  it('keyName/model 被 URL 解码', () => {
    const parsed = parseRequestLogsSearch('?keyName=%E7%94%9F%E4%BA%A7&model=gpt-4o%20mini');
    expect(parsed.keyName).toBe('生产');
    expect(parsed.model).toBe('gpt-4o mini');
  });

  it('序列化与解析互逆，省略默认值', () => {
    const params = parseRequestLogsSearch(
      '?startTime=2026-09-22T00%3A00%3A00.000Z&endTime=2026-09-23T00%3A00%3A00.000Z&result=SUCCESS&keyName=prod&model=gpt-4&page=2',
    );
    const search = serializeRequestLogsSearch(params);
    const reparsed = parseRequestLogsSearch(search);
    expect(reparsed).toEqual(params);
  });

  it('默认参数序列化为空字符串', () => {
    const search = serializeRequestLogsSearch(parseRequestLogsSearch(''));
    expect(search).toBe('');
  });

  it('非空 keyName/model 被 URL 编码', () => {
    const parsed = parseRequestLogsSearch('');
    const withValues = { ...parsed, keyName: '生产', model: 'gpt-4o mini', page: 2 };
    const search = serializeRequestLogsSearch(withValues);
    expect(search).toContain('keyName=%E7%94%9F%E4%BA%A7');
    expect(search).toMatch(/model=gpt-4o(%20|\+)mini/);
    expect(search).toContain('page=2');
  });
});

describe('isDashboardDrilldownRange', () => {
  it('带完整 startTime/endTime 视为 Dashboard 下钻', () => {
    const parsed = parseRequestLogsSearch(
      '?startTime=2026-09-22T00%3A00%3A00.000Z&endTime=2026-09-23T00%3A00%3A00.000Z',
    );
    expect(isDashboardDrilldownRange(parsed)).toBe(true);
  });

  it('无时间参数视为非下钻', () => {
    const parsed = parseRequestLogsSearch('?page=1&result=SUCCESS');
    expect(isDashboardDrilldownRange(parsed)).toBe(false);
  });

  it('DASHBOARD_DRILLDOWN_PRESET 为固定值', () => {
    expect(DASHBOARD_DRILLDOWN_PRESET).toBe('dashboard');
  });
});
