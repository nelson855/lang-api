import { describe, expect, it } from 'vitest';
import { buildDashboardRange, DASHBOARD_PRESETS, type DashboardRangeValue } from './dashboardRange';

function seconds(from: string, to: string): number {
  return (Date.parse(to) - Date.parse(from)) / 1000;
}

describe('dashboard 快捷范围生成', () => {
  it('提供 1H/24H/今天/昨天/7D/30D 六个预设常量', () => {
    expect(DASHBOARD_PRESETS).toHaveLength(6);
    const keys = DASHBOARD_PRESETS.map((p) => p.value);
    expect(keys).toEqual(['1h', '24h', 'today', 'yesterday', '7d', '30d']);
  });

  it('1H 为 now 前 1 小时，粒度 FIVE_MINUTES，可请求', () => {
    const now = new Date('2026-09-01T12:34:56.789Z');
    const range = buildDashboardRange('1h', now, 'UTC');
    expect(range.granularity).toBe('FIVE_MINUTES');
    expect(range.enabled).toBe(true);
    expect(range.startTime.endsWith('.000Z')).toBe(true);
    expect(range.endTime.endsWith('.000Z')).toBe(true);
    expect(seconds(range.startTime, range.endTime)).toBe(3600);
  });

  it('24H 为 now 前 24 小时，粒度 HOUR', () => {
    const now = new Date('2026-09-01T12:34:56.789Z');
    const range = buildDashboardRange('24h', now, 'UTC');
    expect(range.granularity).toBe('HOUR');
    expect(seconds(range.startTime, range.endTime)).toBe(24 * 3600);
  });

  it('7D 为 now 前 7 天，粒度 HOUR，可请求', () => {
    const now = new Date('2026-09-01T12:34:56.789Z');
    const range = buildDashboardRange('7d', now, 'Asia/Shanghai');
    expect(range.granularity).toBe('HOUR');
    expect(range.enabled).toBe(true);
    expect(seconds(range.startTime, range.endTime)).toBe(7 * 24 * 3600);
    expect(range.timezone).toBe('Asia/Shanghai');
  });

  it('30D 可见但默认不可请求，粒度 DAY', () => {
    const now = new Date('2026-09-01T12:34:56.789Z');
    const range = buildDashboardRange('30d', now, 'UTC');
    expect(range.granularity).toBe('DAY');
    expect(range.enabled).toBe(false);
    expect(seconds(range.startTime, range.endTime)).toBe(30 * 24 * 3600);
  });

  it('今天：时区自然日 00:00 → now，粒度 HOUR', () => {
    const now = new Date('2026-09-01T20:00:00.000Z'); // Asia/Shanghai 已是 9 月 2 日 04:00
    const range = buildDashboardRange('today', now, 'Asia/Shanghai');
    expect(range.granularity).toBe('HOUR');
    // 上海时区 9/2 自然日 00:00 CST = 9/1 16:00 UTC
    expect(range.startTime).toBe('2026-09-01T16:00:00.000Z');
    expect(range.endTime).toBe('2026-09-01T20:00:00.000Z');
  });

  it('昨天：完整前一自然日，粒度 HOUR，与今天边界不重叠', () => {
    const now = new Date('2026-09-01T20:00:00.000Z');
    const today = buildDashboardRange('today', now, 'Asia/Shanghai');
    const yesterday = buildDashboardRange('yesterday', now, 'Asia/Shanghai');
    expect(yesterday.granularity).toBe('HOUR');
    // 昨天 = 9/1 00:00 CST → 9/2 00:00 CST；今天 start = 9/2 00:00 CST
    expect(yesterday.endTime).toBe(today.startTime);
    expect(yesterday.startTime).toBe('2026-08-31T16:00:00.000Z');
    expect(yesterday.endTime).toBe('2026-09-01T16:00:00.000Z');
  });

  it('UTC 日界：now 在 UTC 零点之后跨自然日', () => {
    const now = new Date('2026-09-02T00:30:00.000Z');
    const today = buildDashboardRange('today', now, 'UTC');
    expect(today.startTime).toBe('2026-09-02T00:00:00.000Z');
    expect(today.endTime).toBe('2026-09-02T00:30:00.000Z');
    const yesterday = buildDashboardRange('yesterday', now, 'UTC');
    expect(yesterday.startTime).toBe('2026-09-01T00:00:00.000Z');
    expect(yesterday.endTime).toBe('2026-09-02T00:00:00.000Z');
  });

  it('America/New_York 春令进：昨天自然日如实跨 23 小时（2026-03-08）', () => {
    const now = new Date('2026-03-09T12:00:00Z'); // EDT，前一天是 3/8（当日 02:00 调快）
    const yesterday = buildDashboardRange('yesterday', now, 'America/New_York');
    // 昨天 = 3/8 完整自然日，因 03-08 凌晨跳表，跨度 23 小时
    expect(seconds(yesterday.startTime, yesterday.endTime)).toBe(23 * 3600);
    // 3/8 00:00 EST = 05:00 UTC；3/9 00:00 EDT = 04:00 UTC
    expect(yesterday.startTime).toBe('2026-03-08T05:00:00.000Z');
    expect(yesterday.endTime).toBe('2026-03-09T04:00:00.000Z');
  });

  it('America/New_York 秋令退：昨天自然日如实跨 25 小时（2026-11-01）', () => {
    const now = new Date('2026-11-02T12:00:00Z'); // EST，前一天是 11/1（当日 02:00 调慢）
    const yesterday = buildDashboardRange('yesterday', now, 'America/New_York');
    // 昨天 = 11/1 完整自然日，因 11-01 凌晨回调，跨度 25 小时
    expect(seconds(yesterday.startTime, yesterday.endTime)).toBe(25 * 3600);
    // 11/1 00:00 EDT = 04:00 UTC；11/2 00:00 EST = 05:00 UTC
    expect(yesterday.startTime).toBe('2026-11-01T04:00:00.000Z');
    expect(yesterday.endTime).toBe('2026-11-02T05:00:00.000Z');
  });

  it('今天与昨天边界按用户时区切分，不采用固定 24h', () => {
    const now = new Date('2026-03-09T06:00:00Z'); // NY 已是 3/9 凌晨（EDT）
    const today = buildDashboardRange('today', now, 'America/New_York');
    expect(today.startTime).toBe('2026-03-09T04:00:00.000Z'); // 3/9 00:00 EDT
    const yesterday = buildDashboardRange('yesterday', now, 'America/New_York');
    expect(yesterday.startTime).toBe('2026-03-08T05:00:00.000Z'); // 3/8 00:00 EST
  });

  it('无效 IANA 时区回退为 UTC', () => {
    const now = new Date('2026-09-01T20:00:00.000Z');
    const range = buildDashboardRange('today', now, 'Mars/Olympus');
    expect(range.timezone).toBe('UTC');
    expect(range.startTime).toBe('2026-09-01T00:00:00.000Z');
  });

  it('返回不可变对象且包含 preset 标识', () => {
    const range: DashboardRangeValue = buildDashboardRange('24h', new Date('2026-09-01T12:00:00Z'), 'UTC');
    expect(range.preset).toBe('24h');
    expect(Object.isFrozen(range)).toBe(true);
  });
});