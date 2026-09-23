import type { DashboardStatsQueryParams } from '../../api/dashboard';

/**
 * Dashboard 快捷范围生成器。
 * 滚动范围（1H/24H/7D/30D）按绝对时长计算；自然日（今天/昨天）使用
 * Intl.DateTimeFormat 解析用户时区的真实瞬时边界，从而覆盖 DST 23/25 小时。
 * 边界统一截断到秒（毫秒清零），返回不可变对象。
 */

export type DashboardPreset = '1h' | '24h' | 'today' | 'yesterday' | '7d' | '30d';
export type DashboardGranularity = 'FIVE_MINUTES' | 'HOUR' | 'DAY';

export interface DashboardRangeValue {
  preset: DashboardPreset;
  startTime: string;
  endTime: string;
  granularity: DashboardGranularity;
  timezone: string;
  /** 是否为 preset 提供的界面标签键。 */
  labelKey: string;
  /** 30D 在当前 P2-03 保护边界下可见但不可请求。 */
  enabled: boolean;
}

export interface DashboardPresetDef {
  value: DashboardPreset;
  granularity: DashboardGranularity;
  enabled: boolean;
  /** 滚动范围时长（秒）；自然日 preset 为 0。 */
  rollingSeconds: number;
  labelKey: string;
  /** 'rolling' 绝对时长 | 'calendar' 自然日。 */
  kind: 'rolling' | 'calendar';
}

export const DASHBOARD_PRESETS: readonly DashboardPresetDef[] = Object.freeze([
  { value: '1h', granularity: 'FIVE_MINUTES', enabled: true, rollingSeconds: 3600, labelKey: 'pages.dashboard.range1h', kind: 'rolling' },
  { value: '24h', granularity: 'HOUR', enabled: true, rollingSeconds: 24 * 3600, labelKey: 'pages.dashboard.range24h', kind: 'rolling' },
  { value: 'today', granularity: 'HOUR', enabled: true, rollingSeconds: 0, labelKey: 'pages.dashboard.rangeToday', kind: 'calendar' },
  { value: 'yesterday', granularity: 'HOUR', enabled: true, rollingSeconds: 0, labelKey: 'pages.dashboard.rangeYesterday', kind: 'calendar' },
  { value: '7d', granularity: 'HOUR', enabled: true, rollingSeconds: 7 * 24 * 3600, labelKey: 'pages.dashboard.range7d', kind: 'rolling' },
  { value: '30d', granularity: 'DAY', enabled: false, rollingSeconds: 30 * 24 * 3600, labelKey: 'pages.dashboard.range30d', kind: 'rolling' },
]);

function truncateSeconds(ms: number): string {
  return new Date(Math.floor(ms / 1000) * 1000).toISOString();
}

/** 返回该时区在给定 UTC 时刻的墙钟时间分量 {year,month,day,hour,minute,second}。 */
function zonedParts(utcMs: number, timeZone: string) {
  const dtf = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
  const map: Record<string, number> = {};
  for (const part of dtf.formatToParts(utcMs)) {
    if (part.type !== 'literal') {
      map[part.type] = Number(part.value);
    }
  }
  return { year: map.year, month: map.month, day: map.day, hour: map.hour, minute: map.minute, second: map.second };
}

/** 该时区在给定 UTC 时刻的偏移（毫秒）：本地墙钟 − UTC。正即本地快于 UTC。 */
function utcOffsetMs(timeZone: string, utcMs: number): number {
  const p = zonedParts(utcMs, timeZone);
  const localAsUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  return localAsUtc - utcMs;
}

/** 返回 now 所在时区自然日 00:00 对应的 UTC 毫秒。 */
function startOfLocalDayUtc(nowMs: number, timeZone: string): number {
  const p = zonedParts(nowMs, timeZone);
  const ymdUtc = Date.UTC(p.year, p.month - 1, p.day, 0, 0, 0, 0); // 把本地 00:00 当作 UTC 的基底
  // 不动点：真实 UTC = ymdUtc - offset(真实 UTC)。offset 在 DST 切换日当天会变，
  // 因此用迭代收敛到该自然日 00:00 时刻的真实偏移，避免"正午估算"在切换日出错。
  let t = ymdUtc - utcOffsetMs(timeZone, nowMs);
  for (let i = 0; i < 6; i++) {
    const next = ymdUtc - utcOffsetMs(timeZone, t);
    if (next === t) {
      break;
    }
    t = next;
  }
  return t;
}

function resolveDef(preset: DashboardPreset): DashboardPresetDef {
  const def = DASHBOARD_PRESETS.find((p) => p.value === preset);
  if (!def) {
    throw new Error(`未知快捷范围: ${preset}`);
  }
  return def;
}

export function buildDashboardRange(
  preset: DashboardPreset,
  now: Date,
  rawTimezone: string,
): DashboardRangeValue {
  let timezone = rawTimezone?.trim() || '';
  const nowMs = now.getTime();

  // 无效 IANA 名称无法被 Intl 解析，回退 UTC。
  try {
    if (timezone) {
      new Intl.DateTimeFormat('en-US', { timeZone: timezone });
    }
  } catch {
    timezone = 'UTC';
  }

  const def = resolveDef(preset);

  let startMs: number;
  let endMs = nowMs;
  if (def.kind === 'rolling') {
    startMs = nowMs - def.rollingSeconds * 1000;
  } else if (preset === 'today') {
    startMs = startOfLocalDayUtc(nowMs, timezone);
  } else {
    // yesterday：先取今天自然日起点，再回退 1 毫秒到前一天，再求前一天自然日起点。
    const todayStart = startOfLocalDayUtc(nowMs, timezone);
    startMs = startOfLocalDayUtc(todayStart - 1, timezone);
    endMs = todayStart;
  }

  return Object.freeze({
    preset,
    startTime: truncateSeconds(startMs),
    endTime: truncateSeconds(endMs),
    granularity: def.granularity,
    timezone,
    labelKey: def.labelKey,
    enabled: def.enabled,
  });
}

export function toStatsParams(range: DashboardRangeValue): DashboardStatsQueryParams {
  return {
    startTime: range.startTime,
    endTime: range.endTime,
    granularity: range.granularity,
    timezone: range.timezone,
  };
}