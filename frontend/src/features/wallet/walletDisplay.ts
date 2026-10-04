import {
  consumptionSummarySchema,
  transactionItemSchema,
  transactionsResponseSchema,
  type TransactionItem,
  type TransactionType,
} from '../../api/consumptionTransactions';
import { formatDecimalString } from './formatDecimal';
import { formatWalletTime } from './formatWalletTime';

/**
 * 钱包消费分析与统一流水的纯展示模型。
 *
 * 页面组件只消费这里的视图模型,不直接拼接 reasonCode、技术枚举、
 * `null` 或原始错误。未知枚举与矛盾组合一律映射为 `invalid`,
 * 由组件渲染通用安全失败面板。
 *
 * 文案全部以 i18n 键表达(资源在 `pages.wallet` 下补齐),数字与时间
 * 按活动 locale 格式化。货币只展示服务端给出的币种代码,绝不补算 USD。
 */

const REASON_KEYS: Record<string, string> = {
  CURRENCY_CONVERSION_NOT_VERIFIED: 'pages.wallet.reasonCurrencyConversionNotVerified',
  MISSING_STABLE_REFERENCE: 'pages.wallet.reasonMissingStableReference',
  BASELINE_NOT_VERIFIED: 'pages.wallet.reasonBaselineNotVerified',
  SOURCE_NOT_AVAILABLE: 'pages.wallet.reasonSourceNotAvailable',
};

const TYPE_KEYS: Record<TransactionType, string> = {
  TOPUP: 'pages.wallet.txTypeTopup',
  CONSUMPTION: 'pages.wallet.txTypeConsumption',
  REFUND: 'pages.wallet.txTypeRefund',
};

const STATUS_KEYS: Record<string, string> = {
  PENDING: 'pages.wallet.txStatusPending',
  SUCCEEDED: 'pages.wallet.txStatusSucceeded',
  FAILED: 'pages.wallet.txStatusFailed',
  REFUNDED: 'pages.wallet.txStatusRefunded',
  UNKNOWN: 'pages.wallet.txStatusUnknown',
};

function reasonKeyOf(reasonCode: string | null, available: boolean): string | null {
  if (available) {
    return reasonCode === null ? null : 'INVALID';
  }
  if (reasonCode === null) return 'INVALID';
  return REASON_KEYS[reasonCode] ?? 'INVALID';
}

/**
 * 总体原因:PARTIAL 的原因分散在逐来源 coverage 中,允许为 null;
 * 只有 AVAILABLE 携带原因或未知原因码才视为矛盾。
 */
function overallReasonKeyOf(
  reasonCode: string | null,
  availability: string,
): string | null | 'INVALID' {
  if (availability === 'AVAILABLE') {
    return reasonCode === null ? null : 'INVALID';
  }
  if (availability !== 'PARTIAL' && availability !== 'UNAVAILABLE') {
    return 'INVALID';
  }
  if (reasonCode === null) return null;
  return REASON_KEYS[reasonCode] ?? 'INVALID';
}

export type MetricDisplay =
  | { status: 'value'; text: string; unitKey: string | null; currency: string | null }
  | { status: 'unavailable'; reasonKey: string }
  | { status: 'invalid' };

function toCountMetric(
  value: string | null,
  unitKey: string,
  availability: string,
  reasonCode: string | null,
  locale: string,
): MetricDisplay {
  const reasonKey = reasonKeyOf(reasonCode, availability === 'AVAILABLE');
  if (reasonKey === 'INVALID') return { status: 'invalid' };
  if (availability === 'AVAILABLE') {
    if (value === null) return { status: 'invalid' };
    try {
      return { status: 'value', text: formatDecimalString(value, locale), unitKey, currency: null };
    } catch {
      return { status: 'invalid' };
    }
  }
  if (availability === 'UNAVAILABLE' || availability === 'PARTIAL') {
    if (value !== null || reasonKey === null) return { status: 'invalid' };
    return { status: 'unavailable', reasonKey };
  }
  return { status: 'invalid' };
}

function toMoneyMetric(
  value: string | null,
  currency: string | null,
  availability: string,
  reasonCode: string | null,
  locale: string,
): MetricDisplay {
  const reasonKey = reasonKeyOf(reasonCode, availability === 'AVAILABLE');
  if (reasonKey === 'INVALID') return { status: 'invalid' };
  if (availability === 'AVAILABLE') {
    if (value === null || currency === null) return { status: 'invalid' };
    try {
      return {
        status: 'value',
        text: formatDecimalString(value, locale),
        unitKey: null,
        currency,
      };
    } catch {
      return { status: 'invalid' };
    }
  }
  if (availability === 'UNAVAILABLE' || availability === 'PARTIAL') {
    if (value !== null || currency !== null || reasonKey === null) {
      return { status: 'invalid' };
    }
    return { status: 'unavailable', reasonKey };
  }
  return { status: 'invalid' };
}

export type CoverageDisplay =
  | { status: 'ok' }
  | { status: 'partial'; reasonKey: string }
  | { status: 'unavailable'; reasonKey: string }
  | { status: 'invalid' };

function toCoverage(availability: string, reasonCode: string | null): CoverageDisplay {
  const reasonKey = reasonKeyOf(reasonCode, availability === 'AVAILABLE');
  if (reasonKey === 'INVALID') return { status: 'invalid' };
  if (availability === 'AVAILABLE') return { status: 'ok' };
  if (availability === 'PARTIAL' && reasonKey) return { status: 'partial', reasonKey };
  if (availability === 'UNAVAILABLE' && reasonKey) return { status: 'unavailable', reasonKey };
  return { status: 'invalid' };
}

