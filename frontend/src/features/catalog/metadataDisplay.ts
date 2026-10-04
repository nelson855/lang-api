/**
 * 详情元数据的展示规则。
 *
 * `null`、`false`、已验证空集合与合法数值零各自独立分支：未知显示暂无数据，
 * false 表示不支持，空集合表示无，零保留为零。不从品牌、模型名或端点推断
 * 厂商、模态、能力、标签或排序，也不执行接口提供的 HTML。
 */

export interface CapabilityLabels {
  supported: string;
  unsupported: string;
  unknown: string;
}

export type DisplayValue =
  | { kind: 'unknown'; text: string }
  | { kind: 'empty'; text: string }
  | { kind: 'value'; text: string }
  | { kind: 'list'; text: string };

export function describeCapability(
  value: boolean | null,
  labels: CapabilityLabels,
): string {
  if (value === null) {
    return labels.unknown;
  }
  return value ? labels.supported : labels.unsupported;
}

export function describeModalities<T extends string>(
  values: T[] | null,
  labels: Record<T, string>,
  unknownText = '暂无数据',
  emptyText = '无',
): DisplayValue {
  if (values === null) {
    return { kind: 'unknown', text: unknownText };
  }
  if (values.length === 0) {
    return { kind: 'empty', text: emptyText };
  }
  return { kind: 'list', text: values.map((value) => labels[value]).join('、') };
}

export function describeOptionalText(value: string | null, unknownText: string): DisplayValue {
  if (value === null || value === '') {
    return { kind: 'unknown', text: unknownText };
  }
  return { kind: 'value', text: value };
}

/** 合法数值零保留为零；只有 null 才是未知。 */
export function describeOptionalNumber(value: number | null, unknownText: string): DisplayValue {
  if (value === null) {
    return { kind: 'unknown', text: unknownText };
  }
  return { kind: 'value', text: String(value) };
}

export function describeTags(
  tags: string[] | null,
  unknownText: string,
  emptyText: string,
): DisplayValue {
  if (tags === null) {
    return { kind: 'unknown', text: unknownText };
  }
  if (tags.length === 0) {
    return { kind: 'empty', text: emptyText };
  }
  return { kind: 'list', text: tags.join('、') };
}

/**
 * 发布日期是日历日期，不作为 UTC 时间点转换，
 * 避免在不同时区展示时偏移一天。
 */
export function describeReleaseDate(
  value: string | null,
  _locale: string,
  unknownText = '暂无数据',
): DisplayValue {
  if (value === null) {
    return { kind: 'unknown', text: unknownText };
  }
  return { kind: 'value', text: value };
}

export interface TruncatedText {
  display: string;
  full: string;
  truncated: boolean;
}

/** 页面截断显示，但完整值始终保留以便选择与复制。 */
export function truncateForDisplay(value: string, limit: number): TruncatedText {
  if (value.length <= limit) {
    return { display: value, full: value, truncated: false };
  }
  return { display: `${value.slice(0, limit)}…`, full: value, truncated: true };
}
