import type { DashboardReasonCode } from '../../api/dashboard';

/**
 * Dashboard 展示模型纯函数层。
 * 将 schema 化的指标/集合转换为 UI 展示状态，不直接操作 JSX。
 */

export interface DisplayFormatters {
  number: (n: number) => string;
  percent: (n: number) => string;
  ms: (n: number) => string;
  money: (amount: string, currency: string) => string;
}

export function createDisplayFormatters(locale: string): DisplayFormatters {
  const numberFmt = new Intl.NumberFormat(locale);
  const percentFmt = new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 2 });
  return {
    number: (n) => numberFmt.format(n),
    percent: (n) => percentFmt.format(n),
    ms: (n) => `${numberFmt.format(n)} ms`,
    money: (amount, currency) =>
      new Intl.NumberFormat(locale, {
        style: 'currency',
        currency,
        maximumFractionDigits: 6,
      }).format(Number(amount)),
  };
}

export interface MetricDisplayInput {
  kind: 'count' | 'ratio' | 'average' | 'money';
  availability: 'AVAILABLE' | 'UNAVAILABLE';
  reasonCode: DashboardReasonCode | null;
  value: number | string | null;
  currency?: string | null;
}

export type MetricDisplayStatus =
  | { status: 'value'; text: string }
  | { status: 'no-data' }
  | { status: 'unavailable'; reason: DashboardReasonCode };

export function toMetricDisplay(
  input: MetricDisplayInput,
  fmt: DisplayFormatters,
): MetricDisplayStatus {
  if (input.availability === 'AVAILABLE') {
    let text: string;
    switch (input.kind) {
      case 'money':
        text = fmt.money(input.value as string, input.currency!);
        break;
      case 'ratio':
        text = fmt.percent(input.value as number);
        break;
      case 'average':
        text = fmt.ms(input.value as number);
        break;
      default:
        text = fmt.number(input.value as number);
        break;
    }
    return { status: 'value', text };
  }

  if (input.reasonCode === 'NO_DATA') {
    return { status: 'no-data' };
  }

  return { status: 'unavailable', reason: input.reasonCode! };
}

export type CollectionDisplayStatus =
  | { status: 'value' }
  | { status: 'partial'; reason: DashboardReasonCode }
  | { status: 'no-data' }
  | { status: 'unavailable'; reason: DashboardReasonCode };

export function toCollectionDisplay(
  availability: 'AVAILABLE' | 'PARTIAL' | 'UNAVAILABLE',
  reasonCode: DashboardReasonCode | null,
): CollectionDisplayStatus {
  if (availability === 'AVAILABLE') {
    return { status: 'value' };
  }
  if (availability === 'PARTIAL') {
    return { status: 'partial', reason: reasonCode! };
  }
  if (reasonCode === 'NO_DATA') {
    return { status: 'no-data' };
  }
  return { status: 'unavailable', reason: reasonCode! };
}