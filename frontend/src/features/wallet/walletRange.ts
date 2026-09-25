import {
  buildAggregationRange,
  type AggregationRangePresetDef,
  type AggregationRangeValue,
} from '../aggregation/aggregationRange';

/**
 * 钱包页消费分析/统一流水的 preset 清单。
 *
 * 与 Dashboard 相比:
 * - 不支持 1H(P2-05 面向的是消费分析而非监控趋势);
 * - 30D 在 P2-05 7 天实时保护边界下不可请求,仅作可发现 placeholder。
 */

export type WalletRangePreset = '24h' | 'today' | 'yesterday' | '7d' | '30d';
export type WalletRangeGranularity = 'HOUR' | 'DAY';

export interface WalletRangeValue extends AggregationRangeValue {
  preset: WalletRangePreset;
  granularity: WalletRangeGranularity;
}

export interface WalletRangePresetDef extends AggregationRangePresetDef {
  value: WalletRangePreset;
  granularity: WalletRangeGranularity;
}

export const WALLET_RANGE_PRESETS: readonly WalletRangePresetDef[] = Object.freeze([
  { value: '24h', granularity: 'HOUR', enabled: true, rollingSeconds: 24 * 3600, labelKey: 'pages.wallet.range24h', kind: 'rolling' },
  { value: 'today', granularity: 'HOUR', enabled: true, rollingSeconds: 0, labelKey: 'pages.wallet.rangeToday', kind: 'calendar' },
  { value: 'yesterday', granularity: 'HOUR', enabled: true, rollingSeconds: 0, labelKey: 'pages.wallet.rangeYesterday', kind: 'calendar' },
  { value: '7d', granularity: 'HOUR', enabled: true, rollingSeconds: 7 * 24 * 3600, labelKey: 'pages.wallet.range7d', kind: 'rolling' },
  // 30D 可发现但不可请求:当前 P2-05 实时日志上限 7 天。
  { value: '30d', granularity: 'DAY', enabled: false, rollingSeconds: 30 * 24 * 3600, labelKey: 'pages.wallet.range30d', kind: 'rolling' },
]);

export function buildWalletRange(
  preset: WalletRangePreset,
  now: Date,
  rawTimezone: string,
): WalletRangeValue {
  return buildAggregationRange(preset, WALLET_RANGE_PRESETS, now, rawTimezone) as WalletRangeValue;
}
