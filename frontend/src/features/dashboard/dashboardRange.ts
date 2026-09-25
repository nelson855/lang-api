import {
  buildAggregationRange,
  type AggregationRangePresetDef,
  type AggregationRangeValue,
} from '../aggregation/aggregationRange';
import type { DashboardStatsQueryParams } from '../../api/dashboard';

/**
 * Dashboard 快捷范围的兼容包装。
 *
 * 实际算法由 features/aggregation/aggregationRange 提供;此模块只保留
 * Dashboard 专属 preset 清单、类型与 toStatsParams,以便现有调用不受影响。
 */

export type DashboardPreset = '1h' | '24h' | 'today' | 'yesterday' | '7d' | '30d';
export type DashboardGranularity = 'FIVE_MINUTES' | 'HOUR' | 'DAY';

export interface DashboardRangeValue extends AggregationRangeValue {
  preset: DashboardPreset;
  granularity: DashboardGranularity;
}

export interface DashboardPresetDef extends AggregationRangePresetDef {
  value: DashboardPreset;
  granularity: DashboardGranularity;
}

export const DASHBOARD_PRESETS: readonly DashboardPresetDef[] = Object.freeze([
  { value: '1h', granularity: 'FIVE_MINUTES', enabled: true, rollingSeconds: 3600, labelKey: 'pages.dashboard.range1h', kind: 'rolling' },
  { value: '24h', granularity: 'HOUR', enabled: true, rollingSeconds: 24 * 3600, labelKey: 'pages.dashboard.range24h', kind: 'rolling' },
  { value: 'today', granularity: 'HOUR', enabled: true, rollingSeconds: 0, labelKey: 'pages.dashboard.rangeToday', kind: 'calendar' },
  { value: 'yesterday', granularity: 'HOUR', enabled: true, rollingSeconds: 0, labelKey: 'pages.dashboard.rangeYesterday', kind: 'calendar' },
  { value: '7d', granularity: 'HOUR', enabled: true, rollingSeconds: 7 * 24 * 3600, labelKey: 'pages.dashboard.range7d', kind: 'rolling' },
  { value: '30d', granularity: 'DAY', enabled: false, rollingSeconds: 30 * 24 * 3600, labelKey: 'pages.dashboard.range30d', kind: 'rolling' },
]);

export function buildDashboardRange(
  preset: DashboardPreset,
  now: Date,
  rawTimezone: string,
): DashboardRangeValue {
  return buildAggregationRange(preset, DASHBOARD_PRESETS, now, rawTimezone) as DashboardRangeValue;
}

export function toStatsParams(range: DashboardRangeValue): DashboardStatsQueryParams {
  return {
    startTime: range.startTime,
    endTime: range.endTime,
    granularity: range.granularity,
    timezone: range.timezone,
  };
}
