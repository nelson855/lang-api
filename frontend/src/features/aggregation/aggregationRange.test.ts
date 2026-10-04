import { describe, expect, it } from 'vitest';
import {
  buildAggregationRange,
  normalizeIANATimezone,
  startOfLocalDayUtc,
  truncateToSeconds,
  type AggregationRangePresetDef,
} from './aggregationRange';

function secondsBetween(fromIso: string, toIso: string): number {
  return (Date.parse(toIso) - Date.parse(fromIso)) / 1000;
}

describe('聚合范围内核:通用工具', () => {
  it('truncateToSeconds 把毫秒清零', () => {
    const ms = Date.parse('2026-09-01T12:34:56.789Z');
    expect(truncateToSeconds(ms)).toBe('2026-09-01T12:34:56.000Z');
  });

  it('normalizeIANATimezone 拒绝非法值并回退 UTC', () => {
    expect(normalizeIANATimezone('UTC')).toBe('UTC');
    expect(normalizeIANATimezone('Asia/Shanghai')).toBe('Asia/Shanghai');
    expect(normalizeIANATimezone('Mars/Olympus')).toBe('UTC');
    expect(normalizeIANATimezone('')).toBe('UTC');
    expect(normalizeIANATimezone('   ')).toBe('UTC');
  });

  it('startOfLocalDayUtc 在 UTC 时返回自然日零点', () => {
    const ms = Date.parse('2026-09-02T15:30:00Z');
    const startMs = startOfLocalDayUtc(ms, 'UTC');
    expect(new Date(startMs).toISOString()).toBe('2026-09-02T00:00:00.000Z');
  });
});

describe('聚合范围内核:buildAggregationRange', () => {
  const presets: AggregationRangePresetDef[] = [
    { value: '1h', kind: 'rolling', rollingSeconds: 3600, granularity: 'FIVE_MINUTES', enabled: true, labelKey: 'x.1h' },
    { value: '24h', kind: 'rolling', rollingSeconds: 24 * 3600, granularity: 'HOUR', enabled: true, labelKey: 'x.24h' },
    { value: 'today', kind: 'calendar', rollingSeconds: 0, granularity: 'HOUR', enabled: true, labelKey: 'x.today' },
    { value: 'yesterday', kind: 'calendar', rollingSeconds: 0, granularity: 'HOUR', enabled: true, labelKey: 'x.yesterday' },
    { value: '7d', kind: 'rolling', rollingSeconds: 7 * 24 * 3600, granularity: 'HOUR', enabled: true, labelKey: 'x.7d' },
    { value: '30d', kind: 'rolling', rollingSeconds: 30 * 24 * 3600, granularity: 'DAY', enabled: false, labelKey: 'x.30d' },
  ];

  it('rolling preset 以绝对时长计算边界', () => {
    const now = new Date('2026-09-01T12:34:56.789Z');
    const range = buildAggregationRange('24h', presets, now, 'UTC');
    expect(range.preset).toBe('24h');
    expect(range.startTime).toBe('2026-08-31T12:34:56.000Z');
    expect(range.endTime).toBe('2026-09-01T12:34:56.000Z');
    expect(range.granularity).toBe('HOUR');
    expect(range.timezone).toBe('UTC');
    expect(range.enabled).toBe(true);
  });

  it('今天 = 用户时区自然日 00:00 至 now', () => {
    const now = new Date('2026-09-01T20:00:00Z');
    const range = buildAggregationRange('today', presets, now, 'Asia/Shanghai');
    expect(range.startTime).toBe('2026-09-01T16:00:00.000Z');
    expect(range.endTime).toBe('2026-09-01T20:00:00.000Z');
  });

  it('昨天 = 用户时区前一完整自然日,endTime 与今天 startTime 重合', () => {
    const now = new Date('2026-09-01T20:00:00Z');
    const today = buildAggregationRange('today', presets, now, 'Asia/Shanghai');
    const yesterday = buildAggregationRange('yesterday', presets, now, 'Asia/Shanghai');
    expect(yesterday.endTime).toBe(today.startTime);
    expect(secondsBetween(yesterday.startTime, yesterday.endTime)).toBe(24 * 3600);
  });

  it('America/New_York 春令进:昨天自然日如实跨 23 小时', () => {
    const now = new Date('2026-03-09T12:00:00Z');
    const yesterday = buildAggregationRange('yesterday', presets, now, 'America/New_York');
    expect(secondsBetween(yesterday.startTime, yesterday.endTime)).toBe(23 * 3600);
    expect(yesterday.startTime).toBe('2026-03-08T05:00:00.000Z');
    expect(yesterday.endTime).toBe('2026-03-09T04:00:00.000Z');
  });

  it('America/New_York 秋令退:昨天自然日如实跨 25 小时', () => {
    const now = new Date('2026-11-02T12:00:00Z');
    const yesterday = buildAggregationRange('yesterday', presets, now, 'America/New_York');
    expect(secondsBetween(yesterday.startTime, yesterday.endTime)).toBe(25 * 3600);
    expect(yesterday.startTime).toBe('2026-11-01T04:00:00.000Z');
    expect(yesterday.endTime).toBe('2026-11-02T05:00:00.000Z');
  });

  it('未知 preset 抛错;非法 IANA 时区回退 UTC', () => {
    const now = new Date('2026-09-01T12:00:00Z');
    expect(() => buildAggregationRange('unknown' as never, presets, now, 'UTC')).toThrow();
    const range = buildAggregationRange('today', presets, now, 'Invalid/TZ');
    expect(range.timezone).toBe('UTC');
  });

  it('30D 在 preset 表中被声明为 disabled', () => {
    const now = new Date('2026-09-01T12:00:00Z');
    const range = buildAggregationRange('30d', presets, now, 'UTC');
    expect(range.enabled).toBe(false);
    expect(range.granularity).toBe('DAY');
    expect(secondsBetween(range.startTime, range.endTime)).toBe(30 * 24 * 3600);
  });

  it('返回对象不可变', () => {
    const range = buildAggregationRange('24h', presets, new Date('2026-09-01T12:00:00Z'), 'UTC');
    expect(Object.isFrozen(range)).toBe(true);
  });
});