export type ConsumptionSummaryDisplay =
  | {
      status: 'ok';
      recordCount: MetricDisplay;
      quotaTotal: MetricDisplay;
      moneyTotal: MetricDisplay;
      coverage: CoverageDisplay;
    }
  | { status: 'invalid' };

export function toConsumptionSummaryDisplay(
  input: unknown,
  locale: string,
): ConsumptionSummaryDisplay {
  const parsed = consumptionSummarySchema.safeParse(input);
  if (!parsed.success) {
    return { status: 'invalid' };
  }
  const data = parsed.data;
  const recordCount = toCountMetric(
    data.recordCount.value,
    'pages.wallet.unitRecords',
    data.recordCount.availability,
    data.recordCount.reasonCode,
    locale,
  );
  const quotaTotal = toCountMetric(
    data.quotaTotal.value,
    'pages.wallet.unitQuota',
    data.quotaTotal.availability,
    data.quotaTotal.reasonCode,
    locale,
  );
  const moneyTotal = toMoneyMetric(
    data.moneyTotal.value,
    data.moneyTotal.currency,
    data.moneyTotal.availability,
    data.moneyTotal.reasonCode,
    locale,
  );
  const coverage = toCoverage(data.coverage.availability, data.coverage.reasonCode);
  if (
    recordCount.status === 'invalid' ||
    quotaTotal.status === 'invalid' ||
    moneyTotal.status === 'invalid' ||
    coverage.status === 'invalid'
  ) {
    return { status: 'invalid' };
  }
  return { status: 'ok', recordCount, quotaTotal, moneyTotal, coverage };
}

export type NullableTextDisplay = { status: 'value'; text: string } | { status: 'none' };

export type TransactionItemDisplay =
  | {
      status: 'ok';
      timeText: string;
      iso: string;
      typeKey: string;
      directionKey: string;
      isIncome: boolean;
      amountText: string;
      amountUnitKey: string | null;
      amountCurrency: string | null;
      statusKey: string;
      remark: NullableTextDisplay;
      reference: NullableTextDisplay;
    }
  | { status: 'invalid' };

function toNullableText(value: string | null): NullableTextDisplay {
  return value === null ? { status: 'none' } : { status: 'value', text: value };
}

export function toTransactionItemDisplay(
  input: unknown,
  locale: string,
  timezone: string,
): TransactionItemDisplay {
  const parsed = transactionItemSchema.safeParse(input);
  if (!parsed.success) {
    return { status: 'invalid' };
  }
  const item: TransactionItem = parsed.data;
  const typeKey = TYPE_KEYS[item.type];
  const statusKey = STATUS_KEYS[item.status];
  if (!typeKey || !statusKey) {
    return { status: 'invalid' };
  }
  let amountText: string;
  try {
    amountText = formatDecimalString(item.amount, locale);
  } catch {
    return { status: 'invalid' };
  }
  const isIncome = item.direction === 'CREDIT';
  return {
    status: 'ok',
    timeText: formatWalletTime(item.occurredAt, timezone, locale),
    iso: item.occurredAt,
    typeKey,
    directionKey: isIncome ? 'pages.wallet.directionCredit' : 'pages.wallet.directionDebit',
    isIncome,
    amountText,
    amountUnitKey: item.unit === 'QUOTA' ? 'pages.wallet.unitQuota' : null,
    amountCurrency: item.currency,
    statusKey,
    remark: toNullableText(item.remark),
    reference: toNullableText(item.referenceId),
  };
}

export interface TransactionCoverageDisplay {
  type: TransactionType;
  typeKey: string;
  availability: 'available' | 'partial' | 'unavailable';
  reasonKey: string | null;
}

export type TransactionsDisplay =
  | {
      status: 'ok';
      overall: 'available' | 'partial' | 'unavailable';
      overallReasonKey: string | null;
      coverage: TransactionCoverageDisplay[];
      items: TransactionItemDisplay[];
      total: number;
      isRealEmpty: boolean;
    }
  | { status: 'invalid' };

export function toTransactionsDisplay(input: unknown, locale: string): TransactionsDisplay {
  const parsed = transactionsResponseSchema.safeParse(input);
  if (!parsed.success) {
    return { status: 'invalid' };
  }
  const data = parsed.data;
  const overallReason = overallReasonKeyOf(data.reasonCode, data.availability);
  if (overallReason === 'INVALID') {
    return { status: 'invalid' };
  }
  let overall: 'available' | 'partial' | 'unavailable';
  if (data.availability === 'AVAILABLE') overall = 'available';
  else if (data.availability === 'PARTIAL') overall = 'partial';
  else if (data.availability === 'UNAVAILABLE') overall = 'unavailable';
  else return { status: 'invalid' };

  const coverage: TransactionCoverageDisplay[] = [];
  for (const entry of data.coverage) {
    const reasonKey = reasonKeyOf(entry.reasonCode, entry.availability === 'AVAILABLE');
    if (reasonKey === 'INVALID') return { status: 'invalid' };
    const typeKey = TYPE_KEYS[entry.type];
    if (!typeKey) return { status: 'invalid' };
    coverage.push({
      type: entry.type,
      typeKey,
      availability:
        entry.availability === 'AVAILABLE'
          ? 'available'
          : entry.availability === 'PARTIAL'
            ? 'partial'
            : 'unavailable',
      reasonKey,
    });
  }

  const timezone = data.range.timezone;
  const items = data.items.map((item) => toTransactionItemDisplay(item, locale, timezone));
  if (items.some((item) => item.status === 'invalid')) {
    return { status: 'invalid' };
  }

  return {
    status: 'ok',
    overall,
    overallReasonKey: overallReason,
    coverage,
    items,
    total: data.total,
    isRealEmpty: overall === 'available' && data.total === 0,
  };
}
