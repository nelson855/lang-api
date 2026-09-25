import { transactionTypeSchema, type TransactionType } from '../../api/consumptionTransactions';
import {
  WALLET_RANGE_PRESETS,
  buildWalletRange,
  type WalletRangePreset,
  type WalletRangeValue,
} from './walletRange';

/**
 * 钱包页 URL 作为唯一已提交状态的纯函数适配层。
 *
 * 白名单参数:range / startTime / endTime / timezone / granularity / type / page。
 * 未知参数、单边时间、非法枚举/页码、preset 与范围语义矛盾、startTime≥endTime
 * 全部回退到 24H/ALL/1,并通过 needsReplace 通知调用方 replace 写回。
 * 30D 可解析但 requestsEnabled=false。
 */

export type WalletTypeFilter = 'ALL' | TransactionType;

export interface WalletUrlState {
  preset: WalletRangePreset;
  range: WalletRangeValue;
  type: WalletTypeFilter;
  page: number;
  /** 30D 等被禁范围下为 false;调用方应据此禁用 P2-05 请求。 */
  requestsEnabled: boolean;
}

export const WALLET_URL_KEYS = [
  'range',
  'startTime',
  'endTime',
  'timezone',
  'granularity',
  'type',
  'page',
] as const;

export const DEFAULT_WALLET_URL_STATE: Readonly<{
  preset: WalletRangePreset;
  type: WalletTypeFilter;
  page: number;
}> = Object.freeze({ preset: '24h', type: 'ALL', page: 1 });

export const WALLET_TRANSACTIONS_PAGE_SIZE_DEFAULT = 20;

const WALLET_PRESET_SET = new Set<WalletRangePreset>(
  WALLET_RANGE_PRESETS.map((p) => p.value),
);

const GRANULARITY_SET = new Set(['FIVE_MINUTES', 'HOUR', 'DAY']);

const ISO_SECONDS = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{3})?(Z|[+-]\d{2}:\d{2})$/;

export interface WalletUrlParseOk {
  ok: true;
  state: WalletUrlState;
}

export interface WalletUrlParseFail {
  ok: false;
}

export type WalletUrlParseResult = WalletUrlParseOk | WalletUrlParseFail;

export interface WalletUrlNormalizeResult {
  state: WalletUrlState;
  /** true 表示输入需要被规范化并 replace 写回(包括首次进入与所有非法输入)。 */
  needsReplace: boolean;
}

function isValidIANA(tz: string): boolean {
  if (!tz) return false;
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: tz });
    return true;
  } catch {
    return false;
  }
}

function isValidIsoSeconds(value: string): boolean {
  if (!ISO_SECONDS.test(value)) return false;
  const ms = Date.parse(value);
  return Number.isFinite(ms);
}

function parsePage(raw: string | null): number | null {
  if (raw === null) return 1;
  if (!/^-?\d+$/.test(raw)) return null;
  const n = Number.parseInt(raw, 10);
  if (!Number.isInteger(n) || n < 1) return null;
  return n;
}

function parseType(raw: string | null): WalletTypeFilter | null {
  if (raw === null) return 'ALL';
  if (raw === 'ALL') return 'ALL';
  const parsed = transactionTypeSchema.safeParse(raw);
  return parsed.success ? parsed.data : null;
}

function isValidPreset(preset: string): preset is WalletRangePreset {
  return WALLET_PRESET_SET.has(preset as WalletRangePreset);
}

/** 校验 range preset 与实际范围跨度是否一致;不一致视为矛盾。 */
function presetMatchesRange(
  preset: WalletRangePreset,
  startTime: string,
  endTime: string,
): boolean {
  const seconds = (Date.parse(endTime) - Date.parse(startTime)) / 1000;
  if (!Number.isFinite(seconds) || seconds <= 0) return false;
  switch (preset) {
    case '24h':
      return seconds === 24 * 3600;
    case '7d':
      return seconds === 7 * 24 * 3600;
    case '30d':
      return seconds === 30 * 24 * 3600;
    case 'today':
    case 'yesterday':
      // 自然日 23/24/25 小时都合法(DST);宽松接受 20~28 小时。
      return seconds >= 20 * 3600 && seconds <= 28 * 3600;
    default:
      return false;
  }
}

/** 为给定 preset + 时钟快照生成一个全新的规范化 state(用于首次进入与回退)。 */
function defaultState(now: Date, timezone: string): WalletUrlState {
  const range = buildWalletRange(DEFAULT_WALLET_URL_STATE.preset, now, timezone);
  return {
    preset: DEFAULT_WALLET_URL_STATE.preset,
    range,
    type: DEFAULT_WALLET_URL_STATE.type,
    page: DEFAULT_WALLET_URL_STATE.page,
    requestsEnabled: true,
  };
}

