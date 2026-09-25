import { useQuery } from '@tanstack/react-query';
import type { AggregationRange } from '../../api/consumptionTransactions';
import {
  fetchConsumptionSummary,
  fetchTransactions,
  WALLET_TRANSACTIONS_PAGE_SIZE,
} from '../../api/consumptionTransactionsFetch';
import {
  consumptionSummaryKey,
  transactionsKey,
  walletAnalyticsRequestsEnabled,
} from './walletAnalyticsCache';
import type { WalletRangeValue } from './walletRange';
import type { WalletTypeFilter, WalletUrlState } from './walletUrlState';

/**
 * 消费汇总与统一流水的 TanStack Query 钩子。
 *
 * 两个查询并行独立,关闭自动重试,透传 AbortSignal 由 Query 负责取消。
 * 30D 被禁 preset 或 URL 尚未规范化时保持 disabled,不发送请求。
 */

export interface WalletAnalyticsOptions {
  /** URL 已规范化(默认 true);false 时禁用请求。 */
  enabled?: boolean;
}

function toAggregationRange(range: WalletRangeValue): AggregationRange {
  return {
    start: range.startTime,
    end: range.endTime,
    timezone: range.timezone,
    granularity: range.granularity,
  };
}

function resolveEnabled(
  range: WalletRangeValue,
  state: Pick<WalletUrlState, 'requestsEnabled'>,
  opts?: WalletAnalyticsOptions,
): boolean {
  const normalized = opts?.enabled ?? true;
  return range.enabled && walletAnalyticsRequestsEnabled(state, normalized);
}

function stateOf(range: WalletRangeValue): Pick<WalletUrlState, 'requestsEnabled'> {
  return { requestsEnabled: range.enabled };
}

export function useConsumptionSummaryQuery(
  userId: number,
  range: WalletRangeValue,
  opts?: WalletAnalyticsOptions,
) {
  const apiRange = toAggregationRange(range);
  return useQuery({
    queryKey: consumptionSummaryKey(userId, range),
    queryFn: ({ signal }) => fetchConsumptionSummary(apiRange, signal),
    retry: false,
    enabled: resolveEnabled(range, stateOf(range), opts),
  });
}

export function useTransactionsQuery(
  userId: number,
  range: WalletRangeValue,
  type: WalletTypeFilter,
  page: number,
  opts?: WalletAnalyticsOptions,
) {
  const apiRange = toAggregationRange(range);
  const singleType = type === 'ALL' ? undefined : type;
  return useQuery({
    queryKey: transactionsKey(userId, range, type, page, WALLET_TRANSACTIONS_PAGE_SIZE),
    queryFn: ({ signal }) => fetchTransactions(apiRange, page, singleType, signal),
    retry: false,
    enabled: resolveEnabled(range, stateOf(range), opts),
  });
}
