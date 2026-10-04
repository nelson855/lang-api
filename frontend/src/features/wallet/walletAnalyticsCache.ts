import { WALLET_QUERY_KEY } from '../../api/wallet';
import { WALLET_TRANSACTIONS_PAGE_SIZE } from '../../api/consumptionTransactionsFetch';
import type { WalletRangeValue } from './walletRange';
import type { WalletTypeFilter, WalletUrlState } from './walletUrlState';

/**
 * 消费汇总与统一流水的查询键与能力门控。
 *
 * 键包含用户、完整范围、类型与分页,落在钱包命名空间下,随钱包范围统一清理。
 * 同一语义输入产生深度相等的稳定键,不携带时钟噪声。
 */

export function consumptionSummaryKey(
  userId: number,
  range: WalletRangeValue,
): readonly unknown[] {
  return [
    ...WALLET_QUERY_KEY,
    userId,
    'consumption-summary',
    range.startTime,
    range.endTime,
    range.granularity,
    range.timezone,
  ];
}

export function transactionsKey(
  userId: number,
  range: WalletRangeValue,
  type: WalletTypeFilter,
  page: number,
  pageSize: number = WALLET_TRANSACTIONS_PAGE_SIZE,
): readonly unknown[] {
  return [
    ...WALLET_QUERY_KEY,
    userId,
    'transactions',
    range.startTime,
    range.endTime,
    range.granularity,
    range.timezone,
    type,
    page,
    pageSize,
  ];
}

/**
 * 聚合请求门控:30D 等被禁 preset 或 URL 尚未规范化时保持 disabled,
 * 不发送已知必然失败或边界未定的请求。
 */
export function walletAnalyticsRequestsEnabled(
  state: Pick<WalletUrlState, 'requestsEnabled'>,
  normalized: boolean,
): boolean {
  return normalized && state.requestsEnabled;
}
