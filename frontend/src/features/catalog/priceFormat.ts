/**
 * 列表与详情共用的价格展示规则。
 *
 * 全程使用十进制字符串：不做 Number/parseFloat/toFixed 转换，不做前端换算，
 * 不静默舍入。科学计数法用字符串算法展开，保留接口给出的完整精度。
 * 真实零价展示为零；`pricing=null` 表示价格暂不可用而不是免费。
 */

import type { CatalogPricing, EnhancedPricingItem } from '../../api/models';

export interface PriceUnitLabels {
  perMillionTokens: string;
  perRequest: string;
  /** 未知单位时保留接口原文。 */
  unknownUnit: (unit: string) => string;
}

export interface PriceRow {
  key: string;
  label: string;
  value: string;
}

export interface BasePricingView {
  rows: PriceRow[];
  unitLabel: string;
}

export type EnhancedPricingView =
  | { state: 'unknown'; rows: [] }
  | { state: 'verified-empty'; rows: [] }
  | {
      state: 'items';
      rows: Array<PriceRow & { unitLabel: string }>;
    };

const DECIMAL = /^(\d+)(?:\.(\d+))?$/;
const SCIENTIFIC = /^(\d+)(?:\.(\d*))?[eE]([+-]?\d+)$/;

/** 用字符串算法展开科学计数法，不经过浮点数。 */
export function normalizeDecimal(value: string): string | null {
  const trimmed = value.trim();
  const scientific = SCIENTIFIC.exec(trimmed);
  if (scientific) {
    const [, intPart, fracPart = '', rawExponent] = scientific;
    const exponent = Number.parseInt(rawExponent, 10);
    const digits = intPart + fracPart;
    const pointIndex = intPart.length + exponent;
    if (pointIndex <= 0) {
      return `0.${'0'.repeat(-pointIndex)}${digits}`;
    }
    if (pointIndex >= digits.length) {
      return digits + '0'.repeat(pointIndex - digits.length);
    }
    return `${digits.slice(0, pointIndex)}.${digits.slice(pointIndex)}`;
  }
  const plain = DECIMAL.exec(trimmed);
  if (!plain) {
    return null;
  }
  const [, intPart, fracPart] = plain;
  return fracPart === undefined ? intPart : `${intPart}.${fracPart}`;
}

function unitLabel(unit: string, currency: string, labels: PriceUnitLabels): string {
  if (unit === 'PER_MILLION_TOKENS') {
    return `${currency} ${labels.perMillionTokens}`;
  }
  if (unit === 'PER_REQUEST') {
    return `${currency} ${labels.perRequest}`;
  }
  return `${currency} ${labels.unknownUnit(unit)}`;
}

export function formatBasePricing(
  pricing: CatalogPricing | null,
  labels: PriceUnitLabels & { input: string; output: string; request: string },
): BasePricingView | null {
  if (!pricing) {
    return null;
  }
  const view: BasePricingView = {
    rows: [],
    unitLabel: unitLabel(pricing.unit, pricing.currency, labels),
  };
  if (pricing.mode === 'TOKEN') {
    if (pricing.input !== null) {
      view.rows.push({ key: 'input', label: labels.input, value: pricing.input });
    }
    if (pricing.output !== null) {
      view.rows.push({ key: 'output', label: labels.output, value: pricing.output });
    }
  } else if (pricing.request !== null) {
    view.rows.push({ key: 'request', label: labels.request, value: pricing.request });
  }
  return view;
}

export function formatEnhancedPricing(
  items: EnhancedPricingItem[] | null,
  labels: PriceUnitLabels & Record<EnhancedPricingItem['type'], string>,
): EnhancedPricingView {
  if (items === null) {
    return { state: 'unknown', rows: [] };
  }
  if (items.length === 0) {
    return { state: 'verified-empty', rows: [] };
  }
  return {
    state: 'items',
    rows: items.map((item) => ({
      key: `${item.type}|${item.currency}|${item.unit}`,
      label: labels[item.type],
      value: normalizeDecimal(item.price) ?? item.price,
      unitLabel: unitLabel(item.unit, item.currency, labels),
    })),
  };
}
