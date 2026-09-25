import {
  aggregationGranularitySchema,
  consumptionSummarySchema,
  transactionsResponseSchema,
  transactionTypeSchema,
  type AggregationRange,
  type ConsumptionSummary,
  type TransactionType,
  type TransactionsResponse,
} from './consumptionTransactions';
import { portalRequest } from './portalClient';
import { buildPortalApiUrl } from './portalPath';

/**
 * P2-05 消费汇总与统一流水的 URL 构造与读取函数。
 * 只接受已规范化的范围、可选单值 type、页码;pageSize 固定 20,
 * 不接受 userId、baseline、客户端多类型或任何上游原始字段。
 */

export const CONSUMPTION_SUMMARY_PATH = '/portal/api/account/consumption-summary';
export const TRANSACTIONS_PATH = '/portal/api/account/transactions';
export const WALLET_TRANSACTIONS_PAGE_SIZE = 20;

/** P2-05 服务端深分页保护:page * pageSize 不得超过 aggregation.max-records=200。 */
const MAX_CANDIDATE_RECORDS = 200;

function requireNonEmpty(value: string, field: string): string {
  const trimmed = value?.trim() ?? '';
  if (!trimmed) {
    throw new Error(`${field} 不得为空`);
  }
  return trimmed;
}

function normalizeRange(range: AggregationRange): {
  startTime: string;
  endTime: string;
  granularity: string;
  timezone: string;
} {
  if (!range || typeof range !== 'object') {
    throw new Error('范围必须是对象');
  }
  const startTime = requireNonEmpty(range.start, 'startTime');
  const endTime = requireNonEmpty(range.end, 'endTime');
  const timezone = requireNonEmpty(range.timezone, 'timezone');
  const granularityRaw = requireNonEmpty(range.granularity, 'granularity');
  aggregationGranularitySchema.parse(granularityRaw);
  return { startTime, endTime, granularity: granularityRaw, timezone };
}

export function buildConsumptionSummaryUrl(range: AggregationRange): string {
  const r = normalizeRange(range);
  return buildPortalApiUrl(CONSUMPTION_SUMMARY_PATH, r);
}

export function buildTransactionsUrl(
  range: AggregationRange,
  page: number,
  type?: TransactionType,
  extras?: never,
): string {
  if (extras !== undefined) {
    throw new Error('统一流水 URL 不接受额外参数');
  }
  const r = normalizeRange(range);
  if (!Number.isInteger(page) || page < 1) {
    throw new Error('页码必须为不小于 1 的整数');
  }
  if (page * WALLET_TRANSACTIONS_PAGE_SIZE > MAX_CANDIDATE_RECORDS) {
    throw new Error(`请求页超出聚合保护上限(${MAX_CANDIDATE_RECORDS} 条)`);
  }
  let typeParam: string | undefined;
  if (type !== undefined) {
    typeParam = transactionTypeSchema.parse(type);
  }
  return buildPortalApiUrl(TRANSACTIONS_PATH, {
    ...r,
    type: typeParam,
    page,
    pageSize: WALLET_TRANSACTIONS_PAGE_SIZE,
  });
}

export function fetchConsumptionSummary(
  range: AggregationRange,
  signal?: AbortSignal,
): Promise<{ data: ConsumptionSummary; requestId: string }> {
  return portalRequest(
    buildConsumptionSummaryUrl(range),
    consumptionSummarySchema,
    signal ? { signal } : undefined,
  );
}

export function fetchTransactions(
  range: AggregationRange,
  page: number,
  type?: TransactionType,
  signal?: AbortSignal,
): Promise<{ data: TransactionsResponse; requestId: string }> {
  return portalRequest(
    buildTransactionsUrl(range, page, type),
    transactionsResponseSchema,
    signal ? { signal } : undefined,
  );
}
