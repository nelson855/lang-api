import type { NormalizedRequestLogsParams } from './requestLogsCache';

/**
 * 请求日志 URL 适配层。
 *
 * URL 是筛选状态的唯一权威来源；本模块负责：
 * - 把 location.search 解析为规范化的筛选参数；
 * - 把筛选参数序列化为 search；
 * - 识别 Dashboard 下钻场景（带完整 startTime/endTime 的范围）；
 * - 拒绝或回退非法参数，保证后续查询不会发送非法请求。
 */

export const REQUEST_LOGS_DEFAULT_PAGE = 1;
export const REQUEST_LOGS_DEFAULT_PAGE_SIZE = 20;
export const REQUEST_LOGS_DEFAULT_RESULT = 'SUCCESS';

export const REQUEST_LOGS_RESULTS = ['SUCCESS', 'ERROR'] as const;
export type RequestLogsResult = (typeof REQUEST_LOGS_RESULTS)[number];

/** 标识“当前 URL 范围来自 Dashboard 下钻”，用于 UI 显示“Dashboard 所选范围”。 */
export const DASHBOARD_DRILLDOWN_PRESET = 'dashboard' as const;

const ISO_DATE_REGEX = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:?\d{2})$/;

function isIsoDateTime(value: string): boolean {
  if (!ISO_DATE_REGEX.test(value)) return false;
  const t = Date.parse(value);
  return Number.isFinite(t);
}

function parsePositiveInt(raw: string | null, fallback: number): number {
  if (raw === null || raw === '') return fallback;
  const n = Number(raw);
  if (!Number.isFinite(n) || !Number.isInteger(n) || n < 1) return fallback;
  return n;
}

function parseResult(raw: string | null): string {
  if (!raw) return REQUEST_LOGS_DEFAULT_RESULT;
  const upper = raw.trim().toUpperCase();
  return (REQUEST_LOGS_RESULTS as readonly string[]).includes(upper) ? upper : REQUEST_LOGS_DEFAULT_RESULT;
}

function parseOptionalString(raw: string | null): string {
  if (raw === null) return '';
  return raw.trim();
}

/**
 * 把 location.search 解析为规范化的请求日志参数。
 * - 未知参数忽略；
 * - 单边时间或非法时间一律清空两侧（避免半状态）；
 * - 非法 result/page 回退默认值。
 */
export function parseRequestLogsSearch(search: string): NormalizedRequestLogsParams {
  const params = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search);
  const startRaw = parseOptionalString(params.get('startTime'));
  const endRaw = parseOptionalString(params.get('endTime'));

  let startTime = '';
  let endTime = '';
  if (startRaw !== '' && endRaw !== '' && isIsoDateTime(startRaw) && isIsoDateTime(endRaw)) {
    startTime = startRaw;
    endTime = endRaw;
  }

  return {
    page: parsePositiveInt(params.get('page'), REQUEST_LOGS_DEFAULT_PAGE),
    pageSize: REQUEST_LOGS_DEFAULT_PAGE_SIZE,
    result: parseResult(params.get('result')),
    keyName: parseOptionalString(params.get('keyName')),
    model: parseOptionalString(params.get('model')),
    startTime,
    endTime,
  };
}

/**
 * 把规范化参数序列化为 search 字符串。
 * 默认值（page=1、result=SUCCESS、空字符串）省略，保持 URL 干净。
 */
export function serializeRequestLogsSearch(params: NormalizedRequestLogsParams): string {
  const search = new URLSearchParams();
  if (params.page !== REQUEST_LOGS_DEFAULT_PAGE) {
    search.set('page', String(params.page));
  }
  if (params.result !== REQUEST_LOGS_DEFAULT_RESULT) {
    search.set('result', params.result);
  }
  if (params.keyName !== '') {
    search.set('keyName', params.keyName);
  }
  if (params.model !== '') {
    search.set('model', params.model);
  }
  if (params.startTime !== '' && params.endTime !== '') {
    search.set('startTime', params.startTime);
    search.set('endTime', params.endTime);
  }
  const text = search.toString();
  return text === '' ? '' : `?${text}`;
}

/**
 * 当前 URL 是否携带 Dashboard 下钻范围（完整起止时间）。
 * 用于在请求日志页中切换范围输入的展示形态。
 */
export function isDashboardDrilldownRange(params: NormalizedRequestLogsParams): boolean {
  return params.startTime !== '' && params.endTime !== '';
}
