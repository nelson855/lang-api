/**
 * 中性的聚合范围内核:将 preset 解析为秒级、左闭右开、IANA 时区感知的
 * [startTime,endTime) 区间,供 Dashboard、钱包等聚合查询复用。
 *
 * 不做任何请求/缓存/日志,纯函数;所有输入显式传入,时钟快照由调用方控制。
 */

export type AggregationGranularity = 'FIVE_MINUTES' | 'HOUR' | 'DAY';

export interface AggregationRangePresetDef {
  /** preset 键(如 '24h'、'today'),由调用方决定枚举集。 */
  value: string;
  /** 'rolling' 绝对时长 | 'calendar' 用户时区自然日。 */
  kind: 'rolling' | 'calendar';
  /** rolling 秒数;calendar preset 约定为 0。 */
  rollingSeconds: number;
  granularity: AggregationGranularity;
  /** 当前是否可发起请求;false 仅展示。 */
  enabled: boolean;
  /** 界面 label 的 i18n 键,由调用方持有。 */
  labelKey: string;
}

export interface AggregationRangeValue {
  preset: string;
  startTime: string;
  endTime: string;
  timezone: string;
  granularity: AggregationGranularity;
  labelKey: string;
  enabled: boolean;
}

export function truncateToSeconds(ms: number): string {
  return new Date(Math.floor(ms / 1000) * 1000).toISOString();
}

export function normalizeIANATimezone(rawTimezone: string): string {
  const tz = rawTimezone?.trim() ?? '';
  if (!tz) return 'UTC';
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: tz });
    return tz;
  } catch {
    return 'UTC';
  }
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

/** 该时区在给定 UTC 时刻的偏移(毫秒):本地墙钟 − UTC。 */
function utcOffsetMs(timeZone: string, utcMs: number): number {
  const p = zonedParts(utcMs, timeZone);
  const localAsUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  return localAsUtc - utcMs;
}

/** 返回 now 所在时区自然日 00:00 对应的 UTC 毫秒;不动点迭代以适应 DST 切换。 */
export function startOfLocalDayUtc(nowMs: number, timeZone: string): number {
  const p = zonedParts(nowMs, timeZone);
  const ymdUtc = Date.UTC(p.year, p.month - 1, p.day, 0, 0, 0, 0);
  let t = ymdUtc - utcOffsetMs(timeZone, nowMs);
  for (let i = 0; i < 6; i++) {
    const next = ymdUtc - utcOffsetMs(timeZone, t);
    if (next === t) break;
    t = next;
  }
  return t;
}

function resolvePreset<P extends AggregationRangePresetDef>(
  preset: string,
  presets: readonly P[],
): P {
  const def = presets.find((p) => p.value === preset);
  if (!def) {
    throw new Error(`未知聚合范围 preset: ${preset}`);
  }
  return def;
}

/**
 * 生成一次聚合范围。
 * 调用方必须在进入页面/切换 preset 时只取一次 `now`,之后 URL/请求/重试复用同一边界。
 */
export function buildAggregationRange<P extends AggregationRangePresetDef>(
  preset: string,
  presets: readonly P[],
  now: Date,
  rawTimezone: string,
): AggregationRangeValue {
  const def = resolvePreset(preset, presets);
  const timezone = normalizeIANATimezone(rawTimezone);
  const nowMs = now.getTime();

  let startMs: number;
  let endMs = nowMs;
  if (def.kind === 'rolling') {
    startMs = nowMs - def.rollingSeconds * 1000;
  } else if (def.value === 'today') {
    startMs = startOfLocalDayUtc(nowMs, timezone);
  } else if (def.value === 'yesterday') {
    const todayStart = startOfLocalDayUtc(nowMs, timezone);
    startMs = startOfLocalDayUtc(todayStart - 1, timezone);
    endMs = todayStart;
  } else {
    throw new Error(`不支持的 calendar preset: ${def.value}`);
  }

  return Object.freeze({
    preset: def.value,
    startTime: truncateToSeconds(startMs),
    endTime: truncateToSeconds(endMs),
    timezone,
    granularity: def.granularity,
    labelKey: def.labelKey,
    enabled: def.enabled,
  });
}