/** 从 URLSearchParams 构造严格 state;任何一项非法都返回 { ok: false }。 */
export function parseWalletUrl(search: string): WalletUrlParseResult {
  const params = new URLSearchParams(search);

  const presetRaw = params.get('range');
  const startTime = params.get('startTime');
  const endTime = params.get('endTime');
  const timezone = params.get('timezone');
  const granularity = params.get('granularity');
  const typeRaw = params.get('type');
  const pageRaw = params.get('page');

  // 所有 7 个白名单字段都必须显式存在;否则视为未规范化的首次进入或残缺输入。
  if (
    presetRaw === null ||
    startTime === null ||
    endTime === null ||
    timezone === null ||
    granularity === null ||
    typeRaw === null ||
    pageRaw === null
  ) {
    return { ok: false };
  }

  if (!isValidPreset(presetRaw)) return { ok: false };
  if (!isValidIsoSeconds(startTime) || !isValidIsoSeconds(endTime)) return { ok: false };
  if (!isValidIANA(timezone)) return { ok: false };
  if (!GRANULARITY_SET.has(granularity)) return { ok: false };

  const type = parseType(typeRaw);
  if (type === null) return { ok: false };
  const page = parsePage(pageRaw);
  if (page === null) return { ok: false };

  if (Date.parse(startTime) >= Date.parse(endTime)) return { ok: false };
  if (!presetMatchesRange(presetRaw, startTime, endTime)) return { ok: false };

  const presetDef = WALLET_RANGE_PRESETS.find((p) => p.value === presetRaw)!;
  const range: WalletRangeValue = Object.freeze({
    preset: presetRaw,
    startTime,
    endTime,
    timezone,
    granularity: granularity as WalletRangeValue['granularity'],
    labelKey: presetDef.labelKey,
    enabled: presetDef.enabled,
  });

  return {
    ok: true,
    state: {
      preset: presetRaw,
      range,
      type,
      page,
      requestsEnabled: presetDef.enabled,
    },
  };
}

/**
 * 规范化当前 URL。
 * - 完整合法 → 原样返回(needsReplace=false);
 * - 其他情况(空 search、参数缺失、非法、矛盾、未知字段等)→ 用单次时钟快照
 *   生成默认 24H/ALL/1,并标记 needsReplace=true。
 */
export function normalizeWalletUrl(
  search: string,
  now: Date,
  timezone: string,
): WalletUrlNormalizeResult {
  const parsed = parseWalletUrl(search);
  if (parsed.ok) {
    return { state: parsed.state, needsReplace: false };
  }
  return { state: defaultState(now, timezone), needsReplace: true };
}

/** 把 state 序列化为以 ? 开头的查询串。 */
export function buildWalletUrlSearch(state: WalletUrlState): string {
  const params = new URLSearchParams();
  params.set('range', state.preset);
  params.set('startTime', state.range.startTime);
  params.set('endTime', state.range.endTime);
  params.set('timezone', state.range.timezone);
  params.set('granularity', state.range.granularity);
  params.set('type', state.type);
  params.set('page', String(state.page));
  return `?${params.toString()}`;
}

/** 用户翻页或响应 total 收敛越界页:只改 page,保留其他已提交状态。 */
export function collapseWalletPage(
  state: WalletUrlState,
  nextPageOrTotal: number,
  pageSize: number = WALLET_TRANSACTIONS_PAGE_SIZE_DEFAULT,
): WalletUrlState {
  // 单参数:视为直接设置 page。
  if (pageSize === WALLET_TRANSACTIONS_PAGE_SIZE_DEFAULT && arguments.length === 2) {
    const nextPage = Math.max(1, Math.floor(nextPageOrTotal));
    return { ...state, page: nextPage };
  }
  // 双参数:基于 total 收敛越界。
  const total = Math.max(0, Math.floor(nextPageOrTotal));
  const lastPage = Math.max(1, Math.ceil(total / pageSize));
  const nextPage = Math.min(Math.max(1, state.page), lastPage);
  return { ...state, page: nextPage };
}

/** 切换范围:用单次时钟快照生成新边界,保留类型,page 重置为 1。 */
export function switchWalletRange(
  state: WalletUrlState,
  preset: WalletRangePreset,
  now: Date,
  timezone: string,
): WalletUrlState {
  if (!isValidPreset(preset)) {
    throw new Error(`未知钱包范围: ${preset}`);
  }
  const range = buildWalletRange(preset, now, timezone);
  const presetDef = WALLET_RANGE_PRESETS.find((p) => p.value === preset)!;
  return {
    preset,
    range,
    type: state.type,
    page: 1,
    requestsEnabled: presetDef.enabled,
  };
}

/** 切换类型:只保留范围,page 重置为 1。 */
export function switchWalletType(state: WalletUrlState, type: WalletTypeFilter): WalletUrlState {
  const nextType = parseType(type);
  if (nextType === null) {
    throw new Error(`未知流水类型: ${type}`);
  }
  return { ...state, type: nextType, page: 1 };
}
